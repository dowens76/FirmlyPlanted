package com.firmlyplanted.app.domain

import java.time.LocalDate

/** Minimal view of a verse's progress state that the scheduler needs — decoupled from Room. */
data class VerseProgress(
    val id: String,
    val orderIndex: Int,
    val phase: VersePhase,
    val addedDate: LocalDate?,
    val lastReviewedDate: LocalDate?,
    val nextReviewDate: LocalDate?,
    val consecutiveSuccesses: Int,
)

data class TodayPlan(
    val newVerseIds: List<String>,
    val dueReviewIds: List<String>,
)

/**
 * Graduated cumulative review, modeled after Andy Davis's method and Scripta Memoria:
 * a few new verses are added each day in passage order; everything already learned is
 * reviewed on a lengthening schedule (daily -> every 3 days -> weekly -> monthly) so the
 * whole passage stays fresh without the daily review load growing without bound.
 */
object ReviewScheduler {

    /** Consecutive successful reviews needed before a verse graduates to the next phase. */
    private const val LEARNING_TO_SHORT = 6   // ~a week of daily review
    private const val SHORT_TO_LONG = 4       // ~2 weeks on a 3-day cycle
    private const val LONG_TO_MASTERED = 6    // ~6 weeks on a weekly cycle

    private fun intervalDaysFor(phase: VersePhase): Int = when (phase) {
        VersePhase.NEW -> 1
        VersePhase.LEARNING -> 1
        VersePhase.REVIEW_SHORT -> 3
        VersePhase.REVIEW_LONG -> 7
        VersePhase.MASTERED -> 30
    }

    /**
     * Builds today's plan: which not-yet-started verses to introduce, and which already-started
     * verses are due for review, each capped by the project's daily settings. Due reviews are
     * prioritized most-overdue-first; anything over the cap simply stays due and surfaces first
     * tomorrow, rather than being dropped.
     */
    fun planToday(
        allVerses: List<VerseProgress>,
        newVersesPerDay: Int,
        reviewVersesPerDay: Int,
        today: LocalDate = LocalDate.now(),
    ): TodayPlan {
        val notStarted = allVerses
            .filter { it.phase == VersePhase.NEW && it.addedDate == null }
            .sortedBy { it.orderIndex }
            .take(newVersesPerDay)
            .map { it.id }

        val due = allVerses
            .filter { it.addedDate != null && it.phase != VersePhase.MASTERED }
            .filter { it.nextReviewDate == null || !it.nextReviewDate.isAfter(today) }
            .sortedBy { it.nextReviewDate ?: LocalDate.MIN }
            .take(reviewVersesPerDay)
            .map { it.id }

        return TodayPlan(newVerseIds = notStarted, dueReviewIds = due)
    }

    /** Call when a verse is first introduced (moves NEW -> LEARNING, schedules tomorrow). */
    fun onIntroduced(progress: VerseProgress, today: LocalDate = LocalDate.now()): VerseProgress =
        progress.copy(
            phase = VersePhase.LEARNING,
            addedDate = today,
            lastReviewedDate = today,
            nextReviewDate = today.plusDays(intervalDaysFor(VersePhase.LEARNING).toLong()),
            consecutiveSuccesses = 0,
        )

    /** Call after a review attempt; `recalledOk` is whether the user recalled it correctly. */
    fun onReviewed(progress: VerseProgress, recalledOk: Boolean, today: LocalDate = LocalDate.now()): VerseProgress {
        if (!recalledOk) {
            // A miss resets progress within the current phase and comes back tomorrow.
            return progress.copy(
                lastReviewedDate = today,
                nextReviewDate = today.plusDays(1),
                consecutiveSuccesses = 0,
            )
        }

        val successes = progress.consecutiveSuccesses + 1
        val (nextPhase, resetCount) = when (progress.phase) {
            VersePhase.LEARNING -> if (successes >= LEARNING_TO_SHORT) VersePhase.REVIEW_SHORT to true else progress.phase to false
            VersePhase.REVIEW_SHORT -> if (successes >= SHORT_TO_LONG) VersePhase.REVIEW_LONG to true else progress.phase to false
            VersePhase.REVIEW_LONG -> if (successes >= LONG_TO_MASTERED) VersePhase.MASTERED to true else progress.phase to false
            else -> progress.phase to false
        }

        return progress.copy(
            phase = nextPhase,
            lastReviewedDate = today,
            nextReviewDate = today.plusDays(intervalDaysFor(nextPhase).toLong()),
            consecutiveSuccesses = if (resetCount) 0 else successes,
        )
    }
}

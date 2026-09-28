package com.firmlyplanted.app.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

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

    private fun currentDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

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
        today: LocalDate = currentDate(),
    ): TodayPlan {
        val notStarted = allVerses
            .filter { it.phase == VersePhase.NEW && it.addedDate == null }
            .sortedBy { it.orderIndex }
            .take(newVersesPerDay)
            .map { it.id }

        val due = allVerses
            .filter { it.addedDate != null && it.phase != VersePhase.MASTERED }
            .filter { it.nextReviewDate == null || it.nextReviewDate <= today }
            .sortedBy { it.nextReviewDate } // nulls (never scheduled) sort first
            .take(reviewVersesPerDay)
            .map { it.id }

        return TodayPlan(newVerseIds = notStarted, dueReviewIds = due)
    }

    /** Call when a verse is first introduced (moves NEW -> LEARNING, schedules tomorrow). */
    fun onIntroduced(progress: VerseProgress, today: LocalDate = currentDate()): VerseProgress =
        progress.copy(
            phase = VersePhase.LEARNING,
            addedDate = today,
            lastReviewedDate = today,
            nextReviewDate = today.plus(intervalDaysFor(VersePhase.LEARNING), DateTimeUnit.DAY),
            consecutiveSuccesses = 0,
        )

    /** Call after a review attempt; `recalledOk` is whether the user recalled it correctly. */
    fun onReviewed(progress: VerseProgress, recalledOk: Boolean, today: LocalDate = currentDate()): VerseProgress {
        if (!recalledOk) {
            // A miss resets progress within the current phase and comes back tomorrow.
            return progress.copy(
                lastReviewedDate = today,
                nextReviewDate = today.plus(1, DateTimeUnit.DAY),
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
            nextReviewDate = today.plus(intervalDaysFor(nextPhase), DateTimeUnit.DAY),
            consecutiveSuccesses = if (resetCount) 0 else successes,
        )
    }
}

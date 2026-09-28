package com.firmlyplanted.app.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewSchedulerTest {

    private val today = LocalDate(2026, 9, 28)

    private fun verse(
        id: String,
        order: Int,
        phase: VersePhase = VersePhase.NEW,
        added: LocalDate? = null,
        next: LocalDate? = null,
        successes: Int = 0,
    ) = VerseProgress(id, order, phase, added, added, next, successes)

    @Test
    fun planTodayTakesNewVersesInOrderAndDueMostOverdueFirst() {
        val verses = listOf(
            verse("n2", 2),
            verse("n1", 1),
            verse("due-late", 0, VersePhase.LEARNING, LocalDate(2026, 9, 1), LocalDate(2026, 9, 20)),
            verse("due-today", 0, VersePhase.LEARNING, LocalDate(2026, 9, 1), today),
            verse("future", 0, VersePhase.LEARNING, LocalDate(2026, 9, 1), LocalDate(2026, 9, 29)),
            verse("unscheduled", 0, VersePhase.LEARNING, LocalDate(2026, 9, 1), null),
        )

        val plan = ReviewScheduler.planToday(verses, newVersesPerDay = 1, reviewVersesPerDay = 5, today = today)

        assertEquals(listOf("n1"), plan.newVerseIds)
        assertEquals(listOf("unscheduled", "due-late", "due-today"), plan.dueReviewIds)
    }

    @Test
    fun introducedVerseIsScheduledForTomorrow() {
        val result = ReviewScheduler.onIntroduced(verse("a", 0), today)

        assertEquals(VersePhase.LEARNING, result.phase)
        assertEquals(LocalDate(2026, 9, 29), result.nextReviewDate)
    }

    @Test
    fun graduatingToShortReviewUsesThreeDayInterval() {
        val learning = verse("a", 0, VersePhase.LEARNING, LocalDate(2026, 9, 1), today, successes = 5)

        val result = ReviewScheduler.onReviewed(learning, recalledOk = true, today = today)

        assertEquals(VersePhase.REVIEW_SHORT, result.phase)
        assertEquals(0, result.consecutiveSuccesses)
        assertEquals(LocalDate(2026, 10, 1), result.nextReviewDate)
    }

    @Test
    fun missResetsSuccessesAndComesBackTomorrow() {
        val reviewing = verse("a", 0, VersePhase.REVIEW_LONG, LocalDate(2026, 8, 1), today, successes = 3)

        val result = ReviewScheduler.onReviewed(reviewing, recalledOk = false, today = today)

        assertEquals(VersePhase.REVIEW_LONG, result.phase)
        assertEquals(0, result.consecutiveSuccesses)
        assertEquals(LocalDate(2026, 9, 29), result.nextReviewDate)
    }
}

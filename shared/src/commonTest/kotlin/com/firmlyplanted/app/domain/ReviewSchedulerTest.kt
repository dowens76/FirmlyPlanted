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
        reviewed: LocalDate? = added,
    ) = VerseProgress(id, order, phase, added, reviewed, next, successes)

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

    private val tomorrow = LocalDate(2026, 9, 29)

    /** Four not-yet-started verses plus [introducedToday] verses already introduced today. */
    private fun passageWith(introducedToday: Int) =
        (1..introducedToday).map { verse("today$it", it, VersePhase.LEARNING, added = today, next = tomorrow) } +
            (1..4).map { verse("n$it", introducedToday + it) }

    @Test
    fun newVersesIntroducedTodayCountAgainstTheDailyCap() {
        val plan = ReviewScheduler.planToday(passageWith(introducedToday = 2), newVersesPerDay = 2, reviewVersesPerDay = 20, today = today)

        assertEquals(emptyList(), plan.newVerseIds)
        assertEquals(emptyList(), plan.dueReviewIds)
    }

    @Test
    fun partiallyUsedCapOffersOnlyTheRemainder() {
        val plan = ReviewScheduler.planToday(passageWith(introducedToday = 1), newVersesPerDay = 2, reviewVersesPerDay = 20, today = today)

        assertEquals(listOf("n1"), plan.newVerseIds)
    }

    @Test
    fun versesIntroducedOnEarlierDaysDoNotCount() {
        val plan = ReviewScheduler.planToday(passageWith(introducedToday = 2), newVersesPerDay = 2, reviewVersesPerDay = 20, today = tomorrow)

        assertEquals(listOf("n1", "n2"), plan.newVerseIds)
        assertEquals(listOf("today1", "today2"), plan.dueReviewIds)
    }

    @Test
    fun raisingThePaceMidDayAllowsMore() {
        val plan = ReviewScheduler.planToday(passageWith(introducedToday = 2), newVersesPerDay = 3, reviewVersesPerDay = 20, today = today)

        assertEquals(listOf("n1"), plan.newVerseIds)
    }

    private val earlier = LocalDate(2026, 9, 1)

    /** [reviewedToday] verses already reviewed today, plus four overdue verses (o1 most overdue). */
    private fun backlogWith(reviewedToday: Int) =
        (1..reviewedToday).map {
            verse("done$it", it, VersePhase.LEARNING, added = earlier, next = tomorrow, reviewed = today)
        } + (1..4).map {
            verse("o$it", reviewedToday + it, VersePhase.LEARNING, added = earlier, next = LocalDate(2026, 9, 20 + it))
        }

    @Test
    fun reviewsDoneTodayCountAgainstTheDailyCap() {
        val plan = ReviewScheduler.planToday(backlogWith(reviewedToday = 3), newVersesPerDay = 2, reviewVersesPerDay = 5, today = today)

        assertEquals(listOf("o1", "o2"), plan.dueReviewIds)
    }

    @Test
    fun usedUpReviewCapLeavesTheBacklogForTomorrow() {
        val verses = backlogWith(reviewedToday = 5)

        assertEquals(emptyList(), ReviewScheduler.planToday(verses, 2, reviewVersesPerDay = 5, today = today).dueReviewIds)
        assertEquals(
            listOf("o1", "o2", "o3", "o4", "done1"),
            ReviewScheduler.planToday(verses, 2, reviewVersesPerDay = 5, today = tomorrow).dueReviewIds,
        )
    }

    @Test
    fun introducingVersesDoesNotCountAsReviewing() {
        val verses = passageWith(introducedToday = 2) + backlogWith(reviewedToday = 0)

        val plan = ReviewScheduler.planToday(verses, newVersesPerDay = 2, reviewVersesPerDay = 3, today = today)

        assertEquals(listOf("o1", "o2", "o3"), plan.dueReviewIds)
    }

    @Test
    fun dueReviewsIgnoreTodaysCap() {
        assertEquals(listOf("o1", "o2"), ReviewScheduler.dueReviewIds(backlogWith(reviewedToday = 5), count = 2, today = today))
    }

    @Test
    fun upcomingNewVersesIgnoreTodaysCap() {
        assertEquals(listOf("n1", "n2"), ReviewScheduler.upcomingNewVerseIds(passageWith(introducedToday = 2), count = 2))
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

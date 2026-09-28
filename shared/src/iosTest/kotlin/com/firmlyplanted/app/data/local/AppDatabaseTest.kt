package com.firmlyplanted.app.data.local

import androidx.room.Room
import com.firmlyplanted.app.data.repository.toEntity
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.VersePhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDatabaseTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildWithDefaults()

    @AfterTest
    fun tearDown() = db.close()

    private val translation = DefaultTranslations.all.first().toEntity()

    private val project = MemoryProjectEntity(
        id = "p1",
        name = "John 1",
        translationId = translation.id,
        book = "John",
        startChapter = 1,
        startVerse = 1,
        endChapter = 1,
        endVerse = 3,
        newVersesPerDay = 2,
        reviewVersesPerDay = 10,
        status = ProjectStatus.ACTIVE,
        createdAt = LocalDateTime(2026, 9, 28, 7, 30, 15, 123_000_000),
        completedAt = null,
    )

    private fun verse(n: Int) = VerseEntity(
        id = "v$n",
        projectId = project.id,
        book = "John",
        chapter = 1,
        verseNumber = n,
        orderIndex = n - 1,
        text = "text $n",
        textCachedAt = LocalDateTime(2026, 9, 28, 7, 30),
        phase = VersePhase.LEARNING,
        addedDate = LocalDate(2026, 9, 27),
        lastReviewedDate = LocalDate(2026, 9, 27),
        nextReviewDate = LocalDate(2026, 9, 28),
        consecutiveSuccesses = 1,
    )

    private suspend fun seed() {
        db.translationDao().upsertAll(listOf(translation))
        db.memoryProjectDao().insert(project)
        db.verseDao().insertAll(listOf(verse(1), verse(2), verse(3)))
    }

    @Test
    fun entitiesRoundTripIncludingDates() = runBlocking {
        seed()

        assertEquals(project, db.memoryProjectDao().getById("p1"))
        assertEquals(listOf(verse(1), verse(2), verse(3)), db.verseDao().observeForProject("p1").first())
        assertEquals(translation, db.translationDao().getById(translation.id))
    }

    @Test
    fun evictTextOutsideKeepsOnlyTheWindow() = runBlocking {
        seed()

        db.verseDao().evictTextOutside("p1", keepIds = listOf("v2"))

        val verses = db.verseDao().getForProject("p1").associateBy { it.id }
        assertNull(verses.getValue("v1").text)
        assertNull(verses.getValue("v1").textCachedAt)
        assertEquals("text 2", verses.getValue("v2").text)
        assertNull(verses.getValue("v3").text)
    }

    @Test
    fun deletingProjectCascadesToVerses() = runBlocking {
        seed()

        db.memoryProjectDao().delete(project)

        assertEquals(0, db.verseDao().countForProject("p1"))
    }
}

package com.firmlyplanted.app.data.backup

import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.TranslationEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.TranslationSource
import com.firmlyplanted.app.domain.VersePhase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupCodecTest {

    private val createdAt = LocalDateTime(2026, 10, 7, 14, 30)

    private fun translation(id: String, source: TranslationSource) = TranslationEntity(
        id = id,
        displayName = id,
        language = "eng",
        source = source,
        sourceId = id,
        copyrightNoticeResName = "notice",
        licenseSummary = "license",
        maxCachedVerses = null,
        approvedWebReaderUrlTemplate = null,
        isDefault = false,
        testamentsCsv = "OLD,NEW",
    )

    private fun project(id: String, translationId: String) = MemoryProjectEntity(
        id = id,
        name = "Project $id",
        translationId = translationId,
        book = "John",
        startChapter = 1,
        startVerse = 1,
        endChapter = 1,
        endVerse = 2,
        newVersesPerDay = 1,
        reviewVersesPerDay = 10,
        status = ProjectStatus.ACTIVE,
        createdAt = createdAt,
        completedAt = null,
    )

    private fun verse(id: String, projectId: String, orderIndex: Int, text: String?) = VerseEntity(
        id = id,
        projectId = projectId,
        book = "John",
        chapter = 1,
        verseNumber = orderIndex + 1,
        orderIndex = orderIndex,
        text = text,
        textCachedAt = if (text != null) createdAt else null,
        phase = VersePhase.LEARNING,
        addedDate = LocalDate(2026, 10, 1),
        lastReviewedDate = LocalDate(2026, 10, 6),
        nextReviewDate = LocalDate(2026, 10, 7),
        consecutiveSuccesses = 3,
    )

    private val contents = BackupContents(
        translations = listOf(translation("esv", TranslationSource.ESV_API), translation("pasted-1", TranslationSource.PASTED)),
        projects = listOf(project("p1", "esv"), project("p2", "pasted-1")),
        verses = listOf(
            verse("v1", "p1", 0, "In the beginning was the Word"),
            verse("v2", "p1", 1, null),
            verse("v3", "p2", 0, "Pasted verse one"),
            verse("v4", "p2", 1, "Pasted verse two"),
        ),
    )

    @Test
    fun roundTripKeepsProjectsAndProgress() {
        val decoded = BackupCodec.decode(BackupCodec.encode(contents, "0.1.1 (2)", createdAt))

        assertEquals(contents.translations, decoded.translations)
        assertEquals(contents.projects, decoded.projects)
        assertEquals(contents.verses.map { it.id }, decoded.verses.map { it.id })
        decoded.verses.zip(contents.verses).forEach { (restored, original) ->
            assertEquals(original.projectId, restored.projectId)
            assertEquals(original.phase, restored.phase)
            assertEquals(original.addedDate, restored.addedDate)
            assertEquals(original.nextReviewDate, restored.nextReviewDate)
            assertEquals(original.consecutiveSuccesses, restored.consecutiveSuccesses)
        }
    }

    @Test
    fun keepsTextOnlyForPastedProjects() {
        val decoded = BackupCodec.decode(BackupCodec.encode(contents, "0.1.1 (2)", createdAt))
        val text = decoded.verses.associate { it.id to it.text }

        assertNull(text["v1"], "Fetched (licensed) text must not be written to the backup")
        assertEquals("Pasted verse one", text["v3"])
        assertEquals("Pasted verse two", text["v4"])
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertFailsWith<InvalidBackupException> { BackupCodec.decode("not json") }
        assertFailsWith<InvalidBackupException> { BackupCodec.decode("""{"hello": "world"}""") }
    }

    @Test
    fun rejectsBackupsFromNewerFormatVersions() {
        val newer = BackupCodec.encode(contents, "9.9.9", createdAt)
            .replace("\"formatVersion\": ${BackupCodec.FORMAT_VERSION}", "\"formatVersion\": ${BackupCodec.FORMAT_VERSION + 1}")
        val error = assertFailsWith<InvalidBackupException> { BackupCodec.decode(newer) }
        assertTrue(error.message!!.contains("newer version"))
    }
}

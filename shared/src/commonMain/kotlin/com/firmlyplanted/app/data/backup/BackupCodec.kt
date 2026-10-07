package com.firmlyplanted.app.data.backup

import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.TranslationEntity
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.TranslationSource
import com.firmlyplanted.app.domain.VersePhase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/*
 * The backup file format. These are deliberately separate from the Room entities so the database
 * schema can change without breaking backups already saved — bump [BackupCodec.FORMAT_VERSION]
 * and map old fields forward instead.
 */

@Serializable
data class BackupFile(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val createdAt: LocalDateTime,
    val translations: List<BackupTranslation>,
    val projects: List<BackupProject>,
)

@Serializable
data class BackupTranslation(
    val id: String,
    val displayName: String,
    val language: String,
    val source: TranslationSource,
    val sourceId: String,
    val copyrightNoticeResName: String,
    val licenseSummary: String,
    val maxCachedVerses: Int? = null,
    val approvedWebReaderUrlTemplate: String? = null,
    val isDefault: Boolean,
    val testamentsCsv: String,
)

@Serializable
data class BackupProject(
    val id: String,
    val name: String,
    val translationId: String,
    val book: String,
    val startChapter: Int,
    val startVerse: Int,
    val endChapter: Int,
    val endVerse: Int,
    val newVersesPerDay: Int,
    val reviewVersesPerDay: Int,
    val status: ProjectStatus,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val vie1925ScopeAcknowledged: Boolean = false,
    val verses: List<BackupVerse>,
)

@Serializable
data class BackupVerse(
    val id: String,
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val orderIndex: Int,
    /** Only present for pasted-text projects, whose text exists nowhere else; fetched text is re-downloaded. */
    val text: String? = null,
    val phase: VersePhase,
    val addedDate: LocalDate? = null,
    val lastReviewedDate: LocalDate? = null,
    val nextReviewDate: LocalDate? = null,
    val consecutiveSuccesses: Int,
)

/** What a backup holds, as database rows ready to insert. */
data class BackupContents(
    val translations: List<TranslationEntity>,
    val projects: List<MemoryProjectEntity>,
    val verses: List<VerseEntity>,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    const val FORMAT_ID = "firmly-planted-backup"
    const val FORMAT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Encodes [contents] as a backup file. Verse text is kept only for verses whose project uses a
     * pasted translation: for everything else it's a licensed, re-fetchable cache, so it's left out.
     */
    fun encode(contents: BackupContents, appVersion: String, createdAt: LocalDateTime): String {
        val pastedTranslationIds = contents.translations
            .filter { it.source == TranslationSource.PASTED }
            .map { it.id }
            .toSet()
        val versesByProject = contents.verses.groupBy { it.projectId }

        val file = BackupFile(
            format = FORMAT_ID,
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion,
            createdAt = createdAt,
            translations = contents.translations.map { it.toBackup() },
            projects = contents.projects.map { project ->
                val keepText = project.translationId in pastedTranslationIds
                project.toBackup(
                    verses = versesByProject[project.id].orEmpty()
                        .sortedBy { it.orderIndex }
                        .map { it.toBackup(keepText) },
                )
            },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** Parses a backup file, throwing [InvalidBackupException] with a user-readable message if it can't be used. */
    fun decode(text: String): BackupContents {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("This file isn't a Firmly Planted backup.", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This file isn't a Firmly Planted backup.", e)
        }
        if (file.format != FORMAT_ID) throw InvalidBackupException("This file isn't a Firmly Planted backup.")
        if (file.formatVersion > FORMAT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of Firmly Planted. Update the app, then try again.")
        }

        val translationIds = file.translations.map { it.id }.toSet()
        file.projects.firstOrNull { it.translationId !in translationIds }?.let {
            throw InvalidBackupException("This backup is incomplete: the translation for \"${it.name}\" is missing.")
        }

        return BackupContents(
            translations = file.translations.map { it.toEntity() },
            projects = file.projects.map { it.toEntity() },
            verses = file.projects.flatMap { project -> project.verses.map { it.toEntity(project.id) } },
        )
    }

    private fun TranslationEntity.toBackup() = BackupTranslation(
        id, displayName, language, source, sourceId, copyrightNoticeResName, licenseSummary,
        maxCachedVerses, approvedWebReaderUrlTemplate, isDefault, testamentsCsv,
    )

    private fun BackupTranslation.toEntity() = TranslationEntity(
        id, displayName, language, source, sourceId, copyrightNoticeResName, licenseSummary,
        maxCachedVerses, approvedWebReaderUrlTemplate, isDefault, testamentsCsv,
    )

    private fun MemoryProjectEntity.toBackup(verses: List<BackupVerse>) = BackupProject(
        id, name, translationId, book, startChapter, startVerse, endChapter, endVerse,
        newVersesPerDay, reviewVersesPerDay, status, createdAt, completedAt, vie1925ScopeAcknowledged, verses,
    )

    private fun BackupProject.toEntity() = MemoryProjectEntity(
        id, name, translationId, book, startChapter, startVerse, endChapter, endVerse,
        newVersesPerDay, reviewVersesPerDay, status, createdAt, completedAt, vie1925ScopeAcknowledged,
    )

    private fun VerseEntity.toBackup(keepText: Boolean) = BackupVerse(
        id = id,
        book = book,
        chapter = chapter,
        verseNumber = verseNumber,
        orderIndex = orderIndex,
        text = if (keepText) text else null,
        phase = phase,
        addedDate = addedDate,
        lastReviewedDate = lastReviewedDate,
        nextReviewDate = nextReviewDate,
        consecutiveSuccesses = consecutiveSuccesses,
    )

    private fun BackupVerse.toEntity(projectId: String) = VerseEntity(
        id = id,
        projectId = projectId,
        book = book,
        chapter = chapter,
        verseNumber = verseNumber,
        orderIndex = orderIndex,
        text = text,
        // Restored text is a fresh copy as far as the rolling cache is concerned; unknown here.
        textCachedAt = null,
        phase = phase,
        addedDate = addedDate,
        lastReviewedDate = lastReviewedDate,
        nextReviewDate = nextReviewDate,
        consecutiveSuccesses = consecutiveSuccesses,
    )
}

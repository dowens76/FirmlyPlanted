package com.firmlyplanted.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.TranslationSource
import com.firmlyplanted.app.domain.VersePhase
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "translations")
data class TranslationEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val language: String,
    val source: TranslationSource,
    val sourceId: String,
    val copyrightNoticeResName: String,
    val licenseSummary: String,
    val maxCachedVerses: Int?,
    val approvedWebReaderUrlTemplate: String?,
    val isDefault: Boolean,
    val testamentsCsv: String, // "OLD,NEW"
)

@Entity(
    tableName = "memory_projects",
    foreignKeys = [
        ForeignKey(
            entity = TranslationEntity::class,
            parentColumns = ["id"],
            childColumns = ["translationId"],
        ),
    ],
    indices = [Index("translationId")],
)
data class MemoryProjectEntity(
    @PrimaryKey val id: String,
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
    val completedAt: LocalDateTime?,
    val vie1925ScopeAcknowledged: Boolean = false,
)

@Entity(
    tableName = "verses",
    foreignKeys = [
        ForeignKey(
            entity = MemoryProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index(value = ["projectId", "orderIndex"], unique = true)],
)
data class VerseEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val book: String,
    val chapter: Int,
    val verseNumber: Int,
    val orderIndex: Int,
    /** Cached text — only populated for verses in the current rolling window. Null otherwise. */
    val text: String?,
    val textCachedAt: LocalDateTime?,
    val phase: VersePhase,
    val addedDate: LocalDate?,
    val lastReviewedDate: LocalDate?,
    val nextReviewDate: LocalDate?,
    val consecutiveSuccesses: Int,
)

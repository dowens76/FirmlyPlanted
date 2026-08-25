package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.local.MemoryProjectDao
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseDao
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.domain.BookCatalog
import com.firmlyplanted.app.domain.LicensePolicy
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.ReviewScheduler
import com.firmlyplanted.app.domain.ScopeCheck
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.TodayPlan
import com.firmlyplanted.app.domain.VersePhase
import com.firmlyplanted.app.domain.VerseProgress
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.util.UUID

class ScopeBlockedException(val messageResName: String) : Exception()

data class ScopePreview(val verseCount: Int, val check: ScopeCheck)

class ProjectRepository(
    private val projectDao: MemoryProjectDao,
    private val verseDao: VerseDao,
    private val textFetcher: TextFetcher,
) {
    companion object {
        /** Verses of context cached on either side of a verse actively in play today. */
        const val WINDOW_PADDING = 8
    }

    fun observeProjects(): Flow<List<MemoryProjectEntity>> = projectDao.observeAll()

    fun observeProject(projectId: String): Flow<MemoryProjectEntity?> = projectDao.observeById(projectId)

    fun observeVerses(projectId: String): Flow<List<VerseEntity>> = verseDao.observeForProject(projectId)

    /**
     * Creates a project: resolves the real verse boundaries of the requested scope from the
     * live source (not a hardcoded table), validates it against that translation's license cap,
     * then stores one row per verse (metadata only) with text cached for the first day's window.
     * Requires network access — there's no way to know a passage's true verse boundaries offline.
     */
    suspend fun createProject(
        name: String,
        translation: Translation,
        bookName: String,
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int,
        newVersesPerDay: Int,
        reviewVersesPerDay: Int,
    ): Result<String> {
        val bookCode = BookCatalog.byName(bookName)?.code
            ?: return Result.failure(IllegalArgumentException("Unknown book: $bookName"))

        val verses = runCatching {
            textFetcher.fetchRange(translation, bookName, bookCode, startChapter, startVerse, endChapter, endVerse)
        }.getOrElse { return Result.failure(it) }

        if (verses.isEmpty()) {
            return Result.failure(IllegalStateException("No verses found for that reference — check the range and try again."))
        }

        when (val check = LicensePolicy.checkScope(translation, verses.size)) {
            is ScopeCheck.Blocked -> return Result.failure(ScopeBlockedException(check.messageResName))
            ScopeCheck.Ok -> Unit
        }

        val projectId = UUID.randomUUID().toString()
        projectDao.insert(
            MemoryProjectEntity(
                id = projectId,
                name = name,
                translationId = translation.id,
                book = bookName,
                startChapter = startChapter,
                startVerse = startVerse,
                endChapter = endChapter,
                endVerse = endVerse,
                newVersesPerDay = newVersesPerDay,
                reviewVersesPerDay = reviewVersesPerDay,
                status = ProjectStatus.ACTIVE,
                createdAt = LocalDateTime.now(),
                completedAt = null,
            ),
        )

        val initialWindowCount = (newVersesPerDay + WINDOW_PADDING).coerceAtMost(verses.size)
        val now = LocalDateTime.now()
        val verseEntities = verses.mapIndexed { index, v ->
            val cached = index < initialWindowCount
            VerseEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                book = bookName,
                chapter = v.chapter,
                verseNumber = v.verse,
                orderIndex = index,
                text = if (cached) v.text else null,
                textCachedAt = if (cached) now else null,
                phase = VersePhase.NEW,
                addedDate = null,
                lastReviewedDate = null,
                nextReviewDate = null,
                consecutiveSuccesses = 0,
            )
        }
        verseDao.insertAll(verseEntities)

        return Result.success(projectId)
    }

    /** Resolves real verse boundaries + license-cap check for a candidate scope, without persisting anything. */
    suspend fun previewScope(
        translation: Translation,
        bookName: String,
        startChapter: Int,
        startVerse: Int,
        endChapter: Int,
        endVerse: Int,
    ): Result<ScopePreview> {
        val bookCode = BookCatalog.byName(bookName)?.code
            ?: return Result.failure(IllegalArgumentException("Unknown book: $bookName"))
        val verses = runCatching {
            textFetcher.fetchRange(translation, bookName, bookCode, startChapter, startVerse, endChapter, endVerse)
        }.getOrElse { return Result.failure(it) }
        return Result.success(ScopePreview(verses.size, LicensePolicy.checkScope(translation, verses.size)))
    }

    suspend fun getTodayPlan(projectId: String): TodayPlan {
        val verses = verseDao.getForProject(projectId)
        val project = projectDao.getById(projectId) ?: return TodayPlan(emptyList(), emptyList())
        return ReviewScheduler.planToday(
            allVerses = verses.map { it.toProgress() },
            newVersesPerDay = project.newVersesPerDay,
            reviewVersesPerDay = project.reviewVersesPerDay,
        )
    }

    /**
     * Ensures cached text covers today's active verses (new + due) plus WINDOW_PADDING verses
     * of surrounding context, fetching only what's missing, then evicts any cached text that
     * has fallen outside every current window — so Room's actual cache never grows beyond a
     * small multiple of the daily verse counts, however large the project's declared scope is.
     * No-ops the fetch (but still evicts) when offline.
     */
    suspend fun ensureWindowCached(projectId: String, translation: Translation, isOnline: Boolean) {
        val project = projectDao.getById(projectId) ?: return
        val allVerses = verseDao.getForProject(projectId).sortedBy { it.orderIndex }
        if (allVerses.isEmpty()) return

        val plan = ReviewScheduler.planToday(
            allVerses = allVerses.map { it.toProgress() },
            newVersesPerDay = project.newVersesPerDay,
            reviewVersesPerDay = project.reviewVersesPerDay,
        )
        val focusIds = (plan.newVerseIds + plan.dueReviewIds).toSet()
        val focusIndices = allVerses.filter { it.id in focusIds }.map { it.orderIndex }
        // A brand-new project has no due/new ids computed yet on first ever open; fall back to
        // the start of the passage so there's always something to show.
        val effectiveFocusIndices = focusIndices.ifEmpty { listOf(0) }

        val keepIndices = mutableSetOf<Int>()
        for (index in effectiveFocusIndices) {
            val lo = (index - WINDOW_PADDING).coerceAtLeast(0)
            val hi = (index + WINDOW_PADDING).coerceAtMost(allVerses.size - 1)
            for (i in lo..hi) keepIndices += i
        }

        val keepVerses = allVerses.filter { it.orderIndex in keepIndices }

        if (isOnline) {
            val missing = keepVerses.filter { it.text == null }
            if (missing.isNotEmpty()) {
                val bookCode = BookCatalog.byName(project.book)?.code ?: project.book
                val refs = missing.map { TextRef(it.chapter, it.verseNumber) }
                val fetched = runCatching {
                    textFetcher.fetchText(translation, project.book, bookCode, refs)
                }.getOrDefault(emptyMap())

                val now = LocalDateTime.now()
                val updated = missing.mapNotNull { verse ->
                    fetched[TextRef(verse.chapter, verse.verseNumber)]?.let { text ->
                        verse.copy(text = text, textCachedAt = now)
                    }
                }
                if (updated.isNotEmpty()) verseDao.updateAll(updated)
            }
        }

        verseDao.evictTextOutside(projectId, keepVerses.map { it.id })
    }

    suspend fun updatePace(projectId: String, newVersesPerDay: Int, reviewVersesPerDay: Int) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.update(project.copy(newVersesPerDay = newVersesPerDay, reviewVersesPerDay = reviewVersesPerDay))
    }

    suspend fun markIntroduced(verseId: String) = updateVerseProgress(verseId) { ReviewScheduler.onIntroduced(it) }

    suspend fun markReviewed(verseId: String, recalledOk: Boolean) =
        updateVerseProgress(verseId) { ReviewScheduler.onReviewed(it, recalledOk) }

    private suspend fun updateVerseProgress(verseId: String, transform: (VerseProgress) -> VerseProgress) {
        val verse = verseDao.getByIds(listOf(verseId)).firstOrNull() ?: return
        val updatedProgress = transform(verse.toProgress())
        verseDao.update(verse.withProgress(updatedProgress))
    }

    /** Clears cached text for a project (kept for ACTIVE projects too, e.g. on manual "clear cache"). */
    suspend fun clearCache(projectId: String) = verseDao.clearAllText(projectId)

    suspend fun completeProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.update(project.copy(status = ProjectStatus.COMPLETED, completedAt = LocalDateTime.now()))
        verseDao.clearAllText(projectId)
    }

    suspend fun archiveProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.update(project.copy(status = ProjectStatus.ARCHIVED))
        verseDao.clearAllText(projectId)
    }

    suspend fun deleteProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.delete(project)
    }

    suspend fun progressSummary(projectId: String): Pair<Int, Int> {
        val verses = verseDao.getForProject(projectId)
        return verses.count { it.phase == VersePhase.MASTERED } to verses.size
    }
}

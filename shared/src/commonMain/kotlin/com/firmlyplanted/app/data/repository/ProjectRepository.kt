package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.local.MemoryProjectDao
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.TranslationDao
import com.firmlyplanted.app.data.local.VerseDao
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.domain.BookCatalog
import com.firmlyplanted.app.domain.LicensePolicy
import com.firmlyplanted.app.domain.PastedTranslations
import com.firmlyplanted.app.domain.PastedVerse
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.domain.ReviewScheduler
import com.firmlyplanted.app.domain.ScopeCheck
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.TodayPlan
import com.firmlyplanted.app.domain.TranslationSource
import com.firmlyplanted.app.domain.VersePhase
import com.firmlyplanted.app.domain.VerseProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun currentDateTime(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private fun newId(): String = Uuid.random().toString()

class ScopeBlockedException(val messageResName: String) : Exception()

data class ScopePreview(val verseCount: Int, val check: ScopeCheck)

class ProjectRepository(
    private val projectDao: MemoryProjectDao,
    private val verseDao: VerseDao,
    private val translationDao: TranslationDao,
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

        val projectId = newId()
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
                createdAt = currentDateTime(),
                completedAt = null,
            ),
        )

        val initialWindowCount = (newVersesPerDay + WINDOW_PADDING).coerceAtMost(verses.size)
        val now = currentDateTime()
        val verseEntities = verses.mapIndexed { index, v ->
            val cached = index < initialWindowCount
            VerseEntity(
                id = newId(),
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

    /**
     * Creates a project from pasted text (see PastedTextParser). Unlike fetched projects, every
     * verse's text is stored up front and kept for the life of the project — there's no source to
     * re-fetch it from — so each pasted project gets its own Translation row describing the paste.
     */
    suspend fun createPastedProject(
        name: String,
        bookName: String,
        verses: List<PastedVerse>,
        translationLabel: String,
        language: String,
        link: String?,
        newVersesPerDay: Int,
        reviewVersesPerDay: Int,
    ): Result<String> {
        if (verses.isEmpty()) return Result.failure(IllegalArgumentException("No verses to memorize."))
        val translation = PastedTranslations.create(PastedTranslations.ID_PREFIX + newId(), translationLabel, language, link)
        translationDao.upsertAll(listOf(translation.toEntity()))

        val projectId = newId()
        val first = verses.first()
        val last = verses.last()
        projectDao.insert(
            MemoryProjectEntity(
                id = projectId,
                name = name,
                translationId = translation.id,
                book = bookName,
                startChapter = first.chapter,
                startVerse = first.verse,
                endChapter = last.chapter,
                endVerse = last.verse,
                newVersesPerDay = newVersesPerDay,
                reviewVersesPerDay = reviewVersesPerDay,
                status = ProjectStatus.ACTIVE,
                createdAt = currentDateTime(),
                completedAt = null,
            ),
        )
        val now = currentDateTime()
        verseDao.insertAll(
            verses.mapIndexed { index, v ->
                VerseEntity(
                    id = newId(),
                    projectId = projectId,
                    book = bookName,
                    chapter = v.chapter,
                    verseNumber = v.verse,
                    orderIndex = index,
                    text = v.text,
                    textCachedAt = now,
                    phase = VersePhase.NEW,
                    addedDate = null,
                    lastReviewedDate = null,
                    nextReviewDate = null,
                    consecutiveSuccesses = 0,
                )
            },
        )
        return Result.success(projectId)
    }

    /** Whether the project's text was pasted in — its text is the only copy, so it's never evicted. */
    suspend fun isPasted(projectId: String): Boolean {
        val project = projectDao.getById(projectId) ?: return false
        return translationDao.getById(project.translationId)?.source == TranslationSource.PASTED
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
     * Ensures cached text covers today's active verses (new + due) and the next session's worth
     * of new verses and backlogged reviews, plus WINDOW_PADDING verses of surrounding context,
     * fetching only what's missing, then evicts any cached text that has fallen outside every
     * current window — so Room's actual cache never grows beyond a small multiple of the daily
     * verse counts, however large the project's declared scope is. No-ops the fetch (but still
     * evicts) when offline.
     */
    suspend fun ensureWindowCached(projectId: String, translation: Translation, isOnline: Boolean) {
        if (translation.source == TranslationSource.PASTED) return // Stored in full; nothing to fetch or evict.
        val project = projectDao.getById(projectId) ?: return
        val allVerses = verseDao.getForProject(projectId).sortedBy { it.orderIndex }
        if (allVerses.isEmpty()) return

        val progress = allVerses.map { it.toProgress() }
        val plan = ReviewScheduler.planToday(
            allVerses = progress,
            newVersesPerDay = project.newVersesPerDay,
            reviewVersesPerDay = project.reviewVersesPerDay,
        )
        // Upcoming new verses and backlogged reviews are kept even once today's caps are used up,
        // so they're already cached if the next session starts offline.
        val upcomingIds = ReviewScheduler.upcomingNewVerseIds(progress, project.newVersesPerDay) +
            ReviewScheduler.dueReviewIds(progress, project.reviewVersesPerDay)
        val focusIds = (plan.newVerseIds + plan.dueReviewIds + upcomingIds).toSet()
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

                val now = currentDateTime()
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

    /**
     * Clears cached text for a project (kept for ACTIVE projects too, e.g. on manual "clear cache").
     * Pasted projects are skipped: their text is the only copy, not a cache.
     */
    suspend fun clearCache(projectId: String) {
        if (!isPasted(projectId)) verseDao.clearAllText(projectId)
    }

    suspend fun completeProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.update(project.copy(status = ProjectStatus.COMPLETED, completedAt = currentDateTime()))
        clearCache(projectId)
    }

    suspend fun archiveProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        projectDao.update(project.copy(status = ProjectStatus.ARCHIVED))
        clearCache(projectId)
    }

    suspend fun deleteProject(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        val pasted = isPasted(projectId)
        projectDao.delete(project)
        // A pasted project's Translation row belongs to it alone (see createPastedProject).
        if (pasted) translationDao.deleteById(project.translationId)
    }

    suspend fun progressSummary(projectId: String): Pair<Int, Int> {
        val verses = verseDao.getForProject(projectId)
        return verses.count { it.phase == VersePhase.MASTERED } to verses.size
    }
}

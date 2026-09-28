package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.local.TranslationDao
import com.firmlyplanted.app.data.remote.FetchBibleService
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.domain.Testament
import com.firmlyplanted.app.domain.Translation
import com.firmlyplanted.app.domain.TranslationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TranslationRepository(
    private val translationDao: TranslationDao,
    private val fetchBibleService: FetchBibleService,
) {
    suspend fun ensureDefaultsSeeded() {
        translationDao.upsertAll(DefaultTranslations.all.map { it.toEntity() })
    }

    fun observeAvailable(): Flow<List<Translation>> =
        translationDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Translation? = translationDao.getById(id)?.toDomain()

    /**
     * The "More" catalog: every other fetch.bible translation, for the language the user picks.
     * Not persisted wholesale — this is a big catalog (1000+ entries) and only the four defaults
     * plus whatever a user actually adds to a project need to live in Room.
     */
    suspend fun fetchMoreCatalog(languageFilter: String? = null): List<Translation> {
        val manifest = fetchBibleService.getManifest()
        val defaultIds = DefaultTranslations.all.map { it.sourceId }.toSet()

        return manifest.bibles.entries
            .asSequence()
            .filter { (id, _) -> id !in defaultIds }
            .filter { (_, entry) -> languageFilter.isNullOrBlank() || entry.name.english.contains(languageFilter, ignoreCase = true) }
            .map { (id, entry) ->
                val testaments = buildSet {
                    if (entry.hasOldTestament()) add(Testament.OLD)
                    if (entry.hasNewTestament()) add(Testament.NEW)
                }
                Translation(
                    id = id,
                    displayName = entry.name.english.ifBlank { id },
                    language = entry.name.local.ifBlank { "Unknown" },
                    source = TranslationSource.FETCH_BIBLE,
                    sourceId = id,
                    copyrightNoticeResName = "copyright_fetch_bible_generic",
                    licenseSummary = entry.copyright?.buildNotice()
                        ?: "License not specified by fetch.bible — treat as all-rights-reserved.",
                    maxCachedVerses = null,
                    approvedWebReaderUrlTemplate = null,
                    isDefault = false,
                    testaments = testaments.ifEmpty { setOf(Testament.OLD, Testament.NEW) },
                )
            }
            .sortedBy { it.displayName }
            .toList()
    }

    /** Persists a "More"-catalog translation once a project is actually created with it. */
    suspend fun cacheTranslation(translation: Translation) {
        translationDao.upsertAll(listOf(translation.toEntity()))
    }
}

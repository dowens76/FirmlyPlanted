package com.firmlyplanted.app

import com.firmlyplanted.app.data.local.AppDatabase
import com.firmlyplanted.app.data.remote.NetworkModule
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.TextFetcher
import com.firmlyplanted.app.data.repository.TranslationRepository

/** Simple hand-wired dependency graph — deliberately no Hilt/DI framework, see LICENSING.md
 * and the project plan for why (fewer moving parts to get right without a local build/CI
 * loop to verify against). Each platform supplies its database and connectivity check. */
class AppContainer(
    database: AppDatabase,
    /** Whether a network connection is currently available (checked before fetching verse text). */
    val isOnline: () -> Boolean,
) {
    val translationRepository = TranslationRepository(
        translationDao = database.translationDao(),
        fetchBibleService = NetworkModule.fetchBibleApi,
    )

    private val textFetcher = TextFetcher(
        esvApi = NetworkModule.esvApi,
        fetchBibleApi = NetworkModule.fetchBibleApi,
        esvApiKey = Secrets.ESV_API_KEY,
    )

    val projectRepository = ProjectRepository(
        projectDao = database.memoryProjectDao(),
        verseDao = database.verseDao(),
        textFetcher = textFetcher,
    )
}

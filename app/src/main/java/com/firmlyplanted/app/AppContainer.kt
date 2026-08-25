package com.firmlyplanted.app

import android.content.Context
import com.firmlyplanted.app.data.local.AppDatabase
import com.firmlyplanted.app.data.remote.NetworkModule
import com.firmlyplanted.app.data.repository.ProjectRepository
import com.firmlyplanted.app.data.repository.TextFetcher
import com.firmlyplanted.app.data.repository.TranslationRepository

/** Simple hand-wired dependency graph — deliberately no Hilt/DI framework, see LICENSING.md
 * and the project plan for why (fewer moving parts to get right without a local build/CI
 * loop to verify against). */
class AppContainer(context: Context) {
    private val database = AppDatabase.get(context)

    val translationRepository = TranslationRepository(
        translationDao = database.translationDao(),
        fetchBibleService = NetworkModule.fetchBibleApi,
    )

    private val textFetcher = TextFetcher(
        esvApi = NetworkModule.esvApi,
        fetchBibleApi = NetworkModule.fetchBibleApi,
        esvApiKey = BuildConfig.ESV_API_KEY,
    )

    val projectRepository = ProjectRepository(
        projectDao = database.memoryProjectDao(),
        verseDao = database.verseDao(),
        textFetcher = textFetcher,
    )
}

package com.firmlyplanted.app.data.repository

import androidx.room.Room
import com.firmlyplanted.app.data.local.AppDatabase
import com.firmlyplanted.app.data.local.buildWithDefaults
import com.firmlyplanted.app.data.remote.NetworkModule
import com.firmlyplanted.app.domain.PastedTextParser
import com.firmlyplanted.app.domain.TranslationSource
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pasted text is the only copy, so none of the cache-trimming paths may ever clear it. */
class PastedProjectTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildWithDefaults()

    // TextFetcher is never used for pasted projects; the real services just satisfy the constructor.
    private val repository = ProjectRepository(
        projectDao = db.memoryProjectDao(),
        verseDao = db.verseDao(),
        translationDao = db.translationDao(),
        textFetcher = TextFetcher(NetworkModule.esvApi, NetworkModule.fetchBibleApi, esvApiKey = ""),
    )

    @AfterTest
    fun tearDown() = db.close()

    // Twenty verses so the rolling cache window (focus ± 8) would evict some if it applied.
    private val pasted = (1..20).joinToString(" ") { "$it Verse number $it text." } + "\nPsalm 119:1-20 WEB"

    private suspend fun createProject(): String {
        val passage = PastedTextParser.parse(pasted).getOrThrow()
        return repository.createPastedProject(
            name = "Psalm 119",
            bookName = passage.reference.book!!.name,
            verses = passage.verses,
            translationLabel = "WEB",
            language = "Other",
            link = null,
            newVersesPerDay = 2,
            reviewVersesPerDay = 20,
        ).getOrThrow()
    }

    private suspend fun storedTexts(projectId: String) = db.verseDao().getForProject(projectId).map { it.text }

    @Test
    fun storesEveryVerseWithItsOwnTranslation() = runBlocking {
        val projectId = createProject()

        val project = db.memoryProjectDao().getById(projectId)!!
        val translation = db.translationDao().getById(project.translationId)!!
        assertEquals(TranslationSource.PASTED, translation.source)
        assertEquals("WEB", translation.displayName)
        assertEquals(listOf(119, 1, 119, 20), listOf(project.startChapter, project.startVerse, project.endChapter, project.endVerse))
        assertEquals((1..20).map { "Verse number $it text." }, storedTexts(projectId))
        assertTrue(repository.isPasted(projectId))
    }

    @Test
    fun cacheWindowClearCacheAndCompleteNeverRemoveText() = runBlocking {
        val projectId = createProject()
        val translation = db.translationDao().getById(db.memoryProjectDao().getById(projectId)!!.translationId)!!.toDomain()

        repository.ensureWindowCached(projectId, translation, isOnline = false)
        repository.clearCache(projectId)
        repository.archiveProject(projectId)
        repository.completeProject(projectId)

        assertTrue(storedTexts(projectId).all { it != null })
    }

    @Test
    fun deletingTheProjectRemovesItsTranslation() = runBlocking {
        val projectId = createProject()
        val translationId = db.memoryProjectDao().getById(projectId)!!.translationId

        repository.deleteProject(projectId)

        assertNull(db.translationDao().getById(translationId))
        assertFalse(repository.isPasted(projectId))
    }
}

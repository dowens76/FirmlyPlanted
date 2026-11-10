package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.audio.AudioFileStore
import com.firmlyplanted.app.data.audio.SmilClip
import com.firmlyplanted.app.data.audio.SmilParser
import com.firmlyplanted.app.data.audio.VerseAudio
import com.firmlyplanted.app.data.audio.VerseRanges
import com.firmlyplanted.app.data.local.MemoryProjectDao
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseDao
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.remote.EbibleAudioService
import com.firmlyplanted.app.domain.AudioSpeed
import com.firmlyplanted.app.domain.BookCatalog
import com.firmlyplanted.app.domain.EbibleAudioEdition
import com.firmlyplanted.app.domain.EbibleAudioRefs
import com.firmlyplanted.app.domain.ProjectStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface AudioDownloadState {
    /** [done] of [total] verses with audio are on the device. */
    data class Downloading(val done: Int, val total: Int) : AudioDownloadState
    data class Complete(val total: Int) : AudioDownloadState

    /** Not finished: [message] says why (e.g. offline); the next ensure call resumes it. */
    data class Incomplete(val done: Int, val total: Int, val message: String) : AudioDownloadState
}

/**
 * Verse audio from eBible.org's audio ePubs, for projects in a translation that has a recording
 * ([EbibleAudioRefs.forTranslation]): Abraham Shmuelof's Hebrew reading for WLC, Andy Martin's
 * Greek reading (Nestle 1904) for SBLGNT.
 * For every ePub chapter a project's verses touch, the chapter recording (MP3) and its media
 * overlay (SMIL, the start/end time of each verse) are saved exactly as they are in the ePub. To
 * play a verse, [verseAudio] looks up its span in the SMIL and the player plays just that range
 * of the chapter file, as an EPUB reading system would; the recording itself is never cut or
 * altered. Files stay until the project is completed, archived or deleted ([removeProjectAudio]).
 */
class AudioRepository(
    private val projectDao: MemoryProjectDao,
    private val verseDao: VerseDao,
    private val store: AudioFileStore,
    private val service: EbibleAudioService,
    private val isOnline: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobsLock = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, AudioDownloadState>>(emptyMap())

    /** Download progress per project id; absent until [ensureProjectAudio] has run for it. */
    val states: StateFlow<Map<String, AudioDownloadState>> = _states.asStateFlow()

    /** Parsed SMIL per file path. Only touched from the UI thread ([verseAudio]). */
    private val smilCache = mutableMapOf<String, List<SmilClip>>()

    /** The recording this project's verses can be played from, or null if there's none for its book. */
    fun editionFor(project: MemoryProjectEntity?): EbibleAudioEdition? =
        project?.let { EbibleAudioRefs.forTranslation(it.translationId) }?.takeIf { it.bookFile(project.book) != null }

    fun supportsAudio(project: MemoryProjectEntity?): Boolean = editionFor(project) != null

    /** True when the recording's wording for this verse differs from the project's text. */
    fun wordingDiffers(project: MemoryProjectEntity, verse: VerseEntity): Boolean =
        editionFor(project)?.wordingDiffers(project.book, verse.chapter, verse.verseNumber) == true

    /**
     * Where the verse is in the downloaded chapter recordings, or null when it has no audio or its
     * chapter isn't downloaded yet. Call from the UI thread.
     */
    fun verseAudio(project: MemoryProjectEntity, verse: VerseEntity): VerseAudio? {
        val edition = editionFor(project) ?: return null
        val speed = project.audioSpeed
        val refs = edition.resolve(project.book, verse.chapter, verse.verseNumber)
        val segments = VerseRanges.segments(
            refs,
            clipsFor = { chapter ->
                if (!store.hasChapter(project.id, speed, chapter)) {
                    null
                } else {
                    val path = store.smilPath(project.id, speed, chapter)
                    smilCache[path] ?: store.readSmil(project.id, speed, chapter)
                        ?.let { SmilParser.parse(it) }
                        ?.also { smilCache[path] = it }
                }
            },
            pathFor = { chapter -> store.mp3Path(project.id, speed, chapter) },
        ) ?: return null
        return VerseAudio("${project.id}/${speed.name}/${verse.chapter}:${verse.verseNumber}", segments)
    }

    /**
     * Starts (or resumes) downloading the project's missing chapters in the background; returns at
     * once. Does nothing for non-WLC or inactive projects, or if a download is already running.
     */
    fun ensureProjectAudio(projectId: String) {
        scope.launch {
            jobsLock.withLock {
                if (jobs[projectId]?.isActive == true) return@launch
                jobs[projectId] = scope.launch { download(projectId) }
            }
        }
    }

    /** Switches the project's recording, deleting the other speed's files, then re-downloads. */
    suspend fun setSpeed(projectId: String, speed: AudioSpeed) {
        cancel(projectId)
        val project = projectDao.getById(projectId) ?: return
        if (project.audioSpeed == speed) {
            ensureProjectAudio(projectId)
            return
        }
        projectDao.update(project.copy(audioSpeed = speed))
        withContext(Dispatchers.IO) { store.deleteSpeed(projectId, project.audioSpeed) }
        ensureProjectAudio(projectId)
    }

    /** Stops any download and deletes all of the project's audio files. */
    suspend fun removeProjectAudio(projectId: String) {
        cancel(projectId)
        withContext(Dispatchers.IO) { store.deleteProject(projectId) }
        _states.update { it - projectId }
    }

    private suspend fun cancel(projectId: String) {
        val job = jobsLock.withLock { jobs.remove(projectId) }
        job?.cancelAndJoin()
    }

    private fun publish(projectId: String, state: AudioDownloadState) = _states.update { it + (projectId to state) }

    private suspend fun download(projectId: String) {
        val project = projectDao.getById(projectId) ?: return
        val edition = editionFor(project) ?: return
        if (project.status != ProjectStatus.ACTIVE) return
        val speed = project.audioSpeed
        val bookCode = BookCatalog.byName(project.book)?.code ?: return

        // Progress is counted in verses: a verse is ready once every ePub chapter it touches is.
        val verseChapters = verseDao.getForProject(projectId)
            .map { edition.resolve(project.book, it.chapter, it.verseNumber).map { ref -> ref.chapter }.toSet() }
            .filter { it.isNotEmpty() }
        val total = verseChapters.size
        val needed = verseChapters.flatten().distinct().sorted()
        val have = needed.filterTo(mutableSetOf()) { store.hasChapter(projectId, speed, it) }
        fun done() = verseChapters.count { have.containsAll(it) }

        if (have.size == needed.size) {
            publish(projectId, AudioDownloadState.Complete(total))
            return
        }
        if (!isOnline()) {
            publish(projectId, AudioDownloadState.Incomplete(done(), total, "Waiting for a connection to download audio."))
            return
        }
        publish(projectId, AudioDownloadState.Downloading(done(), total))
        try {
            for (chapter in needed) {
                if (chapter in have) continue
                val audio = service.fetchChapter(edition, project.book, bookCode, chapter, speed)
                store.writeChapter(projectId, speed, chapter, audio.mp3, audio.smil)
                have += chapter
                publish(projectId, AudioDownloadState.Downloading(done(), total))
            }
            publish(projectId, AudioDownloadState.Complete(total))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            publish(projectId, AudioDownloadState.Incomplete(done(), total, "Download stopped: ${e.message ?: "network error"}"))
        }
    }
}

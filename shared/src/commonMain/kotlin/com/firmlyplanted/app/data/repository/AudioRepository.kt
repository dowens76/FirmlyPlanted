package com.firmlyplanted.app.data.repository

import com.firmlyplanted.app.data.audio.AudioFileStore
import com.firmlyplanted.app.data.audio.Mp3Clipper
import com.firmlyplanted.app.data.audio.SmilClip
import com.firmlyplanted.app.data.audio.SmilParser
import com.firmlyplanted.app.data.local.MemoryProjectDao
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.data.local.VerseDao
import com.firmlyplanted.app.data.local.VerseEntity
import com.firmlyplanted.app.data.remote.EbibleAudioService
import com.firmlyplanted.app.domain.AudioSpeed
import com.firmlyplanted.app.domain.BookCatalog
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.domain.EbibleAudioRefs
import com.firmlyplanted.app.domain.EbibleClipRef
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
 * Hebrew verse audio for WLC projects: Abraham Shmuelof's reading from eBible.org's audio ePubs.
 * For every verse in a project's scope, the matching span of the chapter recording is cut out
 * (see [Mp3Clipper]) and saved as its own small MP3, so sessions can play any verse instantly and
 * offline. Each chapter MP3 is downloaded once per run, cut, and discarded; clips stay until the
 * project is completed, archived or deleted ([removeProjectAudio]).
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

    fun supportsAudio(project: MemoryProjectEntity?): Boolean =
        project != null && project.translationId == DefaultTranslations.WLC_ID &&
            EbibleAudioRefs.bookFile(project.book) != null

    /** The verse's saved clip, or null when it isn't downloaded (or the project has no audio). */
    fun clipPath(project: MemoryProjectEntity, verse: VerseEntity): String? {
        if (!supportsAudio(project)) return null
        return store.clipPath(project.id, project.audioSpeed, verse.chapter, verse.verseNumber)
            .takeIf { store.hasClip(project.id, project.audioSpeed, verse.chapter, verse.verseNumber) }
    }

    /**
     * Starts (or resumes) downloading the project's missing clips in the background; returns at
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

    /** Switches the project's recording, deleting the other speed's clips, then re-downloads. */
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

    /** Stops any download and deletes every clip of the project. */
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
        if (!supportsAudio(project) || project.status != ProjectStatus.ACTIVE) return
        val speed = project.audioSpeed
        val bookCode = BookCatalog.byName(project.book)?.code ?: return

        val withAudio = verseDao.getForProject(projectId)
            .sortedBy { it.orderIndex }
            .map { it to EbibleAudioRefs.resolve(project.book, it.chapter, it.verseNumber) }
            .filter { (_, refs) -> refs.isNotEmpty() }
        val total = withAudio.size
        val missing = withAudio.filter { (v, _) -> !store.hasClip(projectId, speed, v.chapter, v.verseNumber) }
        var done = total - missing.size
        if (missing.isEmpty()) {
            publish(projectId, AudioDownloadState.Complete(total))
            return
        }
        if (!isOnline()) {
            publish(projectId, AudioDownloadState.Incomplete(done, total, "Waiting for a connection to download audio."))
            return
        }
        publish(projectId, AudioDownloadState.Downloading(done, total))

        // A verse can span two ePub chapters (e.g. 1 Sam 20:42 = Hebrew 20:42 + 21:1), so its
        // pieces are collected per chapter and written once all are in.
        val pieces = mutableMapOf<VerseEntity, Array<ByteArray?>>()
        val chapters = missing.flatMap { (_, refs) -> refs.map { it.chapter } }.distinct()
        var failures = 0
        try {
            for (chapter in chapters) {
                val audio = service.fetchChapter(project.book, bookCode, chapter, speed)
                val clips = SmilParser.parse(audio.smil)
                val clipper = Mp3Clipper(audio.mp3)
                for ((verse, refs) in missing) {
                    refs.forEachIndexed { index, ref ->
                        if (ref.chapter != chapter) return@forEachIndexed
                        val bytes = cut(clipper, clips, ref)
                        pieces.getOrPut(verse) { arrayOfNulls(refs.size) }[index] = bytes ?: ByteArray(0)
                        if (bytes == null) failures++
                    }
                    val collected = pieces[verse] ?: continue
                    if (collected.any { it == null }) continue
                    pieces.remove(verse)
                    if (collected.all { it!!.isNotEmpty() }) {
                        store.writeClip(projectId, speed, verse.chapter, verse.verseNumber, concat(collected))
                        done++
                        publish(projectId, AudioDownloadState.Downloading(done, total))
                    }
                }
            }
            publish(
                projectId,
                if (done == total) {
                    AudioDownloadState.Complete(total)
                } else {
                    AudioDownloadState.Incomplete(done, total, "$failures verse${if (failures == 1) "" else "s"} had no matching audio.")
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            publish(projectId, AudioDownloadState.Incomplete(done, total, "Download stopped: ${e.message ?: "network error"}"))
        }
    }

    private fun cut(clipper: Mp3Clipper, clips: List<SmilClip>, ref: EbibleClipRef): ByteArray? {
        val matching = clips.filter { ref.matches(it.id) }
        if (matching.isEmpty()) return null
        return clipper.clip(matching.minOf { it.begin }, matching.maxOf { it.end }).takeIf { it.isNotEmpty() }
    }

    private fun concat(parts: Array<ByteArray?>): ByteArray {
        if (parts.size == 1) return parts[0]!!
        val out = ByteArray(parts.sumOf { it!!.size })
        var p = 0
        for (part in parts) {
            part!!.copyInto(out, p)
            p += part.size
        }
        return out
    }
}

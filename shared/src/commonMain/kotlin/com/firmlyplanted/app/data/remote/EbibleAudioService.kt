package com.firmlyplanted.app.data.remote

import com.firmlyplanted.app.domain.AudioSpeed
import com.firmlyplanted.app.domain.EbibleAudioIndex
import com.firmlyplanted.app.domain.EbibleAudioRefs
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One chapter's files, byte for byte as stored in the ePub (after undoing the zip compression). */
class ChapterAudio(val smil: ByteArray, val mp3: ByteArray)

/**
 * eBible.org's audio ePubs of the Hebrew Bible read by Abraham Shmuelof
 * (https://ebible.org/multimedia/hbo/, CC BY-NC-ND 4.0). One ePub per book and speed; inside,
 * `Text/00-{CODE}-{CCC}.xhtml.smil` and `Audio/00-{CODE}-{CCC}.mp3` per chapter. Only the entries
 * needed are downloaded, via [RangedZipReader].
 */
class EbibleAudioService(private val zip: RangedZipReader) {
    private val directories = mutableMapOf<String, Map<String, RangedZipReader.Entry>>()
    private val lock = Mutex()

    suspend fun fetchChapter(book: String, bookCode: String, chapter: Int, speed: AudioSpeed): ChapterAudio {
        val file = EbibleAudioRefs.bookFile(book) ?: error("No Hebrew audio for $book")
        val url = EbibleAudioIndex.BASE_URL + file.fileName(speed).encodeURLPathPart()
        val directory = lock.withLock { directories[url] } ?: zip.directory(url).also { dir ->
            lock.withLock { directories[url] = dir }
        }
        val stem = "00-${bookCode.uppercase()}-${chapter.toString().padStart(3, '0')}"
        val smilEntry = directory["Text/$stem.xhtml.smil"] ?: error("$book $chapter has no timing data")
        val mp3Entry = directory["Audio/$stem.mp3"] ?: error("$book $chapter has no audio")
        val smil = zip.read(url, smilEntry)
        return ChapterAudio(smil, zip.read(url, mp3Entry))
    }
}

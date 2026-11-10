package com.firmlyplanted.app.data.remote

import com.firmlyplanted.app.domain.AudioSpeed
import com.firmlyplanted.app.domain.EbibleAudioEdition
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One chapter's files, byte for byte as stored in the ePub (after undoing the zip compression). */
class ChapterAudio(val smil: ByteArray, val mp3: ByteArray)

/**
 * eBible.org's audio ePubs (see [EbibleAudioEdition]): one ePub per book (and speed), with
 * `…Text/NN-{CODE}-{CCC}.xhtml.smil` and `…Audio/NN-{CODE}-{CCC}.mp3` per chapter; the Hebrew
 * ePubs keep them at the top level, the Greek ones under `OEBPS/`. Only the entries needed are
 * downloaded, via [RangedZipReader].
 */
class EbibleAudioService(private val zip: RangedZipReader) {
    private val directories = mutableMapOf<String, Map<String, RangedZipReader.Entry>>()
    private val lock = Mutex()

    suspend fun fetchChapter(
        edition: EbibleAudioEdition,
        book: String,
        bookCode: String,
        chapter: Int,
        speed: AudioSpeed,
    ): ChapterAudio {
        val file = edition.bookFile(book) ?: error("No ${edition.label.lowercase()} for $book")
        val url = edition.baseUrl + edition.fileName(file, speed).encodeURLPathPart()
        val directory = lock.withLock { directories[url] } ?: zip.directory(url).also { dir ->
            lock.withLock { directories[url] = dir }
        }
        val stem = "-${bookCode.uppercase()}-${chapter.toString().padStart(3, '0')}"
        val smilEntry = directory.entry(Regex("""(^|/)Text/\d+${Regex.escape(stem)}\.xhtml\.smil$"""))
            ?: error("$book $chapter has no timing data")
        val mp3Entry = directory.entry(Regex("""(^|/)Audio/\d+${Regex.escape(stem)}\.mp3$"""))
            ?: error("$book $chapter has no audio")
        val smil = zip.read(url, smilEntry)
        return ChapterAudio(smil, zip.read(url, mp3Entry))
    }

    private fun Map<String, RangedZipReader.Entry>.entry(name: Regex): RangedZipReader.Entry? =
        entries.firstOrNull { name.containsMatchIn(it.key) }?.value
}

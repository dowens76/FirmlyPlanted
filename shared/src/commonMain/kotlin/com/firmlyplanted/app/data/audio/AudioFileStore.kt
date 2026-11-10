package com.firmlyplanted.app.data.audio

import com.firmlyplanted.app.domain.AudioSpeed

/** Minimal file access for the audio store, backed by java.io.File / NSFileManager. */
internal expect object PlatformFiles {
    fun exists(path: String): Boolean
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray)
    fun deleteRecursively(path: String)
}

/**
 * eBible.org chapter files on disk, under the app's private storage, exactly as they come out of
 * the audio ePub (only the zip's own compression is undone):
 * `{root}/{projectId}/{speed}/{ebibleChapter}.mp3` (the chapter recording) and `.smil` (its EPUB3
 * media overlay, with each verse's start and end time). Chapters use the ePub's (Hebrew)
 * numbering. A chapter counts as downloaded once both files exist; the SMIL is written last.
 */
class AudioFileStore(private val root: String) {

    fun mp3Path(projectId: String, speed: AudioSpeed, ebibleChapter: Int): String =
        "${speedDir(projectId, speed)}/$ebibleChapter.mp3"

    fun smilPath(projectId: String, speed: AudioSpeed, ebibleChapter: Int): String =
        "${speedDir(projectId, speed)}/$ebibleChapter.smil"

    fun hasChapter(projectId: String, speed: AudioSpeed, ebibleChapter: Int): Boolean =
        PlatformFiles.exists(smilPath(projectId, speed, ebibleChapter)) &&
            PlatformFiles.exists(mp3Path(projectId, speed, ebibleChapter))

    /** Each file is written via a temporary file and rename, so a partial file never looks downloaded. */
    fun writeChapter(projectId: String, speed: AudioSpeed, ebibleChapter: Int, mp3: ByteArray, smil: ByteArray) {
        PlatformFiles.writeBytes(mp3Path(projectId, speed, ebibleChapter), mp3)
        PlatformFiles.writeBytes(smilPath(projectId, speed, ebibleChapter), smil)
    }

    fun readSmil(projectId: String, speed: AudioSpeed, ebibleChapter: Int): String? =
        PlatformFiles.readBytes(smilPath(projectId, speed, ebibleChapter))?.decodeToString()

    fun deleteProject(projectId: String) = PlatformFiles.deleteRecursively("$root/$projectId")

    fun deleteSpeed(projectId: String, speed: AudioSpeed) = PlatformFiles.deleteRecursively(speedDir(projectId, speed))

    private fun speedDir(projectId: String, speed: AudioSpeed) = "$root/$projectId/${speed.name.lowercase()}"
}

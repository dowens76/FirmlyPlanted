package com.firmlyplanted.app.data.audio

import com.firmlyplanted.app.domain.AudioSpeed

/** Minimal file access for the audio store, backed by java.io.File / NSFileManager. */
internal expect object PlatformFiles {
    fun exists(path: String): Boolean
    fun writeBytes(path: String, bytes: ByteArray)
    fun deleteRecursively(path: String)
}

/**
 * Per-verse audio clips on disk, under the app's private storage:
 * `{root}/{projectId}/{speed}/{chapter}-{verse}.mp3` (app chapter/verse numbering). Whether a
 * clip's file exists is the only record of what's downloaded.
 */
class AudioFileStore(private val root: String) {

    fun clipPath(projectId: String, speed: AudioSpeed, chapter: Int, verse: Int): String =
        "${speedDir(projectId, speed)}/$chapter-$verse.mp3"

    fun hasClip(projectId: String, speed: AudioSpeed, chapter: Int, verse: Int): Boolean =
        PlatformFiles.exists(clipPath(projectId, speed, chapter, verse))

    /** Writes via a temporary file and rename, so an interrupted write never looks downloaded. */
    fun writeClip(projectId: String, speed: AudioSpeed, chapter: Int, verse: Int, bytes: ByteArray) {
        PlatformFiles.writeBytes(clipPath(projectId, speed, chapter, verse), bytes)
    }

    fun deleteProject(projectId: String) = PlatformFiles.deleteRecursively("$root/$projectId")

    fun deleteSpeed(projectId: String, speed: AudioSpeed) = PlatformFiles.deleteRecursively(speedDir(projectId, speed))

    private fun speedDir(projectId: String, speed: AudioSpeed) = "$root/$projectId/${speed.name.lowercase()}"
}

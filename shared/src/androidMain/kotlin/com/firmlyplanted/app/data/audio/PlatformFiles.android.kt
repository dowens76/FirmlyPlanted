package com.firmlyplanted.app.data.audio

import java.io.File

internal actual object PlatformFiles {
    actual fun exists(path: String): Boolean = File(path).isFile

    /** Writes to a temporary sibling, then renames, so a half-written file never exists at [path]. */
    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = File(path)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".part")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            temp.delete()
            error("Couldn't save $path")
        }
    }

    actual fun deleteRecursively(path: String) {
        File(path).deleteRecursively()
    }
}

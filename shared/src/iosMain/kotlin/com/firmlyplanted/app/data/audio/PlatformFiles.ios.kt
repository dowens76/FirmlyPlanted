package com.firmlyplanted.app.data.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual object PlatformFiles {
    private val fileManager get() = NSFileManager.defaultManager

    actual fun exists(path: String): Boolean = fileManager.fileExistsAtPath(path)

    /** `atomically = true` writes a temporary file and renames it, so a partial file never exists at [path]. */
    actual fun writeBytes(path: String, bytes: ByteArray) {
        val dir = path.substringBeforeLast('/')
        fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }
        check(data.writeToFile(path, atomically = true)) { "Couldn't save $path" }
    }

    actual fun deleteRecursively(path: String) {
        if (fileManager.fileExistsAtPath(path)) fileManager.removeItemAtPath(path, error = null)
    }
}

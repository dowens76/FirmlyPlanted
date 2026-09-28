package com.firmlyplanted.app.data.local

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** Stored under Application Support: private to the app and backed up, but not user-visible. */
@OptIn(ExperimentalForeignApi::class)
fun createAppDatabase(): AppDatabase {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(directory?.path) { "Application Support directory unavailable" } +
        "/" + AppDatabase.FILE_NAME
    return Room.databaseBuilder<AppDatabase>(name = path).buildWithDefaults()
}

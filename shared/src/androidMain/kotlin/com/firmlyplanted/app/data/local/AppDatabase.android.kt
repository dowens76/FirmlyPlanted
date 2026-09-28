package com.firmlyplanted.app.data.local

import android.content.Context
import androidx.room.Room

/** Same file as the pre-multiplatform build, so existing installs keep their data. */
fun createAppDatabase(context: Context): AppDatabase {
    val appContext = context.applicationContext
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(AppDatabase.FILE_NAME).absolutePath,
    ).buildWithDefaults()
}

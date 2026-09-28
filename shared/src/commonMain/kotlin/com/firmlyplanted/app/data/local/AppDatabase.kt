package com.firmlyplanted.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [TranslationEntity::class, MemoryProjectEntity::class, VerseEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun memoryProjectDao(): MemoryProjectDao
    abstract fun verseDao(): VerseDao

    companion object {
        const val FILE_NAME = "firmly_planted.db"
    }
}

/** Room's KSP processor generates the `actual` for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Shared build settings; each platform's `createAppDatabase` supplies the file location. */
internal fun RoomDatabase.Builder<AppDatabase>.buildWithDefaults(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

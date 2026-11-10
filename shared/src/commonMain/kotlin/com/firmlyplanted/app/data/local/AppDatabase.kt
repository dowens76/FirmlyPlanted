package com.firmlyplanted.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [TranslationEntity::class, MemoryProjectEntity::class, VerseEntity::class],
    version = 2,
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

/** v2: per-project Hebrew audio speed (see AudioRepository). Existing projects get FLUENT. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE memory_projects ADD COLUMN audioSpeed TEXT NOT NULL DEFAULT 'FLUENT'")
    }
}

/** Shared build settings; each platform's `createAppDatabase` supplies the file location. */
internal fun RoomDatabase.Builder<AppDatabase>.buildWithDefaults(): AppDatabase =
    setDriver(BundledSQLiteDriver())
        .addMigrations(MIGRATION_1_2)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

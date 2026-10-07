package com.firmlyplanted.app.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.firmlyplanted.app.data.backup.BackupCodec
import com.firmlyplanted.app.data.backup.BackupContents
import com.firmlyplanted.app.data.local.AppDatabase
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Exports every project (with its progress) to a backup file's text, and restores from one. */
class BackupRepository(
    private val database: AppDatabase,
    private val appVersion: String,
) {
    private val projectDao = database.memoryProjectDao()
    private val verseDao = database.verseDao()
    private val translationDao = database.translationDao()

    suspend fun projectCount(): Int = projectDao.getAll().size

    suspend fun exportBackup(): String {
        val projects = projectDao.getAll()
        val translationIds = projects.map { it.translationId }.toSet()
        val contents = BackupContents(
            translations = translationIds.mapNotNull { translationDao.getById(it) },
            projects = projects,
            verses = projects.flatMap { verseDao.getForProject(it.id) },
        )
        return BackupCodec.encode(contents, appVersion, now())
    }

    /**
     * Restores [backupText] in one transaction: each project in the backup replaces any project
     * with the same id (so restoring returns it to the backed-up state); projects that aren't in
     * the backup are left alone. Returns how many projects were restored. Throws
     * [com.firmlyplanted.app.data.backup.InvalidBackupException] if the file can't be used.
     */
    suspend fun restoreBackup(backupText: String): Int {
        val contents = BackupCodec.decode(backupText)
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // Existing translation rows are kept as-is: other projects may share them, and
                // the defaults are re-seeded from the app itself.
                val missing = contents.translations.filter { translationDao.getById(it.id) == null }
                if (missing.isNotEmpty()) translationDao.upsertAll(missing)

                for (project in contents.projects) {
                    projectDao.deleteById(project.id) // Cascades to its verses.
                    projectDao.insert(project)
                }
                verseDao.insertAll(contents.verses)
            }
        }
        return contents.projects.size
    }

    private fun now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}

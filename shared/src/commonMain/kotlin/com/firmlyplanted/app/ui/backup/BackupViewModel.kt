package com.firmlyplanted.app.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firmlyplanted.app.data.backup.BackupCodec
import com.firmlyplanted.app.data.backup.InvalidBackupException
import com.firmlyplanted.app.data.repository.BackupRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** A backup file the user picked, waiting for them to confirm the restore. */
data class PendingRestore(val text: String, val projectNames: List<String>)

class BackupViewModel(private val backupRepository: BackupRepository) : ViewModel() {

    var projectCount by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** The outcome of the last backup or restore, shown under the buttons. */
    var status by mutableStateOf<String?>(null)
        private set
    var pendingRestore by mutableStateOf<PendingRestore?>(null)
        private set

    init {
        refreshCount()
    }

    private fun refreshCount() {
        viewModelScope.launch { projectCount = backupRepository.projectCount() }
    }

    fun backupFileName(): String =
        "firmly-planted-backup-${Clock.System.todayIn(TimeZone.currentSystemDefault())}.json"

    suspend fun produceBackup(): String = backupRepository.exportBackup()

    fun onBackupStarted() {
        busy = true
        status = null
    }

    fun onBackupResult(result: Result<Boolean>) {
        busy = false
        status = result.fold(
            onSuccess = { saved -> if (saved) "Backup saved." else null },
            onFailure = { "Backup failed: ${it.message ?: "unknown error"}" },
        )
    }

    /** Validates the picked file and, if usable, asks the user to confirm before anything changes. */
    fun onBackupFilePicked(result: Result<String?>) {
        val text = result.getOrElse {
            status = "Couldn't open that file: ${it.message ?: "unknown error"}"
            return
        } ?: return
        status = null
        try {
            val contents = BackupCodec.decode(text)
            pendingRestore = PendingRestore(text, contents.projects.map { it.name })
        } catch (e: InvalidBackupException) {
            status = e.message
        }
    }

    fun cancelRestore() {
        pendingRestore = null
    }

    fun confirmRestore() {
        val pending = pendingRestore ?: return
        pendingRestore = null
        busy = true
        viewModelScope.launch {
            status = try {
                val count = backupRepository.restoreBackup(pending.text)
                if (count == 1) "Restored 1 project." else "Restored $count projects."
            } catch (e: InvalidBackupException) {
                e.message
            } catch (e: Exception) {
                "Restore failed: ${e.message ?: "unknown error"}. Nothing was changed."
            }
            busy = false
            refreshCount()
        }
    }
}

package com.firmlyplanted.app.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: BackupViewModel = viewModel(factory = simpleFactory { BackupViewModel(container.backupRepository) })

    val saveBackup = rememberBackupSaver(
        produce = { viewModel.produceBackup() },
        onResult = viewModel::onBackupResult,
    )
    val openBackup = rememberBackupOpener(onResult = viewModel::onBackupFilePicked)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Back up", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Saves all your projects and review progress to one file. You choose where it " +
                            "goes — this device, Google Drive, iCloud Drive, or another storage app.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Text you pasted in is included. Text from online sources isn't; it's downloaded again after a restore.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            viewModel.onBackupStarted()
                            saveBackup(viewModel.backupFileName())
                        },
                        enabled = !viewModel.busy && (viewModel.projectCount ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when (val count = viewModel.projectCount) {
                                null, 0 -> "Back up"
                                1 -> "Back up 1 project"
                                else -> "Back up $count projects"
                            },
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Brings back the projects in a backup file. A project that's already on this " +
                            "device is replaced by its backed-up version; other projects are kept.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = openBackup, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore from backup…")
                    }
                }
            }

            if (viewModel.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            viewModel.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    viewModel.pendingRestore?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    buildString {
                        append(
                            if (pending.projectNames.size == 1) "It contains 1 project:\n" else "It contains ${pending.projectNames.size} projects:\n",
                        )
                        pending.projectNames.forEach { append("• ").append(it).append('\n') }
                        append("\nAny of these already on this device will be replaced with the backed-up version.")
                    },
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}

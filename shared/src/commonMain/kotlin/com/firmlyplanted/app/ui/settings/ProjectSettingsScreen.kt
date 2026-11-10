package com.firmlyplanted.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.data.repository.AudioDownloadState
import com.firmlyplanted.app.domain.AudioSpeed
import com.firmlyplanted.app.domain.EbibleAudioEdition
import com.firmlyplanted.app.domain.VersePhase
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun ProjectSettingsScreen(projectId: String, onBack: () -> Unit, onProjectRemoved: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ProjectSettingsViewModel = viewModel(
        factory = simpleFactory { ProjectSettingsViewModel(projectId, container.projectRepository, container.audioRepository) },
    )
    val project by viewModel.project.collectAsStateWithLifecycle()
    val verses by viewModel.verses.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()

    var newPerDay by remember(project) { mutableStateOf(project?.newVersesPerDay ?: 2) }
    var reviewPerDay by remember(project) { mutableStateOf(project?.reviewVersesPerDay ?: 20) }
    var showCompleteDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val hasAudio = viewModel.supportsAudio(project)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Project Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            val mastered = verses.count { it.phase == VersePhase.MASTERED }
            Text("$mastered of ${verses.size} verses mastered", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(24.dp))
            Text("New verses per day: $newPerDay")
            Slider(value = newPerDay.toFloat(), onValueChange = { newPerDay = it.toInt() }, valueRange = 1f..10f, steps = 8)

            Spacer(Modifier.height(16.dp))
            Text("Verses reviewed per day: $reviewPerDay")
            Slider(value = reviewPerDay.toFloat(), onValueChange = { reviewPerDay = it.toInt() }, valueRange = 5f..100f, steps = 18)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.updatePace(newPerDay, reviewPerDay) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save pace") }

            if (!viewModel.isPasted) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { viewModel.clearCacheNow() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear cached text now")
                }
            }

            val currentProject = project
            val edition = viewModel.audioEdition(currentProject)
            if (currentProject != null && edition != null) {
                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                AudioSection(
                    edition = edition,
                    speed = currentProject.audioSpeed,
                    state = audioState,
                    onSpeedChange = viewModel::setAudioSpeed,
                    onRetry = viewModel::retryAudio,
                )
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = { showCompleteDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        viewModel.isPasted -> "Mark Complete"
                        hasAudio -> "Mark Complete (clears cached text and audio)"
                        else -> "Mark Complete (clears cached text)"
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.archive(onProjectRemoved) }, modifier = Modifier.fillMaxWidth()) {
                Text("Archive")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete project") }
        }
    }

    if (showCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteDialog = false },
            title = { Text("Mark this project complete?") },
            text = {
                Text(
                    when {
                        viewModel.isPasted ->
                            "Your pasted text and progress history stay on this device until you delete the project."
                        hasAudio ->
                            "This clears the small amount of Scripture text cached on this device for this project " +
                                "and deletes its downloaded audio. Your progress history is kept."
                        else ->
                            "This clears the small amount of Scripture text currently cached on this device for this project. Your progress history is kept."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showCompleteDialog = false; viewModel.markComplete(onProjectRemoved) }) { Text("Complete") }
            },
            dismissButton = { TextButton(onClick = { showCompleteDialog = false }) { Text("Cancel") } },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this project?") },
            text = { Text("This permanently removes the project and all of its progress. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete(onProjectRemoved) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AudioSection(
    edition: EbibleAudioEdition,
    speed: AudioSpeed,
    state: AudioDownloadState?,
    onSpeedChange: (AudioSpeed) -> Unit,
    onRetry: () -> Unit,
) {
    Text(edition.label, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    if (edition.speeds.size > 1) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = edition.speeds.map {
                it to when (it) {
                    AudioSpeed.FLUENT -> "Normal"
                    AudioSpeed.SLOWER -> "Slower (−20%)"
                }
            }
            options.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = speed == option,
                    onClick = { if (speed != option) onSpeedChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    when (state) {
        null -> Text("Checking audio…", style = MaterialTheme.typography.bodyMedium)
        is AudioDownloadState.Downloading -> {
            Text("Downloading audio: ${state.done} of ${state.total} verses", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is AudioDownloadState.Complete ->
            Text("Audio downloaded for all ${state.total} verses.", style = MaterialTheme.typography.bodyMedium)
        is AudioDownloadState.Incomplete -> {
            Text(
                "Audio downloaded for ${state.done} of ${state.total} verses. ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(edition.credit, style = MaterialTheme.typography.bodySmall)
}

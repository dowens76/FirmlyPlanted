package com.firmlyplanted.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun SessionScreen(projectId: String, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SessionViewModel = viewModel(
        factory = simpleFactory { SessionViewModel(projectId, container.projectRepository) },
    )
    val verses by viewModel.verses.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Today's Session") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                viewModel.loading -> CircularProgressIndicator()
                viewModel.finished -> SessionComplete(onDone)
                else -> {
                    val verse = verses.find { it.id == viewModel.currentVerseId() }
                    if (verse == null) {
                        CircularProgressIndicator()
                    } else {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            LinearProgressIndicator(
                                progress = { (viewModel.currentIndex + 1).toFloat() / viewModel.queue.size.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "${verse.book} ${verse.chapter}:${verse.verseNumber}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (viewModel.isCurrentNew()) {
                                NewVerseCard(text = verse.text, onLearned = { viewModel.confirmLearned() })
                            } else {
                                ReviewVerseCard(
                                    text = verse.text,
                                    revealed = viewModel.revealed,
                                    onReveal = { viewModel.reveal() },
                                    onResult = { ok -> viewModel.submitReview(ok) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewVerseCard(text: String?, onLearned: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("New verse — read it over a few times.", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            Text(text ?: "Not cached yet — connect to the internet and reopen Today.", style = MaterialTheme.typography.bodyLarge)
        }
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onLearned, enabled = text != null, modifier = Modifier.fillMaxWidth()) {
        Text("I've got it — start reviewing")
    }
}

@Composable
private fun ReviewVerseCard(text: String?, revealed: Boolean, onReveal: () -> Unit, onResult: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Try to recall this verse, then reveal it.", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            if (revealed) {
                Text(text ?: "Not cached yet — connect to the internet and reopen Today.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    if (!revealed) {
        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Reveal") }
    } else {
        Text("Did you recall it correctly?", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onResult(false) }, modifier = Modifier.weight(1f)) { Text("Not quite") }
            Button(onClick = { onResult(true) }, modifier = Modifier.weight(1f)) { Text("Got it") }
        }
    }
}

@Composable
private fun SessionComplete(onDone: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Session complete!", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Come back tomorrow for more.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onDone) { Text("Done") }
    }
}

package com.firmlyplanted.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory
import com.firmlyplanted.app.ui.theme.fontFamilyForLanguage
import com.firmlyplanted.app.ui.theme.isRtlLanguage
import com.firmlyplanted.app.ui.theme.scriptureTextStyle

/**
 * The last step of every session: the whole passage learned so far, laid out in order so it can
 * be recited start to finish as a block rather than one verse at a time, before the session is
 * marked complete.
 */
@Composable
fun GroupReviewScreen(projectId: String, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: GroupReviewViewModel = viewModel(
        factory = simpleFactory {
            GroupReviewViewModel(projectId, container.projectRepository, container.translationRepository, container.isOnline)
        },
    )
    val verses = viewModel.verses
    val fontFamily = fontFamilyForLanguage(viewModel.translation?.language)
    val isRtl = isRtlLanguage(viewModel.translation?.language)

    var revealedIds by remember { mutableStateOf(emptySet<String>()) }
    var hints by remember { mutableStateOf(emptyMap<String, RecallHint>()) }
    var showAll by remember { mutableStateOf(false) }
    var reviewDone by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Review the Passage") }) }) { padding ->
        when {
            verses == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            reviewDone || verses.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                SessionComplete(onDone = onDone)
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(
                        if (verses.size == 1) {
                            "Recite the verse you've learned so far. Tap it to check yourself."
                        } else {
                            "Recite all ${verses.size} verses you've learned so far, in order. Tap a verse to check yourself."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) "Hide all" else "Show all")
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(verses, key = { it.id }) { verse ->
                        val revealed = showAll || verse.id in revealedIds
                        Card(
                            onClick = {
                                revealedIds =
                                    if (verse.id in revealedIds) revealedIds - verse.id else revealedIds + verse.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "${verse.book} ${verse.chapter}:${verse.verseNumber}",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val hint = hints[verse.id] ?: RecallHint.NONE
                                val hintText = verse.text?.let { hint.textFor(it) }
                                if (revealed) {
                                    Text(
                                        verse.text ?: "Not cached — connect to the internet to see this verse.",
                                        style = scriptureTextStyle(rtl = isRtl && verse.text != null),
                                        fontFamily = fontFamily,
                                    )
                                } else {
                                    if (hintText != null) {
                                        Text(
                                            hintText,
                                            style = scriptureTextStyle(rtl = isRtl),
                                            fontFamily = fontFamily,
                                        )
                                    }
                                    RecallHintButtons(
                                        hint = hint,
                                        enabled = verse.text != null,
                                        onHintChange = { hints = hints + (verse.id to it) },
                                    )
                                }
                            }
                        }
                    }
                }
                Button(onClick = { reviewDone = true }, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Finish session")
                }
            }
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

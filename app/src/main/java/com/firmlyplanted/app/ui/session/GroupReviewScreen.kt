package com.firmlyplanted.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory
import com.firmlyplanted.app.ui.theme.fontFamilyForLanguage
import com.firmlyplanted.app.ui.theme.scriptureTextStyle

/**
 * Shown once the individual review/learning cards for the session are done: today's verses laid
 * out together (up to [GROUP_REVIEW_MAX_VERSES], in canonical order) so they can be read or
 * recited as a block rather than one at a time.
 */
@Composable
fun GroupReviewScreen(projectId: String, verseIds: List<String>, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: GroupReviewViewModel = viewModel(
        factory = simpleFactory {
            GroupReviewViewModel(projectId, verseIds, container.projectRepository, container.translationRepository)
        },
    )
    val verses by viewModel.verses.collectAsStateWithLifecycle()
    val fontFamily = fontFamilyForLanguage(viewModel.translation?.language)

    var revealedIds by remember { mutableStateOf(emptySet<String>()) }
    var showAll by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Review Together") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(
                    "Read through these ${verses.size} verses in order, like reciting the passage.",
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
                            if (revealed) {
                                Text(
                                    verse.text ?: "Not cached yet — connect to the internet and reopen Today.",
                                    style = scriptureTextStyle(),
                                    fontFamily = fontFamily,
                                )
                            }
                        }
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(24.dp)) { Text("Done") }
        }
    }
}

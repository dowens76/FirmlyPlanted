package com.firmlyplanted.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.CopyrightNotice
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun TodayScreen(
    projectId: String,
    onStartSession: () -> Unit,
    onSettings: () -> Unit,
    onReadMore: () -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current.applicationContext
    val viewModel: TodayViewModel = viewModel(
        factory = simpleFactory {
            TodayViewModel(projectId, container.projectRepository, container.translationRepository, context)
        },
    )

    val project by viewModel.project.collectAsStateWithLifecycle()
    val verses by viewModel.verses.collectAsStateWithLifecycle()
    val plan by viewModel.todayPlan.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Today") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Project settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.offline) {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp)) {
                    Text(
                        "Offline — showing last-saved text. Connect to the internet to refresh.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "${plan.dueReviewIds.size} to review, ${plan.newVerseIds.size} new today",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(plan.dueReviewIds, key = { "review-$it" }) { id ->
                    val verse = verses.find { it.id == id }
                    if (verse != null) VerseRow(label = "Review", ref = "${verse.book} ${verse.chapter}:${verse.verseNumber}")
                }
                items(plan.newVerseIds, key = { "new-$it" }) { id ->
                    val verse = verses.find { it.id == id }
                    if (verse != null) VerseRow(label = "New", ref = "${verse.book} ${verse.chapter}:${verse.verseNumber}")
                }
            }

            viewModel.translation?.let { CopyrightNotice(it, modifier = Modifier.fillMaxWidth()) }

            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = onStartSession,
                    enabled = plan.dueReviewIds.isNotEmpty() || plan.newVerseIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Today's Session")
                }
                TextButton(onClick = onReadMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Read more online")
                }
            }
        }
    }
}

@Composable
private fun VerseRow(label: String, ref: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(ref, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

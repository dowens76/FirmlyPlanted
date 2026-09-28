package com.firmlyplanted.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.data.local.MemoryProjectEntity
import com.firmlyplanted.app.domain.ProjectStatus
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun HomeScreen(
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onAbout: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = simpleFactory { HomeViewModel(container.projectRepository) })
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firmly Planted") },
                actions = {
                    IconButton(onClick = onAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About & Licenses")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProject,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Project") },
            )
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No memory projects yet. Tap \"New Project\" to start memorizing a passage.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(project, viewModel, onClick = { onOpenProject(project.id) })
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(project: MemoryProjectEntity, viewModel: HomeViewModel, onClick: () -> Unit) {
    var progress by remember(project.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(project.id) { progress = viewModel.progressFor(project.id) }

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${project.book} ${project.startChapter}:${project.startVerse} – ${project.endChapter}:${project.endVerse}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            progress?.let { (mastered, total) ->
                LinearProgressIndicator(
                    progress = { if (total > 0) mastered.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text("$mastered / $total verses mastered", style = MaterialTheme.typography.labelSmall)
            }
            if (project.status != ProjectStatus.ACTIVE) {
                Spacer(Modifier.height(8.dp))
                AssistChip(onClick = {}, label = { Text(project.status.name) })
            }
        }
    }
}

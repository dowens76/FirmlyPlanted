package com.firmlyplanted.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.firmlyplanted.app.domain.DefaultTranslations
import com.firmlyplanted.app.ui.resolveCopyrightNotice

@Composable
fun AboutLicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About & Licenses") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Firmly Planted fetches Scripture text from the ESV API and fetch.bible. " +
                        "Only a small rolling window of verses is ever cached on this device — " +
                        "see each text's notice below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(DefaultTranslations.all, key = { it.id }) { translation ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(translation.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(resolveCopyrightNotice(translation), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("More texts (via fetch.bible)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Additional translations chosen from \"More\" when creating a project " +
                                "carry whatever license fetch.bible's own catalog reports for them; " +
                                "that notice is shown before you create a project with one.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Firmly Planted app", style = MaterialTheme.typography.titleSmall)
                        Text("Licensed under the MIT License. Source and full text of all licenses are in this app's repository.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

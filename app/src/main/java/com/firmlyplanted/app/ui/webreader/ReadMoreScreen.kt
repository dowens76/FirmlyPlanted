package com.firmlyplanted.app.ui.webreader

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firmlyplanted.app.ui.CopyrightNotice
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.simpleFactory

@Composable
fun ReadMoreScreen(projectId: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ReadMoreViewModel = viewModel(
        factory = simpleFactory { ReadMoreViewModel(projectId, container.projectRepository, container.translationRepository) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Read More") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                viewModel.url == null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "This text doesn't have an online reader we could confirm — see About & Licenses for how to use it within its terms.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> WebViewContainer(url = viewModel.url!!, modifier = Modifier.weight(1f))
            }
            viewModel.translation?.let { CopyrightNotice(it) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContainer(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                loadUrl(url)
            }
        },
        update = { it.loadUrl(url) },
    )
}

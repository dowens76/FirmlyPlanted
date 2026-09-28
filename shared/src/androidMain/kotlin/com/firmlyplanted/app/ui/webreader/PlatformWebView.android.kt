package com.firmlyplanted.app.ui.webreader

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
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

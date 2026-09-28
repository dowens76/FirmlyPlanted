package com.firmlyplanted.app.ui.webreader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** An embedded browser showing [url] — Android's WebView, or WKWebView on iOS. */
@Composable
expect fun PlatformWebView(url: String, modifier: Modifier = Modifier)

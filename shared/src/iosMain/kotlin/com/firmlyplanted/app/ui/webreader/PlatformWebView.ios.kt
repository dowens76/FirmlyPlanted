package com.firmlyplanted.app.ui.webreader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView

/** [url] is fixed for the lifetime of the screen, so there's no `update` — that avoids reloading after redirects. */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    UIKitView(
        modifier = modifier.fillMaxSize(),
        factory = {
            WKWebView().apply {
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
    )
}

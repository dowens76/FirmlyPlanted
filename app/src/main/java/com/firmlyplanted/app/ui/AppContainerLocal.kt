package com.firmlyplanted.app.ui

import androidx.compose.runtime.compositionLocalOf
import com.firmlyplanted.app.AppContainer

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap the app in CompositionLocalProvider(LocalAppContainer provides container)")
}

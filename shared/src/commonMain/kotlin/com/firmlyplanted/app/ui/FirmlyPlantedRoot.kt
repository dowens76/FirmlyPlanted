package com.firmlyplanted.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.firmlyplanted.app.AppContainer
import com.firmlyplanted.app.ui.navigation.FirmlyPlantedNavHost
import com.firmlyplanted.app.ui.theme.FirmlyPlantedTheme

/** The whole app UI — hosted by MainActivity on Android and MainViewController on iOS. */
@Composable
fun FirmlyPlantedRoot(container: AppContainer) {
    FirmlyPlantedTheme {
        CompositionLocalProvider(LocalAppContainer provides container) {
            FirmlyPlantedNavHost()
        }
    }
}

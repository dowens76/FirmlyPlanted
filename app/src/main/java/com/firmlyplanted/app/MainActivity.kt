package com.firmlyplanted.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.firmlyplanted.app.ui.LocalAppContainer
import com.firmlyplanted.app.ui.navigation.FirmlyPlantedNavHost
import com.firmlyplanted.app.ui.theme.FirmlyPlantedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FirmlyPlantedApp).container

        setContent {
            FirmlyPlantedTheme {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    FirmlyPlantedNavHost()
                }
            }
        }
    }
}

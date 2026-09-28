package com.firmlyplanted.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.firmlyplanted.app.ui.FirmlyPlantedRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FirmlyPlantedApp).container

        setContent {
            FirmlyPlantedRoot(container)
        }
    }
}

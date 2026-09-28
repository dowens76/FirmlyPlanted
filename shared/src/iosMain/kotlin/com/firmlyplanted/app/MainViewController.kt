package com.firmlyplanted.app

import androidx.compose.ui.window.ComposeUIViewController
import com.firmlyplanted.app.data.local.createAppDatabase
import com.firmlyplanted.app.data.remote.NetworkMonitor
import com.firmlyplanted.app.ui.FirmlyPlantedRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** iOS counterpart of the Android `FirmlyPlantedApp` Application: one container per process. */
private val container: AppContainer by lazy {
    NetworkMonitor.start()
    AppContainer(createAppDatabase(), NetworkMonitor::isOnline).also { container ->
        CoroutineScope(Dispatchers.IO).launch {
            container.translationRepository.ensureDefaultsSeeded()
        }
    }
}

/** Entry point for the Xcode app: host this view controller as the root of the window. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    FirmlyPlantedRoot(container)
}

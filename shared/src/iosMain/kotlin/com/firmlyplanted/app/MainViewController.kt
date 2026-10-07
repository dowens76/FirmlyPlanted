package com.firmlyplanted.app

import androidx.compose.ui.window.ComposeUIViewController
import com.firmlyplanted.app.data.local.createAppDatabase
import com.firmlyplanted.app.data.remote.NetworkMonitor
import com.firmlyplanted.app.ui.FirmlyPlantedRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

/** iOS counterpart of the Android `FirmlyPlantedApp` Application: one container per process. */
private val container: AppContainer by lazy {
    NetworkMonitor.start()
    AppContainer(createAppDatabase(), NetworkMonitor::isOnline, bundleVersion()).also { container ->
        CoroutineScope(Dispatchers.IO).launch {
            container.translationRepository.ensureDefaultsSeeded()
        }
    }
}

/** The app's version and build number from Info.plist, e.g. "0.1.1 (6)". */
private fun bundleVersion(): String {
    val info = NSBundle.mainBundle
    val version = info.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?"
    val build = info.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "?"
    return "$version ($build)"
}

/** Entry point for the Xcode app: host this view controller as the root of the window. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController = ComposeUIViewController {
    FirmlyPlantedRoot(container)
}

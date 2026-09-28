package com.firmlyplanted.app

import android.app.Application
import com.firmlyplanted.app.data.local.createAppDatabase
import com.firmlyplanted.app.data.remote.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FirmlyPlantedApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(
            database = createAppDatabase(this),
            isOnline = { Connectivity.isOnline(this) },
        )
        CoroutineScope(Dispatchers.IO).launch {
            container.translationRepository.ensureDefaultsSeeded()
        }
    }
}

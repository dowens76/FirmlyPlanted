package com.firmlyplanted.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Android-only; passed to the shared AppContainer as its `isOnline` check (iOS uses NetworkMonitor). */
object Connectivity {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

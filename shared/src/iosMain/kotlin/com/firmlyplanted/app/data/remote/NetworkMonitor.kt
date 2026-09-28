package com.firmlyplanted.app.data.remote

import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

/** iOS counterpart of the Android app's Connectivity check, backed by NWPathMonitor. */
object NetworkMonitor {
    /** Optimistic until the monitor's first update arrives (moments after [start]). */
    @Volatile
    private var satisfied = true

    private val monitor by lazy {
        nw_path_monitor_create().also { monitor ->
            nw_path_monitor_set_update_handler(monitor) { path ->
                satisfied = nw_path_get_status(path) == nw_path_status_satisfied
            }
            nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.firmlyplanted.network-monitor", null))
        }
    }

    fun start() = nw_path_monitor_start(monitor)

    fun isOnline(): Boolean = satisfied
}

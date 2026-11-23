package com.example.sortmate.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.sortmate.util.evaluationMark
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach

/**
 * Emits the current connectivity and every change, so the app can stop
 * realtime listeners while offline and resume them the moment a network is
 * back instead of waiting for the next 15-minute WorkManager run.
 */
class NetworkMonitor(context: Context) {

    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                trySend(hasInternet())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        manager.registerDefaultNetworkCallback(callback)
        trySend(hasInternet())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().onEach { evaluationMark("network online=$it") }.conflate()

    private fun hasInternet() = manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}

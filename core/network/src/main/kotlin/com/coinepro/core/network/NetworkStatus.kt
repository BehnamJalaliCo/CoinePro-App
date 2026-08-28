package com.coinepro.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether this phone has a network the app could reach a server over.
 *
 * ### Why the app needs to know this at all
 *
 * Every failure in this app currently arrives as the same thing — a request that did not come
 * back — and every screen says the same sentence about it: something went wrong, try again. That
 * sentence is correct and useless when the reader is in a lift. "Try again" is advice they cannot
 * take, and repeating it is how an app that is working perfectly gets blamed for a tunnel.
 *
 * With this, a failure that happens while the phone is offline can be *named* as offline, which is
 * both true and actionable, and the retry can be offered when the network comes back rather than
 * demanded while it is gone.
 *
 * ### What "online" means here, precisely
 *
 * [NetworkCapabilities.NET_CAPABILITY_INTERNET] plus
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED]. Both, and the second is the one that matters:
 * `INTERNET` alone is true for a captive-portal café wifi that answers every request with a login
 * page, which is precisely the case where an app confidently saying "you are online" is worse than
 * saying nothing. `VALIDATED` means Android actually reached the internet through it.
 *
 * ### What this is not
 *
 * Not a promise that *our* servers are reachable — a validated network and a backend that is down
 * look identical from here, and claiming otherwise would put a false "you are offline" over an
 * outage. It answers one question: is there a path off this device.
 */
class NetworkStatus(private val context: Context) {

    /**
     * Emits on every change, starting with the current state.
     *
     * A cold flow with the callback registered per collector, and unregistered when the last one
     * goes away: a listener that outlives the screen watching it is a leak the system holds, not
     * one the garbage collector can clear.
     */
    val online: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            // No connectivity service at all — a stripped image, or a test environment. Reporting
            // "offline" there would put a permanent banner over a working app, so the honest
            // default is to say nothing is wrong.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        // Tracked as a set rather than a boolean. A phone handing over from wifi to cellular
        // briefly holds both, and `onLost` for the old one arrives *after* `onAvailable` for the
        // new one — so a boolean flips to false for a moment and every screen flashes an offline
        // banner during an ordinary handover.
        val validated = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val usable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (usable) validated += network else validated -= network
                trySend(validated.isNotEmpty())
            }

            override fun onLost(network: Network) {
                validated -= network
                trySend(validated.isNotEmpty())
            }
        }

        trySend(currentlyOnline(manager))
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** A one-shot read, for the moment a request fails and the app has to say why. */
    fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        return currentlyOnline(manager)
    }

    private fun currentlyOnline(manager: ConnectivityManager): Boolean {
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

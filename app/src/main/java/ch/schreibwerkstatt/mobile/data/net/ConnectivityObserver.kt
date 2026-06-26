package ch.schreibwerkstatt.mobile.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Beobachtet die Netzwerk-Erreichbarkeit als [Flow]<Boolean>.
 *
 * Bewusst NUR `NET_CAPABILITY_INTERNET` (nicht `VALIDATED`): Der Server ist
 * self-hosted/variabel und läuft oft in einem LAN ohne Internet-Validierung
 * (siehe CLAUDE.md). Würden wir `VALIDATED` verlangen, gälte das Gerät auf einem
 * reinen LAN-WLAN fälschlich als offline.
 */
class ConnectivityObserver(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val online: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(isCurrentlyOnline()) }
        }
        trySend(isCurrentlyOnline())
        cm?.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    fun isCurrentlyOnline(): Boolean {
        val manager = cm ?: return false
        val active = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

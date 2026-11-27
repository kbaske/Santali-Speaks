package app.santals.speaks.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the device has an internet connection.
 * This is a hint for the UI and for choosing between cache and network. The network call itself
 * still reports real failures, because "connected" does not always mean "server reachable".
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _isOnline = MutableStateFlow(readCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                _isOnline.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            override fun onLost(network: Network) {
                _isOnline.value = readCurrentlyOnline()
            }
        }
        try {
            connectivity?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback,
            )
        } catch (e: SecurityException) {
            // ACCESS_NETWORK_STATE is declared, so this only happens on a broken device profile.
            // The app then relies on real request failures instead of connectivity events.
        } catch (e: RuntimeException) {
            // Some OEM builds throw when too many callbacks are registered.
        }
    }

    fun currentlyOnline(): Boolean = readCurrentlyOnline()

    private fun readCurrentlyOnline(): Boolean {
        val manager = connectivity ?: return false
        return try {
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: SecurityException) {
            false
        }
    }
}

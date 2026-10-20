package app.hongo.reader.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.ConcurrentHashMap

/**
 * The phone's current network, and whether it is a hotspot with a login page (Android's captive
 * portal check: a café's or hotel's Wi-Fi). Android only reports the login page until the reader
 * has signed in to it, so a network seen with one stays a hotspot until the phone leaves it. A
 * hotspot joined while HonGO! was not running and already signed in to is not recognised.
 */
class NetworkWatch(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val hotspots = ConcurrentHashMap.newKeySet<Network>()

    init {
        try {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) hotspots += network
                }

                override fun onLost(network: Network) {
                    hotspots -= network
                }
            })
        } catch (_: RuntimeException) {
            // Without the callback only a login page showing right now is recognised.
        }
    }

    fun current(): Network? = try {
        connectivity?.activeNetwork
    } catch (_: RuntimeException) {
        null
    }

    fun isHotspot(): Boolean {
        val network = current() ?: return false
        if (network in hotspots) return true
        val capabilities = try {
            connectivity?.getNetworkCapabilities(network)
        } catch (_: RuntimeException) {
            null
        }
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) != true) return false
        hotspots += network
        return true
    }
}

package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class NetworkState(
    val isConnected: Boolean,
    val isValidated: Boolean,
    val isWifi: Boolean,
) {
    val isOnline = isConnected && isValidated
}

@Suppress("DEPRECATION")
fun Context.activeNetworkState(): NetworkState {
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return NetworkState(
        isConnected = connectivityManager.activeNetworkInfo?.isConnected ?: false,
        isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false,
        isWifi = wifiManager.isWifiEnabled && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false,
    )
}

fun Context.networkStateFlow() = callbackFlow {
    val networkCallback = object : NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(activeNetworkState())
        }
        override fun onLost(network: Network) {
            trySend(activeNetworkState())
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    } else {
        // registerDefaultNetworkCallback requires API 24; fall back to the legacy
        // NetworkRequest-based registration (available since API 21) on Android 6.
        @Suppress("DEPRECATION")
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }
    awaitClose {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

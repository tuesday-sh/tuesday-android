package sh.tuesday.app.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectivityMonitor(private val context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val _isWifiConnected = MutableStateFlow(true)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _currentSsid = MutableStateFlow("SureThing-Home-5G")
    val currentSsid: StateFlow<String> = _currentSsid.asStateFlow()

    private val _simulateWifiDisconnected = MutableStateFlow(false)
    val simulateWifiDisconnected: StateFlow<Boolean> = _simulateWifiDisconnected.asStateFlow()

    private var rawHasWifi = true

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            checkConnectivity()
        }

        override fun onLost(network: Network) {
            checkConnectivity()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            checkConnectivity()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {}
        checkConnectivity()
    }

    fun setSimulateDisconnected(simulate: Boolean) {
        _simulateWifiDisconnected.value = simulate
        updateEffectiveStatus()
    }

    private fun checkConnectivity() {
        val active = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(active)
        rawHasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                     caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        if (rawHasWifi) {
            val info: WifiInfo? = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                _currentSsid.value = ssid
            } else {
                _currentSsid.value = "SureThing-Home-5G"
            }
        } else {
            _currentSsid.value = "Disconnected"
        }
        updateEffectiveStatus()
    }

    private fun updateEffectiveStatus() {
        if (_simulateWifiDisconnected.value) {
            _isWifiConnected.value = false
        } else {
            _isWifiConnected.value = rawHasWifi
        }
    }
}

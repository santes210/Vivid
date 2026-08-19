package com.vivid.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Transporte activo. Distinguir Wi-Fi de datos móviles es lo que permite
 * la precarga adaptativa de Reels/Stories (no gastar la tarifa).
 */
enum class NetworkTransport {
    None,
    Wifi,
    Cellular,
    Ethernet,
    Other
}

/**
 * Monitor de conectividad global de la app: una única fuente de verdad para
 * "¿hay internet?" y "¿es Wi-Fi o datos?".
 *
 * La UI lo usa para banners de "Sin conexión" y para [PlaybackPolicy].
 * Se inicializa una sola vez en VividApplication; el callback se registra
 * contra el applicationContext, así que no hay fugas de Activity.
 *
 * Requiere el permiso ACCESS_NETWORK_STATE (ya declarado en el manifest).
 */
object NetworkMonitor {

    /** true = en línea, false = sin conexión, null = aún sin detectar. */
    private val _isOnline = MutableStateFlow<Boolean?>(null)
    val isOnline: StateFlow<Boolean?> = _isOnline.asStateFlow()

    private val _transport = MutableStateFlow(NetworkTransport.None)
    val transport: StateFlow<NetworkTransport> = _transport.asStateFlow()

    /**
     * true si la red cuenta contra una tarifa (celular, hotspot medido).
     * Default conservador: true hasta que el sistema confirme lo contrario.
     */
    private val _isMetered = MutableStateFlow(true)
    val isMetered: StateFlow<Boolean> = _isMetered.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }

        val connectivityManager = ContextCompat.getSystemService(
            context.applicationContext,
            ConnectivityManager::class.java
        ) ?: return

        applyCapabilities(currentCapabilities(connectivityManager))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                applyCapabilities(connectivityManager.getNetworkCapabilities(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                applyCapabilities(networkCapabilities)
            }

            override fun onLost(network: Network) {
                applyCapabilities(currentCapabilities(connectivityManager))
            }

            override fun onUnavailable() {
                applyCapabilities(null)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    fun currentTransport(): NetworkTransport = _transport.value

    fun currentIsMetered(): Boolean = _isMetered.value

    /** Consulta puntual, sin depender del callback (útil al arrancar). */
    fun isCurrentlyOnline(connectivityManager: ConnectivityManager): Boolean {
        val capabilities = currentCapabilities(connectivityManager) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun currentCapabilities(connectivityManager: ConnectivityManager): NetworkCapabilities? {
        val network = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(network)
    }

    private fun applyCapabilities(capabilities: NetworkCapabilities?) {
        if (capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            _isOnline.value = false
            _transport.value = NetworkTransport.None
            _isMetered.value = true
            return
        }
        _isOnline.value = true
        _transport.value = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.Ethernet
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Cellular
            else -> NetworkTransport.Other
        }
        _isMetered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

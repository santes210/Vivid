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
 * Monitor de conectividad global de la app: una única fuente de verdad para
 * "¿hay internet?".
 *
 * La UI lo usa para mostrar los banners de "Sin conexión"
 * (VividOfflineBannerHost) y para saber si un reintento tiene sentido.
 * Se inicializa una sola vez en VividApplication; el callback se registra
 * contra el applicationContext, así que no hay fugas de Activity.
 *
 * Requiere el permiso ACCESS_NETWORK_STATE (ya declarado en el manifest).
 */
object NetworkMonitor {

    /** true = en línea, false = sin conexión, null = aún sin detectar. */
    private val _isOnline = MutableStateFlow<Boolean?>(null)
    val isOnline: StateFlow<Boolean?> = _isOnline.asStateFlow()

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

        // Estado inicial síncrono: evita un flash de "sin conexión" al abrir
        // la app mientras llega el primer callback del sistema.
        _isOnline.value = isCurrentlyOnline(connectivityManager)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
            }

            override fun onUnavailable() {
                _isOnline.value = false
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    /** Consulta puntual, sin depender del callback (útil al arrancar). */
    fun isCurrentlyOnline(connectivityManager: ConnectivityManager): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

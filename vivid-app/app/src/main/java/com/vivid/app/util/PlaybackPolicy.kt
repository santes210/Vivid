package com.vivid.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Política de precarga adaptativa para Reels y Stories.
 *
 * El modo oscuro/claro ya se resuelve en Theme; aquí decidimos cuánto
 * video adelantar según:
 *   - Ahorro de datos (Ajustes)
 *   - Transporte real (Wi-Fi / datos móviles / Ethernet / nada)
 *   - Si la red es medida (hotspot, roaming, etc.)
 *
 * En datos móviles o ahorro: solo el ítem visible, sin prefetch.
 * En Wi-Fi: una página extra y prefetch de la siguiente story.
 */
object PlaybackPolicy {

    data class Decision(
        val pageSize: Int,
        val beyondViewportPageCount: Int,
        val prefetchNextMedia: Boolean,
        val autoplayAllowed: Boolean,
        val constrained: Boolean
    )

    fun decide(
        dataSaver: Boolean,
        transport: NetworkTransport,
        isMetered: Boolean,
        autoplaySetting: Boolean
    ): Decision {
        val onUnmeteredWifi = (transport == NetworkTransport.Wifi ||
            transport == NetworkTransport.Ethernet) && !isMetered
        val offline = transport == NetworkTransport.None
        val constrained = dataSaver || offline || !onUnmeteredWifi ||
            transport == NetworkTransport.Cellular

        return when {
            offline || dataSaver -> Decision(
                pageSize = 6,
                beyondViewportPageCount = 0,
                prefetchNextMedia = false,
                autoplayAllowed = autoplaySetting && !offline && !dataSaver,
                constrained = true
            )
            constrained -> Decision(
                pageSize = 8,
                beyondViewportPageCount = 0,
                prefetchNextMedia = false,
                autoplayAllowed = autoplaySetting,
                constrained = true
            )
            else -> Decision(
                pageSize = 15,
                beyondViewportPageCount = 1,
                prefetchNextMedia = true,
                autoplayAllowed = autoplaySetting,
                constrained = false
            )
        }
    }

    fun current(
        dataSaver: Boolean = SettingsManager.dataSaverMode,
        autoplaySetting: Boolean = SettingsManager.autoplayReels
    ): Decision = decide(
        dataSaver = dataSaver,
        transport = NetworkMonitor.currentTransport(),
        isMetered = NetworkMonitor.currentIsMetered(),
        autoplaySetting = autoplaySetting
    )
}

@Composable
fun rememberPlaybackPolicy(): PlaybackPolicy.Decision {
    val transport by NetworkMonitor.transport.collectAsState()
    val metered by NetworkMonitor.isMetered.collectAsState()
    val dataSaver = SettingsManager.dataSaverMode
    val autoplay = SettingsManager.autoplayReels
    return remember(transport, metered, dataSaver, autoplay) {
        PlaybackPolicy.decide(dataSaver, transport, metered, autoplay)
    }
}

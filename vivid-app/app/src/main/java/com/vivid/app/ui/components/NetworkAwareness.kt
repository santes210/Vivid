package com.vivid.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.vivid.app.util.NetworkMonitor

/**
 * Estado de conectividad en Compose:
 * true = en línea, false = sin conexión, null = aún sin detectar.
 * Usar `null` como "no mostrar nada" evita banners falsos al arrancar.
 */
@Composable
fun collectIsOnlineState(): State<Boolean?> = NetworkMonitor.isOnline.collectAsState()

/**
 * Banner de "Sin conexión" listo para incrustar arriba de cualquier pantalla.
 * Solo se muestra cuando el monitor detecta que NO hay red.
 *
 * ```
 * Column {
 *     VividOfflineBannerHost()
 *     // ... contenido de la pantalla
 * }
 * ```
 */
@Composable
fun VividOfflineBannerHost(
    modifier: Modifier = Modifier,
    message: String = "Sin conexión · Algunos contenidos pueden no estar actualizados"
) {
    val isOnline by collectIsOnlineState()
    if (isOnline == false) {
        VividOfflineBanner(modifier = modifier, message = message)
    }
}

package com.vivid.app.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Preferencia de movimiento de Vivid para todo el árbol de Compose.
 *
 * Cuando vale `false`, las pantallas deben mostrar el estado final directamente y evitar
 * animaciones decorativas o infinitas. Las interacciones y el contenido siguen funcionando.
 */
val LocalVividAnimationsEnabled = compositionLocalOf { true }

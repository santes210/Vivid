package com.vivid.app.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.compositionLocalOf

/**
 * Preferencia de movimiento de Vivid para todo el árbol de Compose.
 *
 * Cuando vale `false`, las pantallas deben mostrar el estado final directamente y evitar
 * animaciones decorativas o infinitas. Las interacciones y el contenido siguen funcionando.
 */
val LocalVividAnimationsEnabled = compositionLocalOf { true }

/**
 * Material 3 Expressive — MotionScheme.
 *
 * El sistema de movimiento Expressive sustituye las animaciones de duración + easing por
 * físicas de muelle (springs): `MotionScheme.expressive()` produce rebote/overshoot visible,
 * lo que hace que los componentes se sientan "vivos" y personales (los componentes se
 * deforman y animan al tocarlos). Es la base de las microinteracciones de M3 Expressive.
 *
 * `MotionScheme.standard()` es la alternativa contenida y predecible (úsala con
 * `LocalVividAnimationsEnabled == false` si implementas reducción de movimiento).
 *
 * Requiere material3 1.5.0-alpha23+ y el opt-in `ExperimentalMaterial3ExpressiveApi`
 * (ya añadido globalmente en build.gradle.kts).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val VividMotionScheme: MotionScheme = MotionScheme.expressive()

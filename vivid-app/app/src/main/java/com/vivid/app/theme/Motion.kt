package com.vivid.app.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf

/**
 * Preferencia de movimiento de Vivid para todo el árbol de Compose.
 *
 * Cuando vale `false`, las pantallas deben mostrar el estado final directamente y evitar
 * animaciones decorativas o infinitas. Las interacciones y el contenido siguen funcionando.
 *
 * Lo alimenta `MainActivity` combinando el ajuste propio ("Animaciones suaves") con
 * `ValueAnimator.areAnimatorsEnabled()`, que ya refleja "Quitar animaciones" del sistema.
 */
val LocalVividAnimationsEnabled = compositionLocalOf { true }

/**
 * Movimiento de Vivid = `MotionScheme` de Material 3 Expressive + respeto por
 * la preferencia de reducir movimiento.
 *
 * [VividTheme] instala `MotionScheme.expressive()`: springs con un punto de
 * rebote controlado que los componentes de material3 (bottom sheets, botones,
 * FAB, ButtonGroup, FloatingToolbar…) ya usan solos. Estas funciones son para
 * las animaciones *propias* de la app, de modo que compartan el mismo lenguaje
 * en vez de repartir `tween(220)` a mano por 27.000 líneas.
 *
 * Reglas de uso:
 *   - **spatial** → cosas que se mueven o cambian de tamaño (posición, escala,
 *     tamaño, offset). Tienen rebote.
 *   - **effects** → cosas que no se mueven (color, alfa, elevación). Sin rebote.
 *   - `fast` para microinteracciones (< 100 dp de recorrido), `default` para
 *     transiciones normales, `slow` para elementos grandes o pantallas.
 *
 * Con movimiento reducido devuelven [snap], es decir, salto directo al estado
 * final: la UI sigue siendo correcta, simplemente no se anima.
 */
object VividMotion {

    @Composable
    @ReadOnlyComposable
    private fun reduced(): Boolean = !LocalVividAnimationsEnabled.current

    /** Microinteracción con movimiento: pulsación, tick del like, chips. */
    @Composable
    fun <T> fastSpatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

    /** Movimiento estándar: reordenar, expandir, indicadores de navegación. */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

    /** Movimiento de elementos grandes: hojas, paneles, transiciones de pantalla. */
    @Composable
    fun <T> slowSpatial(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else MaterialTheme.motionScheme.slowSpatialSpec()

    /** Cambios sin desplazamiento y rápidos: tinte de un icono al activarse. */
    @Composable
    fun <T> fastEffects(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

    /** Cambios sin desplazamiento: color, alfa, elevación. */
    @Composable
    fun <T> effects(): FiniteAnimationSpec<T> =
        if (reduced()) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()
}

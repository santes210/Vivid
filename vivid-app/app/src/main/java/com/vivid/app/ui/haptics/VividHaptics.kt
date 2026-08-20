package com.vivid.app.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vivid.app.util.SettingsManager

/**
 * Vocabulario háptico de Vivid.
 *
 * En Android el háptico no es decoración: es la confirmación física de que el
 * sistema registró tu gesto, y es lo que separa una app que "se siente cara"
 * de un clon. La regla es que cada tipo de evento tenga SIEMPRE el mismo
 * patrón en toda la app, por eso esto es un vocabulario cerrado y no llamadas
 * sueltas a `performHapticFeedback` repartidas por las pantallas.
 *
 * Cuándo usar cada uno:
 *
 * | Evento                                   | API                |
 * |------------------------------------------|--------------------|
 * | Activar algo (like, guardar, seguir)     | [toggleOn]         |
 * | Desactivar algo (quitar like, dejar de…) | [toggleOff]        |
 * | Acción completada (enviar, publicar)     | [confirm]          |
 * | Acción rechazada / error de validación   | [reject]           |
 * | Cambiar de pestaña o de destino          | [tick]             |
 * | Menú contextual / selección larga        | [longPress]        |
 * | Un gesto cruza su umbral (swipe, drag)   | [gestureThreshold] |
 *
 * Respeta `Ajustes → Apariencia → Respuesta háptica`: cuando está apagado,
 * todas las llamadas son no-ops (y siguen siendo baratas: un `if`).
 */
@Immutable
class VividHaptics internal constructor(
    private val haptics: HapticFeedback,
    private val enabled: Boolean
) {
    private fun perform(type: HapticFeedbackType) {
        if (enabled) haptics.performHapticFeedback(type)
    }

    /** Se activó un estado: like, guardar, seguir, activar un switch. */
    fun toggleOn() = perform(HapticFeedbackType.ToggleOn)

    /** Se desactivó un estado: quitar like, dejar de seguir. */
    fun toggleOff() = perform(HapticFeedbackType.ToggleOff)

    /** Atajo para toggles: elige [toggleOn] / [toggleOff] según el estado nuevo. */
    fun toggle(newValue: Boolean) = if (newValue) toggleOn() else toggleOff()

    /** La acción se completó: mensaje enviado, publicación creada. */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    /** La acción se rechazó: sin conexión, validación fallida, permiso denegado. */
    fun reject() = perform(HapticFeedbackType.Reject)

    /** Paso discreto: cambiar de pestaña, mover un slider por posiciones. */
    fun tick() = perform(HapticFeedbackType.SegmentTick)

    /** Pulsación larga: abrir menú contextual, entrar en modo selección. */
    fun longPress() = perform(HapticFeedbackType.LongPress)

    /** Un gesto continuo alcanzó su umbral (pull-to-refresh, swipe to dismiss). */
    fun gestureThreshold() = perform(HapticFeedbackType.GestureThresholdActivate)
}

/**
 * Instancia estable de [VividHaptics] para el composable actual.
 *
 * Se puede llamar sin miedo en cualquier pantalla: en un `@Preview` el
 * `LocalHapticFeedback` por defecto no hace nada.
 */
@Composable
fun rememberVividHaptics(): VividHaptics {
    val haptics = LocalHapticFeedback.current
    val enabled = SettingsManager.hapticFeedbackEnabled
    return remember(haptics, enabled) { VividHaptics(haptics, enabled) }
}

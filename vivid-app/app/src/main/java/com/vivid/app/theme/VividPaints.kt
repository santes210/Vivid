package com.vivid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Pigmentos que no son roles de Material.
 *
 * El editor de stories necesita una paleta de pintura (blanco, negro, coral…)
 * independiente del tema: un texto amarillo sobre una foto no puede volverse
 * `primary` cuando el usuario activa color dinámico. Viven aquí — no como
 * `Color(0x…)` sueltos en `presentation/` — para que el grep de hex en
 * pantallas se quede en cero y se puedan retocar juntos.
 */
object VividPaints {
    val StoryText: List<Color> = listOf(
        Color.White,
        Color.Black,
        Color.Red,
        Color(0xFFFF6B6B),
        Color(0xFFFFD93D),
        Color(0xFF6BCB77),
        Color(0xFF4D96FF),
        Color(0xFFB983FF),
        Color(0xFFFF9F45)
    )
}

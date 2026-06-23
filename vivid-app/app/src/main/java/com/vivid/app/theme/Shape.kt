package com.vivid.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material You 3 — Shape system (esquinas expresivas).
 *
 * M3 introduce un sistema de formas consistente en toda la app
 * para dar una sensación táctil coherente.
 *
 * Curvas elegidas para Vivid (estilo Instagram-friendly):
 *   - Extra small: 4dp (chips, badges)
 *   - Small:      8dp (botones pequeños, text fields compactos)
 *   - Medium:     16dp (cards, botones principales)
 *   - Large:      24dp (sheets, dialogs)
 *   - Extra large: 32dp (FABs extendidos, hero cards)
 */
val VividShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

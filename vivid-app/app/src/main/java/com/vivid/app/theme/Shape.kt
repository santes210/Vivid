package com.vivid.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material You 3 — Shape system (esquinas expresivas).
 *
 * M3 expresivo (2024+) sube los radios para una sensación más
 * amigable y táctil. Solo cambia la GEOMETRÍA de los componentes:
 * los colores dinámicos del sistema siguen intactos.
 *
 *   - Extra small:  8dp (badges, chips pequeños)
 *   - Small:       12dp (chips, botones compactos)
 *   - Medium:      20dp (cards, contenedores)
 *   - Large:       28dp (sheets, dialogs, barras)
 *   - Extra large: 36dp (hero cards, dialogs grandes)
 */
val VividShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

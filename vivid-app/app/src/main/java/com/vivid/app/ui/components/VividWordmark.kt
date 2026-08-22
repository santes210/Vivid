package com.vivid.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.SoraFamily
import androidx.compose.material3.Text

/**
 * Wordmark de marca: la ✦ de Vivid en vector real + "Vivid" en Sora
 * ExtraBold, ambos con el gradiente primary→tertiary (coral→ámbar) de la
 * paleta. Es EL logo de la app: úsalo en la top bar del feed, splash o
 * cualquier momento hero, en lugar de un `Text` suelto.
 *
 * El gradiente se toma del [MaterialTheme] vigente, así que respeta
 * claro/oscuro y Material You sin trabajo extra.
 */
@Composable
fun VividWordmark(
    modifier: Modifier = Modifier,
    sparkSize: Dp = 22.dp
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    Row(
        // Un logo se anuncia como una sola cosa ("Vivid"), no como
        // "imagen sin etiqueta" + texto por separado.
        modifier = modifier.clearAndSetSemantics { contentDescription = "Vivid" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(modifier = Modifier.size(sparkSize)) {
            drawPath(path = sparklePath(size), brush = gradient)
        }
        Text(
            text = "Vivid",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = SoraFamily,
                fontWeight = FontWeight.ExtraBold,
                brush = gradient
            )
        )
    }
}

/**
 * Estrella de 4 puntas (✦) como vector real: cuatro curvas cuadráticas con
 * el punto de control en el centro, que producen los lados cóncavos del
 * "sparkle" clásico. Se dibuja nítida a cualquier tamaño y densidad.
 */
private fun sparklePath(size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    return Path().apply {
        moveTo(cx, 0f)                // punta superior
        quadraticTo(cx, cy, w, cy)    // → punta derecha
        quadraticTo(cx, cy, cx, h)    // → punta inferior
        quadraticTo(cx, cy, 0f, cy)   // → punta izquierda
        quadraticTo(cx, cy, cx, 0f)   // → cierre en la superior
        close()
    }
}

// ─────────────────────────────────────────────────────────────
//  Previews
// ─────────────────────────────────────────────────────────────

@com.vivid.app.ui.preview.VividPreview
@Composable
private fun VividWordmarkPreview() {
    com.vivid.app.ui.preview.VividPreviewSurface {
        VividWordmark()
    }
}

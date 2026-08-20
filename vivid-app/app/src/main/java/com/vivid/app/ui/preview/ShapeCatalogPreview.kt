package com.vivid.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.toShape
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.rememberVividMorph

/**
 * Catálogo de las **35 formas** de Material 3 Expressive.
 *
 * Material añadió 35 `MaterialShapes` con el rediseño Expressive. Elegir entre
 * ellas leyendo nombres (`Ghostish`, `Bun`, `PuffyDiamond`…) es imposible: este
 * preview las pinta todas con su nombre para poder señalar una y usarla.
 *
 * El rol semántico de cada forma en Vivid se define en
 * [VividMaterialShapes]; la app nunca debería referenciar
 * `MaterialShapes.X` directamente.
 */
@Preview(name = "Catálogo · 35 formas", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
private fun MaterialShapesCatalogPreview() {
    VividPreviewSurface(padding = 12) {
        val columns = 4
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "MaterialShapes · ${VividMaterialShapes.Catalog.size} formas",
                style = MaterialTheme.typography.titleMedium
            )
            VividMaterialShapes.Catalog.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (name, polygon) ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = polygon.toShape()
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                name,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Relleno para que la última fila no estire sus celdas.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Demo de morphing: el deslizador interpola entre las dos formas.
 *
 * Es la razón de ser del sistema — las formas no son sellos sueltos, son
 * polígonos que se transforman entre sí. En la app esto se usa, por ejemplo,
 * en el botón de Crear (círculo → galleta al pulsarlo).
 *
 * Hay que abrir el preview en modo interactivo para mover el deslizador.
 */
@Preview(name = "Morphing · círculo ⇄ corazón", showBackground = true, widthDp = 320)
@Composable
private fun ShapeMorphPreview() {
    VividPreviewSurface {
        var progress by remember { mutableFloatStateOf(0f) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = rememberVividMorph(
                            start = VividMaterialShapes.MorphResting,
                            end = VividMaterialShapes.AvatarActive,
                            progress = progress
                        )
                    )
            )
            Spacer(Modifier.height(20.dp))
            Slider(
                value = progress,
                onValueChange = { progress = it },
                modifier = Modifier.width(240.dp)
            )
            Text(
                "progress = ${"%.2f".format(progress)}",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

/** La secuencia de polígonos que usa el indicador de carga de Vivid. */
@VividPreview
@Composable
private fun LoadingSequencePreview() {
    VividPreviewSurface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VividMaterialShapes.LoadingSequence.forEach { polygon ->
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = polygon.toShape()
                        )
                )
            }
        }
    }
}

/** Roles semánticos: lo que la app usa de verdad. */
@VividPreview
@Composable
private fun ShapeRolesPreview() {
    VividPreviewSurface {
        val roles = listOf(
            "Vacío" to VividMaterialShapes.EmptyStateContainer,
            "Celebración" to VividMaterialShapes.Celebration,
            "Avatar" to VividMaterialShapes.AvatarHighlight,
            "Like" to VividMaterialShapes.Like,
            "Logro" to VividMaterialShapes.Achievement,
            "Destacado" to VividMaterialShapes.Featured
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            roles.forEach { (name, shape) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, shape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

package com.vivid.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Conversión de `RoundedPolygon` a [Shape] **sin contexto de composición**.
 *
 * Por qué existe y no se usa `RoundedPolygon.toShape()` de material3: esa
 * extensión es `@Composable`, así que solo se puede llamar dentro de una
 * función composable. Los roles de forma de Vivid ([VividMaterialShapes]) son
 * constantes de un `object` y se usan también fuera de composición (por
 * ejemplo dentro de `drawBehind`, que es un `DrawScope`, no un `@Composable`).
 *
 * La conversión normaliza con las medidas reales del polígono
 * (`calculateBounds`) en vez de asumir un espacio concreto: así funciona igual
 * si Material cambia la normalización de sus 35 formas.
 */
@Immutable
class VividPolygonShape(private val polygon: RoundedPolygon) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val bounds = polygon.calculateBounds()
        val boundsWidth = (bounds[2] - bounds[0]).takeIf { it > 0f } ?: 1f
        val boundsHeight = (bounds[3] - bounds[1]).takeIf { it > 0f } ?: 1f

        // El orden importa: la matriz aplica primero la traslación (en el
        // espacio del polígono) y después la escala al tamaño del componente.
        val matrix = Matrix()
        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])

        val path = polygon.toPath().asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is VividPolygonShape && other.polygon == polygon)

    override fun hashCode(): Int = polygon.hashCode()
}

/** Convierte cualquier polígono de `MaterialShapes` en un [Shape] reutilizable. */
fun RoundedPolygon.toVividShape(): Shape = VividPolygonShape(this)

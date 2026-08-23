package com.vivid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.vivid.app.theme.VividMotion

/**
 * Morphing entre las formas de Material 3 Expressive.
 *
 * Las 35 `MaterialShapes` no son sellos independientes: al ser `RoundedPolygon`
 * se pueden **interpolar**, y ahí está la gracia del sistema. Un botón que pasa
 * de círculo a galleta de 9 puntas al pulsarse comunica el estado con la propia
 * silueta, sin cambiar de color ni añadir un ripple extra.
 *
 * Implementación: un [Shape] que en cada frame pide a [Morph] el `Path` del
 * progreso actual y lo escala al tamaño del componente. Es el patrón que
 * documenta Android para shape morphing; el `Morph` se cachea con `remember`
 * porque construirlo implica emparejar los vértices de los dos polígonos.
 *
 * Coste: `createOutline` se llama al cambiar el tamaño o el progreso, así que
 * no conviene animarlo en cada elemento de una lista larga. Para uso puntual
 * (un botón, un avatar, un FAB) es perfectamente barato.
 */
@Stable
class VividMorphShape(
    private val morph: Morph,
    private val progress: Float,
    /** Caja envolvente común de las dos formas: `[left, top, right, bottom]`. */
    private val bounds: FloatArray
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Se normaliza con las medidas reales de los polígonos (igual que
        // VividPolygonShape) y con la caja COMÚN de los dos, para que la forma
        // no cambie de tamaño mientras se está transformando.
        val boundsWidth = (bounds[2] - bounds[0]).takeIf { it > 0f } ?: 1f
        val boundsHeight = (bounds[3] - bounds[1]).takeIf { it > 0f } ?: 1f

        val matrix = Matrix()
        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])

        val path: Path = morph.toPath(progress = progress.coerceIn(0f, 1f)).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** Caja envolvente que contiene a los dos polígonos. */
private fun unionBounds(start: RoundedPolygon, end: RoundedPolygon): FloatArray {
    val a = start.calculateBounds()
    val b = end.calculateBounds()
    return floatArrayOf(
        minOf(a[0], b[0]),
        minOf(a[1], b[1]),
        maxOf(a[2], b[2]),
        maxOf(a[3], b[3])
    )
}

/**
 * Forma que interpola entre [start] y [end] según [progress] (0f..1f).
 *
 * ```
 * Surface(shape = rememberVividMorph(MaterialShapes.Circle, MaterialShapes.Heart, p))
 * ```
 */
@Composable
fun rememberVividMorph(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: Float
): Shape {
    val morph = remember(start, end) { Morph(start, end) }
    val bounds = remember(start, end) { unionBounds(start, end) }
    return remember(morph, bounds, progress) { VividMorphShape(morph, progress, bounds) }
}

/**
 * Forma que se transforma de [resting] a [pressed] mientras el usuario
 * mantiene pulsado el componente, usando el `MotionScheme` del tema.
 *
 * Pensado para el botón de Crear, FABs y avatares interactivos:
 *
 * ```
 * val interactions = remember { MutableInteractionSource() }
 * Surface(
 *     onClick = { … },
 *     interactionSource = interactions,
 *     shape = pressMorphShape(interactions)
 * ) { … }
 * ```
 *
 * Con "reducir movimiento" activo el cambio es instantáneo (lo decide
 * [VividMotion]), no se pierde el feedback visual.
 */
@Composable
fun pressMorphShape(
    interactionSource: InteractionSource,
    resting: RoundedPolygon = com.vivid.app.theme.VividMaterialShapes.MorphResting,
    pressed: RoundedPolygon = com.vivid.app.theme.VividMaterialShapes.MorphPressed
): Shape {
    val isPressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = VividMotion.fastSpatial(),
        label = "pressMorph"
    )
    return rememberVividMorph(resting, pressed, progress)
}

/**
 * Versión horizontal del feedback de presión para botones de acción principales.
 * A diferencia del morph poligonal (pensado para cajas cuadradas), anima el radio
 * 20dp → 12dp y mantiene los bordes de un botón ancho sin deformarlos.
 */
@Composable
fun pressPrimaryButtonShape(interactionSource: InteractionSource): Shape {
    val isPressed by interactionSource.collectIsPressedAsState()
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isPressed) 12.dp else 20.dp,
        animationSpec = VividMotion.fastSpatial(),
        label = "primaryButtonPressShape"
    )
    return androidx.compose.foundation.shape.RoundedCornerShape(corner)
}

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
    private val progress: Float
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // El polígono vive en un espacio normalizado centrado en (0,0) con
        // radio 1: hay que escalarlo a la mitad del tamaño y recentrarlo.
        val matrix = Matrix()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)

        val path: Path = morph.toPath(progress = progress.coerceIn(0f, 1f)).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
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
    return remember(morph, progress) { VividMorphShape(morph, progress) }
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

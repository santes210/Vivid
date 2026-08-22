package com.vivid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividMotion

/**
 * Anillo de historias que **morpha** de círculo a trébol de 4 hojas
 * ([VividMaterialShapes.AvatarResting] → [VividMaterialShapes.AvatarActive])
 * cuando hay una historia sin ver, en vez de ser "solo un gradiente alrededor".
 *
 * La silueta del anillo se transforma con `Morph` (mismo mecanismo que el botón
 * de Crear y el FAB del rail), animada con el `MotionScheme` del tema y
 * respetando "reducir movimiento" (en ese caso salta directo entre estados).
 *
 * - Visto / sin historia: **círculo** (la forma de reposo del avatar).
 * - Pendiente de ver: **trébol**; las cuatro puntas asoman alrededor del avatar
 *   circular, que es el detalle "expressive" que distingue el contenido nuevo.
 *
 * Sustituye al anillo de `Canvas` con `drawArc` que había antes: este rellena la
 * forma morph con el gradiente de marca (`sweepGradient` con la misma rampa que
 * el avatar hero del perfil), de modo que el anillo "sabe" a Vivid.
 *
 * @param hasUnseenStory `true` si el grupo tiene al menos una historia no vista.
 * @param ringSize       Tamaño exterior del anillo (la forma que se transforma).
 * @param avatarSize     Tamaño del avatar circular que va dentro.
 * @param avatar         Contenido central (el avatar propiamente dicho).
 */
@Composable
fun VividStoryRing(
    modifier: Modifier = Modifier,
    hasUnseenStory: Boolean,
    ringSize: Dp = 68.dp,
    avatarSize: Dp = 56.dp,
    avatar: @Composable () -> Unit
) {
    val animationsEnabled = LocalVividAnimationsEnabled.current

    // Se arranca en círculo (0f) y el LaunchedEffect dispara la transformación
    // a trébol si hay historia nueva: así el morph se VE al entrar en pantalla,
    // no solo cuando cambia el flag. Con movimiento reducido, animación = snap.
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hasUnseenStory) {
        target = if (hasUnseenStory) 1f else 0f
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animationsEnabled) VividMotion.spatial() else snap(),
        label = "vividStoryRingMorph"
    )

    val ringShape: Shape = rememberVividMorph(
        start = VividMaterialShapes.AvatarResting,
        end = VividMaterialShapes.AvatarActive,
        progress = progress
    )
    val storyRing = LocalVividAccents.current.storyRing
    val ringWidth = (ringSize - avatarSize) / 2f

    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        // Anillo: la forma morph rellena con el gradiente de marca.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ringShape)
                .background(Brush.sweepGradient(storyRing))
        )
        // Relleno de superficie: deja el gradiente como borde (igual que el
        // anillo clásico) y separa el color del anillo del avatar.
        Box(
            modifier = Modifier
                .size(ringSize - ringWidth)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
        )
        Box(modifier = Modifier.size(avatarSize), contentAlignment = Alignment.Center) {
            avatar()
        }
    }
}

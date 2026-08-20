package com.vivid.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividMotion
import com.vivid.app.ui.haptics.rememberVividHaptics

/**
 * El corazón de Vivid.
 *
 * Un like es la interacción más repetida de toda la app: si se siente barata,
 * la app se siente barata. Este botón añade tres cosas sobre un `IconButton`
 * normal:
 *
 *  - **Háptico**: `ToggleOn` al dar like, `ToggleOff` al quitarlo. Es la
 *    confirmación física de que se registró, antes incluso de que la red
 *    responda.
 *  - **Rebote con anillo**: el icono se comprime, sale rebotando por encima de
 *    su tamaño y un anillo se expande y desaparece. Solo cuando el gesto viene
 *    del usuario: si el like llega porque Firestore sincronizó otro
 *    dispositivo, el icono cambia sin espectáculo.
 *  - **Color de marca armonizado**: usa el acento de producto
 *    (`LocalVividAccents.like`), no un `Color.Red` a pelo, así que sigue
 *    siendo "el rojo de Vivid" aunque el usuario tenga color dinámico.
 *
 * Con "reducir movimiento" activo el estado cambia sin animación, pero el
 * háptico se mantiene: es accesibilidad, no decoración.
 */
@Composable
fun VividLikeButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val haptics = rememberVividHaptics()
    val accents = LocalVividAccents.current
    val animationsEnabled = LocalVividAnimationsEnabled.current

    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    // Solo se anima lo que el usuario provoca; los cambios que llegan del
    // backend actualizan el icono en silencio.
    var localTaps by remember { mutableIntStateOf(0) }

    LaunchedEffect(localTaps) {
        if (localTaps == 0 || !animationsEnabled) return@LaunchedEffect
        if (isLiked) {
            ring.snapTo(0f)
            scale.animateTo(0.82f, tween(durationMillis = 90))
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            scale.animateTo(0.9f, tween(durationMillis = 80))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
        }
    }

    // El anillo se lanza aparte para que corra a la vez que el rebote.
    LaunchedEffect(localTaps, isLiked) {
        if (localTaps == 0 || !animationsEnabled || !isLiked) return@LaunchedEffect
        ring.snapTo(0f)
        ring.animateTo(1f, tween(durationMillis = 420))
    }

    val tint by animateColorAsState(
        targetValue = if (isLiked) accents.like else MaterialTheme.colorScheme.onSurface,
        animationSpec = VividMotion.fastEffects(),
        label = "likeTint"
    )
    val likeLabel = stringResource(R.string.feed_like)

    IconButton(
        onClick = {
            haptics.toggle(!isLiked)
            localTaps++
            onToggle()
        },
        modifier = modifier.semantics {
            role = Role.Switch
            stateDescription = if (isLiked) "Te gusta" else "No te gusta"
        }
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = likeLabel,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .drawBehind {
                    val progress = ring.value
                    if (progress <= 0f || progress >= 1f) return@drawBehind
                    val radius = size.minDimension / 2f * (1f + progress * 1.6f)
                    drawCircle(
                        color = accents.like.copy(alpha = (1f - progress) * 0.6f),
                        radius = radius,
                        style = Stroke(width = (1f - progress) * 6f)
                    )
                }
        )
    }
}

/**
 * Contenedor de media con doble toque para dar like.
 *
 * Un toque abre la publicación; dos toques dan like y sueltan un corazón
 * grande que crece y se desvanece, más un háptico de confirmación. Nunca quita
 * el like: el doble toque solo suma, quitarlo es una acción deliberada del
 * botón (evita el "se me fue el dedo y desliké a alguien").
 */
@Composable
fun DoubleTapLikeBox(
    isLiked: Boolean,
    onLike: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val haptics = rememberVividHaptics()
    val accents = LocalVividAccents.current
    val animationsEnabled = LocalVividAnimationsEnabled.current

    val burstScale = remember { Animatable(0f) }
    val burstAlpha = remember { Animatable(0f) }
    var burstKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(burstKey) {
        if (burstKey == 0 || !animationsEnabled) return@LaunchedEffect
        burstScale.snapTo(0.4f)
        burstAlpha.snapTo(1f)
        burstScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        burstAlpha.animateTo(0f, tween(durationMillis = 260, delayMillis = 180))
    }

    Box(
        modifier = modifier.pointerInput(isLiked) {
            detectTapGestures(
                onDoubleTap = {
                    burstKey++
                    if (isLiked) {
                        // Ya tenía like: solo se celebra, no se deshace.
                        haptics.tick()
                    } else {
                        haptics.confirm()
                        onLike()
                    }
                },
                onTap = { onTap() }
            )
        }
    ) {
        content()
        if (burstAlpha.value > 0f) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White.copy(alpha = burstAlpha.value * 0.95f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = burstScale.value
                        scaleY = burstScale.value
                        alpha = burstAlpha.value
                    }
                    .drawBehind {
                        drawCircle(
                            color = accents.like.copy(alpha = burstAlpha.value * 0.35f),
                            radius = size.minDimension / 1.6f
                        )
                    }
            )
        }
    }
}

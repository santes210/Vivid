package com.vivid.app.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.haptics.rememberVividHaptics

/**
 * Pull-to-refresh de Vivid.
 *
 * El `PullToRefreshBox` de Material trae por defecto el indicador clásico: una
 * flecha que gira dentro de un círculo. Aquí se sustituye por el
 * `LoadingIndicator` expresivo — el que muerde formas y se transforma entre
 * ellas — usando **la secuencia de polígonos de la marca**
 * ([VividMaterialShapes.LoadingSequence]), la misma que ya se ve en Explorar,
 * en el detalle de publicación y en los estados de carga.
 *
 * Por qué no se usa `PullToRefreshDefaults.LoadingIndicator`, que ya existe:
 * ese atajo no acepta el parámetro `polygons`, así que daría la animación de
 * Material pero con sus formas, no con las nuestras. `IndicatorBox` es la API
 * que Material documenta justo para este caso ("useful when implementing
 * custom indicators") y aporta lo difícil: desplazamiento según el arrastre,
 * recorte, sombra y fondo.
 *
 * Dos detalles que hacen que se sienta bien y no solo se vea bien:
 *
 *  - **Mientras arrastras** el indicador es *determinado*: las formas se
 *    transforman según lo que llevas tirado (`state.distanceFraction`), así
 *    que el gesto controla la animación en vez de correr por su cuenta. Al
 *    soltar y empezar la recarga pasa al modo indeterminado.
 *  - **Háptico en el umbral**: un toque cuando cruzas el punto de no retorno
 *    (te dice "suelta ya" sin mirar) y una confirmación cuando la recarga
 *    termina. Respeta el ajuste de Apariencia como todo lo demás.
 *
 * Con movimiento reducido el indicador se queda quieto en su estado final:
 * sigue apareciendo y sigue informando, pero no anima.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VividPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable BoxScope.() -> Unit
) {
    val haptics = rememberVividHaptics()

    // Confirmación cuando la recarga TERMINA (no cuando empieza): es el
    // momento en que el usuario recupera el control y ya hay contenido nuevo.
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) haptics.confirm()
        wasRefreshing = isRefreshing
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            VividRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        content = content
    )
}

/**
 * El indicador en sí. Se expone aparte por si alguna pantalla necesita montar
 * su propio `PullToRefreshBox` (por ejemplo con un `contentAlignment`
 * distinto) sin perder el indicador de marca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VividRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = rememberVividHaptics()
    val animationsEnabled = LocalVividAnimationsEnabled.current

    // "Ya puedes soltar": se dispara al cruzar el umbral, y otra vez si el
    // usuario retrocede y vuelve a cruzarlo.
    val thresholdCrossed = state.distanceFraction >= 1f
    LaunchedEffect(thresholdCrossed) {
        if (thresholdCrossed && !isRefreshing) haptics.gestureThreshold()
    }

    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        containerColor = PullToRefreshDefaults.loadingIndicatorContainerColor,
        elevation = PullToRefreshDefaults.LoadingIndicatorElevation
    ) {
        when {
            // Recargando: animación continua.
            isRefreshing && animationsEnabled -> LoadingIndicator(
                color = PullToRefreshDefaults.loadingIndicatorColor,
                polygons = VividMaterialShapes.LoadingSequence
            )
            // Arrastrando (o con movimiento reducido): la forma la manda el
            // dedo, no un temporizador.
            else -> LoadingIndicator(
                progress = { state.distanceFraction.coerceIn(0f, 1f) },
                color = PullToRefreshDefaults.loadingIndicatorColor,
                polygons = VividMaterialShapes.LoadingSequence
            )
        }
    }
}

@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.vivid.app.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.vivid.app.theme.LocalVividAnimationsEnabled

/**
 * Transiciones de elemento compartido para Vivid.
 *
 * La diferencia entre "clon de Instagram" y "app cara" está casi entera en la
 * continuidad: al tocar una miniatura del grid, esa miniatura **es** la que
 * crece hasta ocupar el detalle; al tocar un avatar, ese avatar **es** el del
 * perfil. Sin eso, cada navegación es un corte y el usuario tiene que volver a
 * buscar dónde estaba mirando.
 *
 * Cómo se usa (3 pasos):
 *
 * 1. La app envuelve su `NavHost` en [VividSharedTransitionHost].
 * 2. Cada destino que participa se declara con [sharedComposable] en vez de
 *    `composable` (eso publica su `AnimatedVisibilityScope`).
 * 3. Los dos composables que deben "ser el mismo" aplican
 *    `Modifier.vividSharedElement(VividSharedKeys.postImage(id))` con la misma
 *    clave.
 *
 * Los modificadores son **degradables**: si no hay `SharedTransitionLayout`
 * (por ejemplo en un `@Preview`, en un test o en una pantalla que aún no migró)
 * o si el usuario pidió reducir el movimiento, devuelven el `Modifier` sin
 * tocar en vez de lanzar una excepción.
 */

/** `SharedTransitionScope` activo, o `null` fuera de [VividSharedTransitionHost]. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** `AnimatedVisibilityScope` del destino actual, o `null` si no lo publicó. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Claves de contenido compartido. Centralizadas para que nunca se desincronicen. */
object VividSharedKeys {
    /** Miniatura del grid ⇄ imagen grande del detalle. */
    fun postImage(postId: String): String = "post-image:$postId"

    /** Contenedor de la tarjeta del post (fondo, esquinas, elevación). */
    fun postContainer(postId: String): String = "post-container:$postId"

    /** Avatar en feed, chats o buscador ⇄ avatar del perfil. */
    fun avatar(userId: String): String = "avatar:$userId"

    /** Nombre de usuario junto al avatar ⇄ título del perfil. */
    fun username(userId: String): String = "username:$userId"
}

/** Envuelve [content] en un `SharedTransitionLayout` y publica su scope. */
@Composable
fun VividSharedTransitionHost(content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * Igual que `composable`, pero publica el `AnimatedVisibilityScope` del destino
 * para que sus hijos puedan participar en transiciones compartidas.
 */
fun NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) {
    composable(route = route, arguments = arguments) { backStackEntry ->
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalNavAnimatedVisibilityScope provides animatedVisibilityScope
        ) {
            content(backStackEntry)
        }
    }
}

/**
 * Marca este elemento como el mismo que el del destino anterior/siguiente con
 * la misma [key]. Pensado para imágenes y avatares (contenido idéntico a ambos
 * lados).
 */
@Composable
fun Modifier.vividSharedElement(
    key: String,
    zIndexInOverlay: Float = 0f
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (!LocalVividAnimationsEnabled.current) return this
    return with(sharedScope) {
        this@vividSharedElement.sharedElement(
            rememberSharedContentState(key = key),
            animatedScope,
            zIndexInOverlay = zIndexInOverlay
        )
    }
}

/**
 * Como [vividSharedElement] pero para contenedores cuyo contenido cambia entre
 * pantallas (una tarjeta que pasa de miniatura a cabecera): anima los límites y
 * funde el contenido.
 */
@Composable
fun Modifier.vividSharedBounds(
    key: String,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut()
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    if (!LocalVividAnimationsEnabled.current) return this
    return with(sharedScope) {
        this@vividSharedBounds.sharedBounds(
            rememberSharedContentState(key = key),
            animatedScope,
            enter = enter,
            exit = exit
        )
    }
}

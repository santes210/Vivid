package com.vivid.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vivid.app.ui.motion.vividSharedElement

/**
 * [AsyncImage] de Vivid: igual que la de Coil, pero **nunca deja un hueco en
 * blanco**.
 *
 * Problema que resuelve: en todo el feed / grid / perfil los `AsyncImage`
 * recibían solo la URL, así que mientras Coil descargaba la imagen la celda
 * estaba vacía y la foto "aparecía" de golpe (el crossfade global animaba
 * desde transparente). Con este wrapper la celda se pinta desde el primer
 * frame con el color de superficie que le toque y la foto hace crossfade
 * *desde ese color*.
 *
 * - [placeholderColor]: lo que se ve mientras carga (por defecto
 *   `surfaceContainerHigh`, el mismo bloque que usan los skeletons).
 * - [errorColor]: lo que se ve si la URL falla (p. ej. firma B2 caducada).
 *   Si prefieres un fallback compuesto (una letra, un ícono) pasa
 *   [onError] y pinta tu estado propio: este wrapper solo cubre el color.
 * - [sharedKey]: clave de [vividSharedElement] opcional para que la imagen
 *   participe en una transición compartida (miniatura ⇄ detalle) sin tener
 *   que encadenar el modificador a mano.
 *
 * Sobre `SubcomposeAsyncImage`: da estados composables más ricos, pero hace
 * subcomposición por imagen y Coil desaconseja usarla dentro de listas;
 * aquí (grids de 30+ celdas) el painter de color es la opción correcta.
 */
@Composable
fun VividAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    onError: (() -> Unit)? = null,
    sharedKey: String? = null
) {
    val resolvedModifier = if (sharedKey.isNullOrBlank()) {
        modifier
    } else {
        modifier.vividSharedElement(sharedKey)
    }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = resolvedModifier,
        contentScale = contentScale,
        placeholder = ColorPainter(placeholderColor),
        error = ColorPainter(errorColor),
        // Modelo null/vacío: mismo bloque que mientras carga.
        fallback = ColorPainter(placeholderColor),
        onError = onError?.let { callback -> { callback() } }
    )
}

package com.vivid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.painter.ColorPainter
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.ui.motion.vividSharedElement

/**
 * List-safe avatar: Coil + URL only.
 *
 * Decoding `avatarBase64` with [android.graphics.BitmapFactory] inside
 * LazyColumn / grids pins full-size bitmaps on the heap and causes jank.
 * Callers should pass the remote URL (already cached by Coil).
 *
 * Pasando [userId] el avatar participa en la transición compartida
 * avatar → perfil: el mismo círculo viaja del feed (o del chat, o del
 * buscador) hasta la cabecera del perfil en vez de aparecer de golpe.
 * Si el destino no está dentro de un `SharedTransitionLayout`, el modificador
 * se ignora sin romper nada.
 */
@Composable
fun UserAvatar(
    imageUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = name,
    userId: String? = null
) {
    val sharedModifier = if (userId.isNullOrBlank()) {
        modifier
    } else {
        modifier.vividSharedElement(VividSharedKeys.avatar(userId))
    }
    val shapeModifier = sharedModifier.size(size).clip(CircleShape)
    val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    // Carga: bloque de superficie (nunca un círculo vacío); error de URL:
    // mismo fallback de letra que cuando no hay foto. Así el avatar se ve
    // "completo" desde el primer frame en listas con scroll rápido.
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl.isNotBlank() && !loadFailed) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = shapeModifier,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
            error = ColorPainter(MaterialTheme.colorScheme.surfaceContainerHigh),
            onError = { loadFailed = true }
        )
    } else {
        Box(
            modifier = shapeModifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                letter,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

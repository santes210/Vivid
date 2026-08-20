package com.vivid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = shapeModifier,
            contentScale = ContentScale.Crop
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

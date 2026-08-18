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
import coil.compose.AsyncImage

/**
 * List-safe avatar: Coil + URL only.
 *
 * Decoding `avatarBase64` with [android.graphics.BitmapFactory] inside
 * LazyColumn / grids pins full-size bitmaps on the heap and causes jank.
 * Callers should pass the remote URL (already cached by Coil).
 */
@Composable
fun UserAvatar(
    imageUrl: String,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    contentDescription: String? = name
) {
    val shapeModifier = modifier.size(size).clip(CircleShape)
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

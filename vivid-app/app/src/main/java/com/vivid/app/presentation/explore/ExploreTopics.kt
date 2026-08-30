package com.vivid.app.presentation.explore

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import com.vivid.app.data.paging.ExplorePaging

/**
 * Identidad visual de cada tema de Explorar.
 *
 * El tag en sí (la clave de Firestore) vive en [ExplorePaging.TAGS]. Aquí
 * solo está lo que Material 3 Expressive aporta: un icono y un rol de
 * color del esquema activo, para que los chips y la cabecera se pinten
 * con primary / secondary / tertiary container y convivan con Material You.
 */
object ExploreTopics {

    fun icon(tag: String): ImageVector = when (tag) {
        "vivid" -> Icons.Filled.AutoAwesome
        "arte" -> Icons.Filled.Palette
        "musica" -> Icons.Filled.MusicNote
        "viaje" -> Icons.Filled.Flight
        "comida" -> Icons.Filled.Restaurant
        "tecnologia" -> Icons.Filled.Memory
        "moda" -> Icons.Filled.Checkroom
        "deporte" -> Icons.Filled.SportsSoccer
        else -> Icons.Filled.Tag
    }

    fun colorIndex(tag: String): Int {
        val curated = ExplorePaging.TAGS.indexOf(tag)
        if (curated >= 0) return curated % 3
        return tag.hashCode().and(Int.MAX_VALUE) % 3
    }

    data class ContainerPair(val container: Color, val onContainer: Color)

    @Composable
    @ReadOnlyComposable
    fun containerPair(tag: String): ContainerPair {
        val scheme = MaterialTheme.colorScheme
        return containerPair(tag, scheme)
    }

    fun containerPair(tag: String, scheme: ColorScheme): ContainerPair = when (colorIndex(tag)) {
        0 -> ContainerPair(scheme.primaryContainer, scheme.onPrimaryContainer)
        1 -> ContainerPair(scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> ContainerPair(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

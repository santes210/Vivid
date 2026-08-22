package com.vivid.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.ui.components.VividAlertDialog
import com.vivid.app.ui.components.VividCelebrationIcon
import com.vivid.app.ui.components.VividDialogTone
import com.vivid.app.ui.components.VividEmptyState
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividLikeButton
import com.vivid.app.ui.components.VividLoadingState
import com.vivid.app.ui.components.VividOfflineBanner
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.ui.components.VividSnackbar
import com.vivid.app.ui.components.VividStoryRing
import com.vivid.app.ui.components.VividVerifiedBadge
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.TextButton

/**
 * Previews de los componentes compartidos.
 *
 * Todos son interactivos en el panel de Android Studio ("Run preview"), así
 * que el rebote del like o el tinte del avatar se pueden revisar sin instalar
 * la app.
 */

@VividPreviewA11y
@Composable
private fun LikeButtonPreview() {
    VividPreviewSurface {
        var liked by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            VividLikeButton(isLiked = liked, onToggle = { liked = !liked })
            Spacer(Modifier.width(VividSpace.xs))
            Text(
                if (liked) "Te gusta" else "Toca el corazón",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@VividPreview
@Composable
private fun AvatarsPreview() {
    VividPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(VividSpace.s)) {
            UserAvatar(imageUrl = "", name = "Ana", size = 40.dp)
            UserAvatar(imageUrl = "", name = "Bruno", size = 56.dp)
            UserAvatar(imageUrl = "", name = "", size = 56.dp)
        }
    }
}

@VividPreview
@Composable
private fun LoadingStatePreview() {
    VividPreviewSurface {
        VividLoadingState(message = "Cargando publicaciones…")
    }
}

@VividPreviewA11y
@Composable
private fun EmptyStatePreview() {
    VividPreviewSurface {
        VividEmptyState(
            icon = Icons.Outlined.PhotoCamera,
            title = "Todavía no hay nada por aquí",
            subtitle = "Cuando publiques tu primera foto aparecerá en tu perfil.",
            actionLabel = "Crear publicación",
            onAction = {}
        )
    }
}

@VividPreview
@Composable
private fun ErrorStatePreview() {
    VividPreviewSurface {
        VividErrorState(onRetry = {})
    }
}

@VividPreviewA11y
@Composable
private fun VerifiedBadgePreview() {
    VividPreviewSurface {
        // Badge de cuenta verificada con forma de gema (MaterialShapes.Gem).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ana García", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(VividSpace.xs))
            VividVerifiedBadge(size = 20.dp)
        }
    }
}

@VividPreviewA11y
@Composable
private fun StoryRingPreview() {
    VividPreviewSurface {
        // Izquierda: con historia nueva → anillo en trébol. Derecha: vista → círculo.
        Row(horizontalArrangement = Arrangement.spacedBy(VividSpace.l)) {
            VividStoryRing(hasUnseenStory = true) {
                UserAvatar(imageUrl = "", name = "Nueva", size = 56.dp)
            }
            VividStoryRing(hasUnseenStory = false) {
                UserAvatar(imageUrl = "", name = "Vista", size = 56.dp)
            }
        }
    }
}

@VividPreview
@Composable
private fun ProfileTabsButtonGroupPreview() {
    VividPreviewSurface {
        // Mismo patrón que las pestañas del perfil (Posts/Reels/Guardados):
        // ButtonGroup single-select con toggleableItem, animado con el
        // MotionScheme. Interactivo en el panel de previews.
        var selected by remember { mutableIntStateOf(0) }
        val tabs = listOf("Posts" to Icons.Filled.GridView, "Reels" to Icons.Filled.Movie, "Guardados" to Icons.Filled.Bookmark)
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            overflowIndicator = {}
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                toggleableItem(
                    weight = 1f,
                    checked = selected == index,
                    onCheckedChange = { selected = index },
                    label = label,
                    icon = { Icon(icon, contentDescription = label) }
                )
            }
        }
    }
}

@VividPreview
@Composable
private fun SnackbarPreview() {
    VividPreviewSurface(padding = 0) {
        VividSnackbar(snackbarData = PreviewSnackbarData("Publicación guardada"))
    }
}

@VividPreview
@Composable
private fun SuccessDialogPreview() {
    VividPreviewSurface {
        VividAlertDialog(
            onDismissRequest = {},
            title = { Text("Publicado") },
            text = { Text("Tu publicación ya está en el feed.") },
            confirmButton = { TextButton(onClick = {}) { Text("Listo") } },
            tone = VividDialogTone.Success
        )
    }
}

@VividPreview
@Composable
private fun CelebrationIconPreview() {
    VividPreviewSurface {
        VividCelebrationIcon(size = 72.dp)
    }
}

@VividPreview
@Composable
private fun OfflineBannerPreview() {
    VividPreviewSurface(padding = 0) {
        VividOfflineBanner()
    }
}

@VividPreview
@Composable
private fun SkeletonPreview() {
    VividPreviewSurface(padding = 0) {
        Column {
            repeat(3) { VividSkeletonListItem() }
        }
    }
}

/**
 * Muestrario de la paleta: sirve para revisar de un vistazo que los pares
 * `container` / `on…` tienen contraste suficiente en claro y en oscuro.
 */
@VividPreview
@Composable
private fun BrandPalettePreview() {
    VividPreviewSurface {
        val scheme = MaterialTheme.colorScheme
        val accents = LocalVividAccents.current
        Column(verticalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
            SwatchRow("primary", scheme.primary, scheme.onPrimary)
            SwatchRow("primaryContainer", scheme.primaryContainer, scheme.onPrimaryContainer)
            SwatchRow("secondaryContainer", scheme.secondaryContainer, scheme.onSecondaryContainer)
            SwatchRow("tertiaryContainer", scheme.tertiaryContainer, scheme.onTertiaryContainer)
            SwatchRow("errorContainer", scheme.errorContainer, scheme.onErrorContainer)
            SwatchRow("surfaceContainer", scheme.surfaceContainer, scheme.onSurface)
            SwatchRow("accent · like", accents.like, Color.White)
            SwatchRow("accent · verificado", accents.verified, Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
                accents.storyRing.forEach { ringColor ->
                    Surface(
                        color = ringColor,
                        shape = VividMaterialShapes.Celebration,
                        modifier = Modifier.size(44.dp)
                    ) {}
                }
            }
        }
    }
}

private class PreviewSnackbarData(
    private val message: String
) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message: String = this@PreviewSnackbarData.message
        override val actionLabel: String? = null
        override val withDismissAction: Boolean = false
        override val duration: SnackbarDuration = SnackbarDuration.Short
    }

    override fun performAction() = Unit
    override fun dismiss() = Unit
}

@Composable
private fun SwatchRow(name: String, container: Color, onContainer: Color) {
    Surface(
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(Modifier.width(VividSpace.s))
            Text(
                name,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

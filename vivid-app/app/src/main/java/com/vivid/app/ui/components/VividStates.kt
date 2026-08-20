package com.vivid.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividShapes

/**
 * Estados unificados de Vivid (cargando / vacío / error / esqueleto).
 *
 * Usan componentes reales de Material 3 Expressive:
 *   - `LoadingIndicator` y `ContainedLoadingIndicator`, que muestran una
 *     secuencia de polígonos que se transforman entre sí. Material los
 *     recomienda sobre `CircularProgressIndicator` para esperas cortas
 *     (< 5 s), que es el 95 % de lo que hace esta app.
 *   - `MaterialShapes` para el contenedor del estado vacío
 *     ([VividMaterialShapes.EmptyStateContainer]).
 */

@Composable
fun VividLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Cargando…",
    showMessage: Boolean = true
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(modifier = Modifier.size(48.dp))
            if (showMessage) {
                Spacer(Modifier.height(16.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun VividLoadingFullScreen(message: String = "Cargando…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { VividLoadingState(message = message) }
}

@Composable
fun VividEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Squircle expresivo 32dp hero + tonal primaryContainer
            Surface(
                // Polígono real de MaterialShapes, no un cuadrado redondeado.
                shape = VividMaterialShapes.EmptyStateContainer,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction, shape = VividExpressiveShapes.PrimaryButton) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun VividErrorState(
    modifier: Modifier = Modifier,
    title: String = "No se pudo cargar",
    message: String = "Comprueba tu conexión e inténtalo de nuevo.",
    icon: ImageVector = Icons.Default.CloudOff,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Reintentar"
) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(18.dp))
                VividRetryButton(onRetry = onRetry, label = retryLabel)
            }
        }
    }
}

@Composable
fun VividRetryButton(onRetry: () -> Unit, label: String = "Reintentar", modifier: Modifier = Modifier) {
    Button(onClick = onRetry, shape = VividExpressiveShapes.PrimaryButton, modifier = modifier) {
        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
}

@Composable
fun VividOfflineBanner(modifier: Modifier = Modifier, message: String = "Sin conexión · Algunos contenidos pueden no estar actualizados") {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = VividShapes.small, // 12dp squircle
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WifiOff, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        }
    }
}

// Skeletons con formas expresivas — medium 16dp para líneas, circle para avatar
@Composable
fun VividSkeleton(modifier: Modifier = Modifier) {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val alpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "vividSkeleton")
        val animatedAlpha by transition.animateFloat(
            0.35f,
            0.75f,
            infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "alpha"
        )
        animatedAlpha
    } else {
        0.45f
    }
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Box(modifier = modifier.clip(VividShapes.medium).background(blockColor))
}

@Composable
fun VividSkeletonListItem(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        VividSkeleton(modifier = Modifier.size(48.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VividSkeleton(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp).clip(VividShapes.small))
            VividSkeleton(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp).clip(VividShapes.small))
        }
    }
}

@Composable
fun VividSkeletonGrid(columns: Int = 3, count: Int = 9) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat((count + columns - 1) / columns) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns) { col ->
                    val idx = row * columns + col
                    if (idx < count) Box(Modifier.weight(1f).aspectRatio(1f).clip(VividShapes.small)) { VividSkeleton(Modifier.fillMaxSize()) }
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

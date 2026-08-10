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

/**
 * Estados unificados Material You 3 Expressive.
 * Cada pantalla resuelve carga/vacío/error de la misma forma.
 */

// ─────────────────────────────────────────────────────────────
//  Loading
// ─────────────────────────────────────────────────────────────
@Composable
fun VividLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Cargando…",
    showMessage: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
            if (showMessage) {
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VividLoadingFullScreen(
    message: String = "Cargando…"
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        VividLoadingState(message = message)
    }
}

// ─────────────────────────────────────────────────────────────
//  Empty
// ─────────────────────────────────────────────────────────────
@Composable
fun VividEmptyState(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Error
// ─────────────────────────────────────────────────────────────
@Composable
fun VividErrorState(
    modifier: Modifier = Modifier,
    title: String = "No se pudo cargar",
    message: String = "Comprueba tu conexión e inténtalo de nuevo.",
    icon: ImageVector = Icons.Default.CloudOff,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Reintentar"
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(Modifier.height(18.dp))
                VividRetryButton(onRetry = onRetry, label = retryLabel)
            }
        }
    }
}

@Composable
fun VividRetryButton(
    onRetry: () -> Unit,
    label: String = "Reintentar",
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onRetry,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ─────────────────────────────────────────────────────────────
//  Offline banner
// ─────────────────────────────────────────────────────────────
@Composable
fun VividOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = "Sin conexión · Algunos contenidos pueden no estar actualizados"
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Skeleton (shimmer tonale)
// ─────────────────────────────────────────────────────────────
@Composable
fun VividSkeleton(
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val transition = rememberInfiniteTransition(label = "vividSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest
        .copy(alpha = if (animationsEnabled) alpha else 0.45f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(blockColor)
    )
}

/**
 * Skeleton genérico para lista: avatar + líneas.
 * Útil para feed, chats, búsqueda.
 */
@Composable
fun VividSkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VividSkeleton(modifier = Modifier.size(48.dp).clip(CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VividSkeleton(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            VividSkeleton(modifier = Modifier.fillMaxWidth(0.85f).height(12.dp))
        }
    }
}

@Composable
fun VividSkeletonGrid(
    columns: Int = 3,
    count: Int = 9
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat((count + columns - 1) / columns) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns) { col ->
                    val idx = row * columns + col
                    if (idx < count) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            VividSkeleton(modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

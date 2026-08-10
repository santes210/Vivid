package com.vivid.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Componentes tonales Material You 3 Expressive para Ajustes.
 * - TopAppBar compacta
 * - Grupos con contenedor tonal (surfaceContainerLow)
 * - Divisores solo cuando aportan claridad
 * - Descripciones cortas y valor actual a la derecha
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VividSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = snackbarHost,
        content = content
    )
}

@Composable
fun VividSettingsGroup(
    title: String? = null,
    showDivider: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 4.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
        if (showDivider) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun VividSettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    showDivider: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val hasTrailing = trailing != null || value != null
    Column {
        ListItem(
            headlineContent = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            },
            leadingContent = icon?.let {
                {
                    Icon(
                        it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingContent = when {
                trailing != null -> trailing
                value != null -> {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                value,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                onClick != null -> {
                    {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                else -> null
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun VividSettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = false
) {
    Column {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            },
            supportingContent = subtitle?.let {
                { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)) }
            },
            leadingContent = icon?.let {
                { Icon(it, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

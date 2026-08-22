package com.vivid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.vivid.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividShapes
import com.vivid.app.theme.VividSpace

/**
 * Material You 3 Expressive — Ajustes con formas chidas.
 * Usa roles tonales + formas expresivas (squircle 20→28dp) para jerarquía,
 * no “tarjetas dentro de tarjetas” planas.
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
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
                modifier = Modifier.padding(start = VividSpace.m, bottom = VividSpace.xs, top = VividSpace.xxs)
            )
        }
        // Forma expresiva HeroCard (28dp) con tonal surfaceContainerLow — jerarquía clara
        Surface(
            shape = VividExpressiveShapes.HeroCard, // 28dp expressive
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
        if (showDivider) {
            Spacer(Modifier.height(VividSpace.xs))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = VividSpace.m)
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
    Column {
        ListItem(
            headlineContent = {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            },
            supportingContent = subtitle?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingContent = icon?.let {
                {
                    // Contenedor tonal expresivo tipo squircle 16dp — M3 Expressive “formas con propósito”
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(VividExpressiveShapes.SmallCard) // 16dp squircle
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            trailingContent = when {
                trailing != null -> trailing
                value != null -> {
                    {
                        Surface(
                            shape = VividShapes.small, // 12dp pill tonal
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.padding(start = VividSpace.xs)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(VividSpace.xxs))
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                onClick != null -> {
                    {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                else -> null
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(VividShapes.medium) // ripple recortado a 16dp squircle
                .clickable(
                    enabled = onClick != null,
                    role = Role.Button,
                    onClick = { onClick?.invoke() }
                ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        // Divisor solo cuando aporta claridad — grosor mínimo y alpha bajo
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                modifier = Modifier.padding(horizontal = VividSpace.m)
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
                { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            leadingContent = icon?.let {
                {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(VividExpressiveShapes.SmallCard)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(it, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
            },
            trailingContent = {
                // La fila completa es el único control para evitar dos focos de TalkBack.
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(VividShapes.medium)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = if (checked) "Activado" else "Desactivado"
                },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f),
                modifier = Modifier.padding(horizontal = VividSpace.m)
            )
        }
    }
}

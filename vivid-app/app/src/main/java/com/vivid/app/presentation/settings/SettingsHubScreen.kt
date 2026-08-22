package com.vivid.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.ui.components.VividSettingsGroup
import com.vivid.app.ui.components.VividSettingsItem
import com.vivid.app.ui.components.VividSettingsScaffold

data class SettingsHubEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val value: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onNavigateCuenta: () -> Unit,
    onNavigatePrivacidad: () -> Unit,
    onNavigateApariencia: () -> Unit,
    onNavigateContenido: () -> Unit,
    onNavigateNotificaciones: () -> Unit,
    onNavigateAlmacenamiento: () -> Unit,
    onNavigateAyuda: () -> Unit,
    onNavigateAcerca: () -> Unit,
    appearanceValue: String,
    contenidoValue: String = "",
    notifValue: String = "",
    storageValue: String = ""
) {
    // Muestra el tamaño real del caché en el hub (calculado en background)
    val context = LocalContext.current
    val appContext = context.applicationContext
    var realCacheMB by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        realCacheMB = runCatching { com.vivid.app.util.VividCacheManager.calculateCacheSizeMB(appContext) }
            .getOrDefault(0f)
    }
    val effectiveStorageValue = if (realCacheMB > 0f) String.format("%.1f MB", realCacheMB) else null
    VividSettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(
                        title = "Cuenta",
                        subtitle = "Contraseña, datos personales y verificación",
                        icon = Icons.Outlined.Person,
                        onClick = onNavigateCuenta,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Privacidad",
                        subtitle = "Cuenta privada, actividad y bloqueo",
                        icon = Icons.Outlined.Lock,
                        onClick = onNavigatePrivacidad,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Apariencia",
                        subtitle = "Tema, idioma y tamaño de texto",
                        icon = Icons.Outlined.Palette,
                        value = appearanceValue,
                        onClick = onNavigateApariencia,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Contenido y multimedia",
                        subtitle = "Reproducción, calidad y filtros",
                        icon = Icons.Outlined.VideoLibrary,
                        onClick = onNavigateContenido,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Notificaciones",
                        subtitle = "Likes, seguidores y mensajes",
                        icon = Icons.Outlined.Notifications,
                        onClick = onNavigateNotificaciones,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Almacenamiento",
                        subtitle = "Caché y calidad de descarga",
                        icon = Icons.Outlined.Storage,
                        value = effectiveStorageValue,
                        onClick = onNavigateAlmacenamiento,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Ayuda",
                        subtitle = "Soporte y contacto",
                        icon = Icons.Outlined.HelpOutline,
                        onClick = onNavigateAyuda,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Acerca de",
                        subtitle = "Versión, términos y privacidad",
                        icon = Icons.Outlined.Info,
                        onClick = onNavigateAcerca
                    )
                }
            }
            item {
                Text(
                    "Vivid · Material You 3 Expressive",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

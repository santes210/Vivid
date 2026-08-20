package com.vivid.app.ui.preview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vivid.app.ui.components.VividSettingsGroup
import com.vivid.app.ui.components.VividSettingsItem
import com.vivid.app.ui.components.VividSettingsSwitchItem

/**
 * Previews del grupo de Ajustes.
 *
 * Sirven sobre todo para vigilar dos cosas que se rompen solas: los subtítulos
 * largos con fuente al 150 % y el contraste de los contenedores tonales de los
 * iconos en tema oscuro.
 */
@VividPreviewA11y
@Composable
private fun SettingsGroupPreview() {
    VividPreviewSurface(padding = 8) {
        var dynamicColor by remember { mutableStateOf(true) }
        var haptics by remember { mutableStateOf(true) }
        VividSettingsGroup(title = "Apariencia") {
            VividSettingsItem(
                title = "Tema",
                subtitle = "Claro, oscuro o sistema",
                icon = Icons.Outlined.Palette,
                value = "Sistema",
                onClick = {},
                showDivider = true
            )
            VividSettingsSwitchItem(
                title = "Color dinámico (Material You)",
                subtitle = "Del fondo de pantalla · Android 12+",
                icon = Icons.Outlined.ColorLens,
                checked = dynamicColor,
                onCheckedChange = { dynamicColor = it },
                showDivider = true
            )
            VividSettingsSwitchItem(
                title = "Respuesta háptica",
                subtitle = "Vibración breve al dar like, seguir o cambiar de pestaña",
                icon = Icons.Outlined.Vibration,
                checked = haptics,
                onCheckedChange = { haptics = it },
                showDivider = true
            )
            VividSettingsItem(
                title = "Idioma",
                subtitle = "Español / English",
                icon = Icons.Outlined.Translate,
                value = "Sistema",
                onClick = {}
            )
        }
    }
}

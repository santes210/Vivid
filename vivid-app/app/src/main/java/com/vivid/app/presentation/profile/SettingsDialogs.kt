package com.vivid.app.presentation.profile

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.composeEmail

// ── Help / Support Dialog ──

@Composable
internal fun HelpDialog(
    context: Context,
    onDismiss: () -> Unit,
    onEmailFailed: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Centro de Ayuda",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text("¿Tienes algún problema con Vivid? Estamos aquí para ayudarte con soporte directo.")
                Spacer(Modifier.height(16.dp))
                Text(
                    "Puedes contactar directamente al desarrollador enviando un correo a:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "poncho2010santes@gmail.com",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val opened = composeEmail(
                    context = context,
                    to = "poncho2010santes@gmail.com",
                    subject = "Soporte Vivid App M3"
                )
                if (!opened) onEmailFailed()
                onDismiss()
            }) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enviar Correo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

// ── Theme Picker Dialog ──

@Composable
internal fun ThemeDialog(
    context: Context,
    selectedThemeOption: String,
    onDismiss: () -> Unit,
    onThemeChanged: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.theme_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                SettingsManager.themeOptions.forEach { themeOption ->
                    // La clave canónica ("system"/"dark"/"light") se persiste;
                    // al usuario solo se le muestra la etiqueta localizada.
                    val label = stringResource(SettingsManager.themeOptionLabelRes(themeOption))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SettingsManager.setThemeOption(context, themeOption)
                                onThemeChanged(label)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedThemeOption == themeOption,
                            onClick = {
                                SettingsManager.setThemeOption(context, themeOption)
                                onThemeChanged(label)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

// ── Download Quality Dialog ──

@Composable
internal fun DownloadQualityDialog(
    context: Context,
    downloadQualityOption: String,
    onDismiss: () -> Unit,
    onQualityChanged: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Calidad de descarga",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                listOf("Alta (HD)", "Media (Equilibrada)", "Baja (Ahorro de datos)").forEach { qualityOption ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SettingsManager.setDownloadQuality(context, qualityOption)
                                onQualityChanged(qualityOption)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = downloadQualityOption == qualityOption,
                            onClick = {
                                SettingsManager.setDownloadQuality(context, qualityOption)
                                onQualityChanged(qualityOption)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(qualityOption, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

// ── Info Dialog (generic) ──

@Composable
internal fun InfoDialog(
    dialog: SettingsInfoDialog,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                dialog.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = { Text(dialog.message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

// ── Sign Out Dialog ──

@Composable
internal fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "¿Cerrar sesión?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = { Text("Tendrás que volver a iniciar sesión para entrar a Vivid.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cerrar Sesión", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp
    )
}

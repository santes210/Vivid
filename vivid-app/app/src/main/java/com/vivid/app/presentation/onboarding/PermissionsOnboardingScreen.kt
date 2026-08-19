package com.vivid.app.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vivid.app.R
import com.vivid.app.ui.components.VividSettingsScaffold

/**
 * Onboarding de permisos.
 *
 * Por qué esta pantalla existe: en Android 12+ pedir un permiso sin contexto
 * confunde al usuario y dispara el "no permitir" automático. Antes de
 * invocar el system dialog explicamos para qué se usa cada permiso y le
 * damos al usuario la opción de saltar.
 *
 * Flujo:
 *  1. Mostrar esta pantalla con descripciones claras de cámara, micrófono
 *     y notificaciones.
 *  2. Al pulsar "Continuar" se piden los permisos en orden (cámara →
 *     micrófono → notificaciones). El usuario puede denegar cualquiera sin
 *     romper el resto de la app.
 *  3. Al finalizar, [onComplete] se llama para que MainActivity cambie
 *     a la pantalla principal. La marca "ya pasó" se guarda en
 *     SharedPreferences para no volver a mostrarla.
 */
@Composable
fun PermissionsOnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current

    // Estado de cada permiso. true = granted.
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // POST_NOTIFICATIONS solo existe desde Android 13.
    val needsNotificationPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var notifGranted by remember {
        mutableStateOf(
            !needsNotificationPerm ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted
    }

    fun openAppSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    VividSettingsScaffold(
        title = stringResource(R.string.perm_onb_title),
        onBack = onSkip
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.perm_onb_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            PermissionCard(
                icon = Icons.Filled.CameraAlt,
                title = stringResource(R.string.perm_onb_camera_title),
                body = stringResource(R.string.perm_onb_camera_body),
                granted = cameraGranted
            )
            PermissionCard(
                icon = Icons.Filled.Mic,
                title = stringResource(R.string.perm_onb_mic_title),
                body = stringResource(R.string.perm_onb_mic_body),
                granted = micGranted
            )
            if (needsNotificationPerm) {
                PermissionCard(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.perm_onb_notif_title),
                    body = stringResource(R.string.perm_onb_notif_body),
                    granted = notifGranted
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Botón "Pedir" por cada permiso pendiente ──
            if (!cameraGranted) {
                FilledTonalButton(
                    onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir cámara")
                }
            }
            if (!micGranted) {
                FilledTonalButton(
                    onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir micrófono")
                }
            }
            if (needsNotificationPerm && !notifGranted) {
                FilledTonalButton(
                    onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir notificaciones")
                }
            }

            // ── Ayuda si el usuario denegó permanentemente ──
            if ((!cameraGranted || !micGranted || (needsNotificationPerm && !notifGranted))) {
                TextButton(
                    onClick = { openAppSettings() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Abrir ajustes del sistema")
                }
            }

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.perm_onb_continue))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.perm_onb_skip))
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (granted)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    if (granted) {
                        Text(
                            "✓",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

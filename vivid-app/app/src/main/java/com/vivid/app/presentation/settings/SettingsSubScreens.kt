package com.vivid.app.presentation.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.BuildConfig
import com.vivid.app.domain.repository.setAccountContentPrivacy
import com.vivid.app.presentation.stories.deleteExpiredStoriesForCurrentUser
import com.vivid.app.ui.components.VividSettingsGroup
import com.vivid.app.ui.components.VividSettingsItem
import com.vivid.app.ui.components.VividSettingsScaffold
import com.vivid.app.ui.components.VividSettingsSwitchItem
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.VividCacheManager
import com.vivid.app.util.VideoCacheManager
import com.vivid.app.util.VividChangelog
import com.vivid.app.util.composeEmail
import com.vivid.app.util.launchExternalIntent
import com.vivid.app.util.openUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividSeedPalette
import com.vivid.app.ui.components.VividAlertDialog

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format("%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

// ─────────────────────────────────────────────────────────────
// Cuenta
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaSettingsScreen(
    onBack: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("vivid_user") }
    var displayName by remember { mutableStateOf("Usuario Vivid") }
    var isPrivate by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<Pair<String,String>?>(null) }

    LaunchedEffect(user?.uid) {
        runCatching { FirebaseFirestore.getInstance().collection("users").document(user!!.uid).get().await() }
            .onSuccess { snap ->
                username = snap.getString("username") ?: username
                displayName = snap.getString("displayName") ?: displayName
                isPrivate = snap.getBoolean("isPrivate") ?: false
            }
    }

    VividSettingsScaffold(title = "Cuenta", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup(title = "Información personal") {
                    VividSettingsItem(
                        title = "Centro de Cuentas",
                        subtitle = "$displayName · @$username",
                        icon = Icons.Outlined.Person,
                        value = user?.email?.take(18) ?: "Ver",
                        onClick = {
                            infoDialog = "Centro de Cuentas" to buildString {
                                appendLine("Nombre: $displayName")
                                appendLine("Usuario: @$username")
                                appendLine("Correo: ${user?.email ?: "No disponible"}")
                                appendLine("UID: ${user?.uid ?: "Sin sesión"}")
                                appendLine()
                                append(if (user?.isEmailVerified == true) "Correo verificado." else "Correo no verificado.")
                            }
                        },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Verificar correo",
                        subtitle = if (user?.isEmailVerified == true) "Verificado" else "Enviar verificación",
                        icon = Icons.Outlined.MarkEmailRead,
                        onClick = {
                            scope.launch {
                                if (user == null) onShowSnackbar("No hay sesión")
                                else if (user.isEmailVerified) onShowSnackbar("Ya está verificado")
                                else runCatching { user.sendEmailVerification().await() }
                                    .onSuccess { onShowSnackbar("Correo enviado") }
                                    .onFailure { onShowSnackbar(it.message ?: "Error") }
                            }
                        },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Cambiar contraseña",
                        subtitle = "Enviar enlace al correo",
                        icon = Icons.Outlined.Password,
                        onClick = {
                            scope.launch {
                                val email = user?.email.orEmpty()
                                if (email.isBlank()) onShowSnackbar("Sin correo")
                                else runCatching { auth.sendPasswordResetEmail(email).await() }
                                    .onSuccess { onShowSnackbar("Enlace enviado a $email") }
                                    .onFailure { onShowSnackbar(it.message ?: "Error") }
                            }
                        }
                    )
                }
            }
            item {
                VividSettingsGroup(title = "Datos rápidos") {
                    VividSettingsItem(
                        title = "Copiar usuario",
                        subtitle = "@$username",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            clipboard.setText(AnnotatedString("@$username"))
                            scope.launch { onShowSnackbar("Usuario copiado") }
                        },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Copiar correo",
                        subtitle = user?.email ?: "No disponible",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            val email = user?.email.orEmpty()
                            if (email.isBlank()) scope.launch { onShowSnackbar("Sin correo") }
                            else { clipboard.setText(AnnotatedString(email)); scope.launch { onShowSnackbar("Correo copiado") } }
                        },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Copiar UID",
                        subtitle = "Para soporte",
                        icon = Icons.Outlined.PermIdentity,
                        onClick = {
                            val uid = user?.uid.orEmpty()
                            if (uid.isBlank()) scope.launch { onShowSnackbar("Sin UID") }
                            else { clipboard.setText(AnnotatedString(uid)); scope.launch { onShowSnackbar("UID copiado") } }
                        }
                    )
                }
            }
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = VividExpressiveShapes.MediumCard
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(com.vivid.app.R.string.action_logout), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
                        supportingContent = { Text("@$username", color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp)) },
                        modifier = Modifier.clickable {
                            com.vivid.app.util.PushNotificationHelper.unregisterToken()
                            // Credential Manager cachea la cuenta usada: sin
                            // limpiarla, el próximo login la reutilizaría.
                            com.vivid.app.presentation.auth.GoogleCredentialSignIn
                                .clearCredentialState(context)
                            auth.signOut(); onBack()
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
    infoDialog?.let { (t,m) ->
        VividAlertDialog(onDismissRequest = { infoDialog = null }, title = { Text(t, fontWeight = FontWeight.Bold) }, text = { Text(m) }, confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("Entendido") } }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

// ─────────────────────────────────────────────────────────────
// Privacidad
// ─────────────────────────────────────────────────────────────
@Composable
fun PrivacidadSettingsScreen(
    onBack: () -> Unit,
    onOpenCloseFriends: () -> Unit,
    onOpenBlocked: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    var isPrivate by remember { mutableStateOf(false) }
    var closeFriendsCount by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    var infoDialog by remember { mutableStateOf<Pair<String,String>?>(null) }
    val activityEnabled = SettingsManager.activityStatusEnabled
    val twoFA = SettingsManager.twoFactorAuthEnabled
    val scope = rememberCoroutineScope()

    LaunchedEffect(user?.uid) {
        runCatching { firestore.collection("users").document(user!!.uid).get().await() }
            .onSuccess { snap ->
                isPrivate = snap.getBoolean("isPrivate") ?: false
                closeFriendsCount = (snap.get("closeFriends") as? List<*>)?.size ?: 0
                blockedCount = (snap.get("blockedUsers") as? List<*>)?.size ?: 0
            }
    }

    VividSettingsScaffold(title = "Privacidad", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup(title = "Visibilidad") {
                    VividSettingsSwitchItem(
                        title = stringResource(com.vivid.app.R.string.account_private),
                        subtitle = if (isPrivate) "Solo seguidores ven tu contenido" else stringResource(com.vivid.app.R.string.account_public),
                        icon = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                        checked = isPrivate,
                        onCheckedChange = { checked ->
                            isPrivate = checked
                            user?.uid?.let { uid ->
                                scope.launch {
                                    runCatching { setAccountContentPrivacy(firestore, uid, checked) }
                                        .onSuccess {
                                            onShowSnackbar(
                                                context.getString(
                                                    if (checked) com.vivid.app.R.string.account_private
                                                    else com.vivid.app.R.string.account_public
                                                )
                                            )
                                        }
                                        .onFailure {
                                            isPrivate = !checked
                                            onShowSnackbar("No se pudo cambiar la privacidad")
                                        }
                                }
                            }
                        },
                        showDivider = true
                    )
                    VividSettingsSwitchItem(
                        title = "Estado de actividad",
                        subtitle = if (activityEnabled) "Mostrando cuándo estás activo" else "Oculto",
                        icon = Icons.Outlined.Visibility,
                        checked = activityEnabled,
                        onCheckedChange = { checked ->
                            SettingsManager.setActivityStatus(context, checked)
                            user?.uid?.let { firestore.collection("users").document(it).update("activityStatusEnabled", checked) }
                        },
                        showDivider = true
                    )
                    VividSettingsSwitchItem(
                        title = "Autenticación en 2 pasos",
                        subtitle = if (twoFA) "Activada" else "Recomendado",
                        icon = Icons.Outlined.Security,
                        checked = twoFA,
                        onCheckedChange = { checked ->
                            SettingsManager.set2FA(context, checked)
                            scope.launch { onShowSnackbar(if (checked) "2FA activada" else "2FA desactivada") }
                        }
                    )
                }
            }
            item {
                VividSettingsGroup(title = "Conexiones") {
                    VividSettingsItem(
                        title = "Mejores amigos",
                        subtitle = "$closeFriendsCount personas",
                        icon = Icons.Outlined.Group,
                        onClick = onOpenCloseFriends,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Usuarios bloqueados",
                        subtitle = "$blockedCount cuentas",
                        icon = Icons.Outlined.Block,
                        onClick = onOpenBlocked,
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Cuentas silenciadas",
                        subtitle = "Gestiona qué no quieres ver",
                        icon = Icons.Outlined.VolumeOff,
                        onClick = { infoDialog = "Silenciadas" to "No tienes cuentas silenciadas. Puedes silenciar desde el perfil de un usuario." }
                    )
                }
            }
        }
    }
    infoDialog?.let { (t,m) ->
        VividAlertDialog(onDismissRequest = { infoDialog = null }, title = { Text(t, fontWeight = FontWeight.Bold) }, text = { Text(m) }, confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("Entendido") } }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
}

// ─────────────────────────────────────────────────────────────
// Apariencia
// ─────────────────────────────────────────────────────────────
@Composable
fun AparienciaSettingsScreen(
    onBack: () -> Unit,
    onShowSnackbar: suspend (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val selectedTheme = SettingsManager.selectedThemeOption
    val dynamic = SettingsManager.dynamicColorEnabled
    val seedPalette = VividSeedPalette.fromId(SettingsManager.seedPaletteId)
    // El wallpaper solo manda si el dispositivo tiene Material You: en
    // Android 11 e inferior el interruptor puede estar activado y la semilla
    // seguir siendo la que decide, así que el selector no debe atenuarse.
    val dynamicApplies = dynamic &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val amoled = SettingsManager.amoledBlackEnabled
    // El negro puro solo se ve si el tema oscuro está realmente activo; el
    // switch se deja usable igualmente (preferencia, no estado) pero el
    // subtítulo lo explica en vez de mentir.
    val isDarkActive = when (selectedTheme) {
        SettingsManager.THEME_DARK -> true
        SettingsManager.THEME_LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    val smooth = SettingsManager.smoothAnimationsEnabled
    val haptic = SettingsManager.hapticFeedbackEnabled
    val haptics = rememberVividHaptics()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Re-suscribirse a los valores del LocaleManager para reflejar el estado
    // actual y que la UI se reescale si el usuario cambia el tamaño.
    val selectedLang = com.vivid.app.util.LocaleManager.selectedLang
    val fontScale = com.vivid.app.util.LocaleManager.fontScale

    // Etiquetas localizadas para los selectores de idioma y tamaño de fuente.
    // (No traducimos los textos hardcoded del resto del archivo: eso queda
    // fuera del alcance; los selectores nuevos sí porque son del feature.)
    val langLabel = when (selectedLang) {
        "en" -> stringResource(com.vivid.app.R.string.lang_english)
        "es" -> stringResource(com.vivid.app.R.string.lang_spanish)
        else -> stringResource(com.vivid.app.R.string.lang_system)
    }
    val fontLabel = when {
        fontScale <= 0.9f -> stringResource(com.vivid.app.R.string.font_size_small)
        fontScale <= 1.05f -> stringResource(com.vivid.app.R.string.font_size_normal)
        fontScale <= 1.2f -> stringResource(com.vivid.app.R.string.font_size_large)
        else -> stringResource(com.vivid.app.R.string.font_size_xlarge)
    }

    VividSettingsScaffold(title = "Apariencia", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(
                        title = stringResource(com.vivid.app.R.string.theme_title),
                        subtitle = stringResource(com.vivid.app.R.string.theme_subtitle),
                        icon = Icons.Outlined.Palette,
                        value = stringResource(SettingsManager.themeOptionLabelRes(selectedTheme)),
                        onClick = { showThemeDialog = true },
                        showDivider = true
                    )
                    VividSettingsSwitchItem(
                        title = "Color dinámico (Material You)",
                        subtitle = "Del fondo de pantalla · Android 12+",
                        icon = Icons.Outlined.ColorLens,
                        checked = dynamic,
                        onCheckedChange = { checked ->
                            SettingsManager.setDynamicColor(context, checked)
                            scope.launch { onShowSnackbar(if (checked) "Material You activado" else "Paleta Vivid clásica") }
                        },
                        showDivider = true
                    )
                    // Semillas de marca. Solo mandan cuando Material You está
                    // apagado: con wallpaper el esquema lo genera el sistema.
                    VividSeedPaletteSelector(
                        selected = seedPalette,
                        enabled = !dynamicApplies,
                        onSelect = { palette ->
                            SettingsManager.setSeedPalette(context, palette.id)
                            haptics.confirm()
                            val label = context.getString(palette.labelRes)
                            scope.launch {
                                onShowSnackbar(
                                    context.getString(
                                        com.vivid.app.R.string.seed_palette_changed,
                                        label
                                    )
                                )
                            }
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    VividSettingsSwitchItem(
                        title = stringResource(com.vivid.app.R.string.amoled_title),
                        subtitle = when {
                            !isDarkActive ->
                                stringResource(com.vivid.app.R.string.amoled_needs_dark)
                            amoled -> stringResource(com.vivid.app.R.string.amoled_subtitle_on)
                            else -> stringResource(com.vivid.app.R.string.amoled_subtitle_off)
                        },
                        icon = Icons.Outlined.Contrast,
                        checked = amoled,
                        onCheckedChange = { checked ->
                            SettingsManager.setAmoledBlack(context, checked)
                            haptics.confirm()
                            scope.launch {
                                onShowSnackbar(
                                    context.getString(
                                        if (checked) com.vivid.app.R.string.amoled_enabled
                                        else com.vivid.app.R.string.amoled_disabled
                                    )
                                )
                            }
                        },
                        showDivider = true
                    )
                    VividSettingsSwitchItem(
                        title = "Animaciones suaves",
                        subtitle = if (smooth) "Transiciones activadas" else "Movimiento reducido",
                        icon = Icons.Outlined.Animation,
                        checked = smooth,
                        onCheckedChange = { checked ->
                            SettingsManager.setSmoothAnimations(context, checked)
                            scope.launch { onShowSnackbar(if (checked) "Animaciones activadas" else "Movimiento reducido") }
                        },
                        showDivider = true
                    )
                    VividSettingsSwitchItem(
                        title = stringResource(com.vivid.app.R.string.settings_haptics_title),
                        subtitle = stringResource(com.vivid.app.R.string.settings_haptics_desc),
                        icon = Icons.Outlined.Vibration,
                        checked = haptic,
                        onCheckedChange = { checked ->
                            SettingsManager.setHapticFeedback(context, checked)
                            if (checked) haptics.confirm()
                            scope.launch {
                                onShowSnackbar(
                                    if (checked) "Respuesta háptica activada"
                                    else "Respuesta háptica desactivada"
                                )
                            }
                        }
                    )
                }
            }
            item {
                VividSettingsGroup(title = "Idioma y texto") {
                    VividSettingsItem(
                        title = stringResource(com.vivid.app.R.string.lang_setting),
                        subtitle = "Español / English",
                        icon = Icons.Outlined.Translate,
                        value = langLabel,
                        onClick = { showLangDialog = true },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = stringResource(com.vivid.app.R.string.font_size_setting),
                        subtitle = stringResource(com.vivid.app.R.string.font_size_subtitle),
                        icon = Icons.Outlined.TextFields,
                        value = fontLabel,
                        onClick = { showFontDialog = true }
                    )
                }
            }
        }
    }
    if (showThemeDialog) {
        VividAlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(com.vivid.app.R.string.theme_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    SettingsManager.themeOptions.forEach { opt ->
                        // Se persiste la clave canónica; la etiqueta visible es localizada.
                        val label = stringResource(SettingsManager.themeOptionLabelRes(opt))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                SettingsManager.setThemeOption(context, opt)
                                showThemeDialog = false
                                scope.launch { onShowSnackbar(context.getString(com.vivid.app.R.string.theme_changed, label)) }
                            }.padding(vertical = VividSpace.s, horizontal = VividSpace.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedTheme == opt, onClick = {
                                SettingsManager.setThemeOption(context, opt)
                                showThemeDialog = false
                            })
                            Spacer(Modifier.width(VividSpace.s)); Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(com.vivid.app.R.string.action_close)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (showLangDialog) {
        val options = listOf(
            "es" to stringResource(com.vivid.app.R.string.lang_spanish),
            "en" to stringResource(com.vivid.app.R.string.lang_english)
        )
        VividAlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(stringResource(com.vivid.app.R.string.lang_setting), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (code, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                com.vivid.app.util.LocaleManager.setLanguage(context, code)
                                showLangDialog = false
                                scope.launch {
                                    onShowSnackbar(context.getString(com.vivid.app.R.string.lang_changed, label))
                                    // Recrear la activity para que los recursos
                                    // se sirvan en el nuevo idioma.
                                    (context as? android.app.Activity)?.recreate()
                                }
                            }.padding(vertical = VividSpace.s, horizontal = VividSpace.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedLang == code, onClick = {
                                com.vivid.app.util.LocaleManager.setLanguage(context, code)
                                showLangDialog = false
                                (context as? android.app.Activity)?.recreate()
                            })
                            Spacer(Modifier.width(VividSpace.s)); Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text("Cerrar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
    if (showFontDialog) {
        val options = listOf(
            com.vivid.app.util.LocaleManager.FONT_SCALES[0] to stringResource(com.vivid.app.R.string.font_size_small),
            com.vivid.app.util.LocaleManager.FONT_SCALES[1] to stringResource(com.vivid.app.R.string.font_size_normal),
            com.vivid.app.util.LocaleManager.FONT_SCALES[2] to stringResource(com.vivid.app.R.string.font_size_large),
            com.vivid.app.util.LocaleManager.FONT_SCALES[3] to stringResource(com.vivid.app.R.string.font_size_xlarge)
        )
        VividAlertDialog(
            onDismissRequest = { showFontDialog = false },
            title = { Text(stringResource(com.vivid.app.R.string.font_size_setting), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { (scale, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                com.vivid.app.util.LocaleManager.setFontScale(context, scale)
                                showFontDialog = false
                                (context as? android.app.Activity)?.recreate()
                            }.padding(vertical = VividSpace.s, horizontal = VividSpace.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = kotlin.math.abs(fontScale - scale) < 0.01f,
                                onClick = {
                                    com.vivid.app.util.LocaleManager.setFontScale(context, scale)
                                    showFontDialog = false
                                    (context as? android.app.Activity)?.recreate()
                                }
                            )
                            Spacer(Modifier.width(VividSpace.s)); Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFontDialog = false }) { Text("Cerrar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

/**
 * Selector de semilla de marca: una fila de muestras circulares.
 *
 * Es un `radiogroup` a efectos de accesibilidad (una sola selección), no una
 * lista de botones sueltos, para que TalkBack anuncie "2 de 5, seleccionado".
 * Cuando el color dinámico está activo se muestra atenuado y no responde: la
 * paleta la manda el wallpaper y desactivarlo visualmente es más honesto que
 * dejar al usuario tocando muestras que no hacen nada.
 */
@Composable
private fun VividSeedPaletteSelector(
    selected: VividSeedPalette,
    enabled: Boolean,
    onSelect: (VividSeedPalette) -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.m, vertical = VividSpace.s)
            .selectableGroup()
    ) {
        Text(
            text = stringResource(com.vivid.app.R.string.seed_palette_title),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
        Text(
            text = stringResource(
                if (enabled) com.vivid.app.R.string.seed_palette_subtitle
                else com.vivid.app.R.string.seed_palette_dynamic_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
        )
        Spacer(Modifier.height(VividSpace.s))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VividSpace.s)
        ) {
            VividSeedPalette.entries.forEach { palette ->
                val isSelected = palette == selected
                val label = stringResource(palette.labelRes)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(palette) }
                        )
                        .padding(vertical = VividSpace.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(palette.swatch.copy(alpha = alpha), CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = alpha),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(VividSpace.xxs))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Contenido y multimedia
// ─────────────────────────────────────────────────────────────
@Composable
fun ContenidoSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val autoplay = SettingsManager.autoplayReels
    val showInFeed = SettingsManager.showReelsInFeed
    val hd = SettingsManager.hdUploadsEnabled
    val dataSaver = SettingsManager.dataSaverMode
    val offensive = SettingsManager.offensiveWordsFilter
    val hideLikes = SettingsManager.hideLikesCount

    fun upd(field: String, v: Boolean) { user?.uid?.let { firestore.collection("users").document(it).update(field, v) } }

    VividSettingsScaffold(title = "Contenido y multimedia", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup(title = "Reproducción") {
                    VividSettingsSwitchItem(title = "Reproducción automática de Reels", subtitle = if (autoplay) "En Wi-Fi y datos" else "Pausada", icon = Icons.Outlined.PlayCircle, checked = autoplay, onCheckedChange = { SettingsManager.setAutoplayReels(context, it); upd("autoplayReels", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Mostrar Reels en el Feed", subtitle = if (showInFeed) "Visibles en inicio" else "Solo en pestaña", icon = Icons.Outlined.VideoLibrary, checked = showInFeed, onCheckedChange = { SettingsManager.setShowReelsInFeed(context, it); upd("showReelsInFeed", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Subidas en HD", subtitle = if (hd) "Calidad original" else "Comprimido", icon = Icons.Outlined.Hd, checked = hd, onCheckedChange = { SettingsManager.setHdUploads(context, it); upd("hdUploadsEnabled", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Ahorro de datos", subtitle = if (dataSaver) "Ahorrando · sin precarga de video" else "En datos móviles no se precargan reels ni stories", icon = Icons.Outlined.DataSaverOff, checked = dataSaver, onCheckedChange = { SettingsManager.setDataSaver(context, it); upd("dataSaverMode", it) })
                }
            }
            item {
                VividSettingsGroup(title = "Filtros") {
                    VividSettingsSwitchItem(title = "Filtro de palabras ofensivas", subtitle = "Oculta contenido molesto", icon = Icons.Outlined.FilterAlt, checked = offensive, onCheckedChange = { SettingsManager.setOffensiveWords(context, it); upd("offensiveWordsFilter", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Ocultar recuento de Me gusta", subtitle = "No mostrar total de likes", icon = Icons.Outlined.FavoriteBorder, checked = hideLikes, onCheckedChange = { SettingsManager.setHideLikes(context, it); upd("hideLikesCount", it) })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Notificaciones
// ─────────────────────────────────────────────────────────────
@Composable
fun NotificacionesSettingsScreen(onBack: () -> Unit, onShowSnackbar: suspend (String)->Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val likes = SettingsManager.notifyLikesComments
    val followers = SettingsManager.notifyNewFollowers
    val dm = SettingsManager.notifyDirectMessages
    val stories = SettingsManager.notifyStoryReminders
    val scope = rememberCoroutineScope()
    fun upd(field: String, v: Boolean) { user?.uid?.let { firestore.collection("users").document(it).update(field, v) } }

    VividSettingsScaffold(title = "Notificaciones", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(
                        title = "Ajustes del sistema",
                        subtitle = "Abrir configuración de Android",
                        icon = Icons.Outlined.Notifications,
                        onClick = {
                            val opened = launchExternalIntent(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName) })
                            if (!opened) scope.launch { onShowSnackbar("No se pudo abrir ajustes") }
                        }
                    )
                }
            }
            item {
                VividSettingsGroup(title = "Push internas") {
                    VividSettingsSwitchItem(title = "Me gusta y comentarios", subtitle = if (likes) "Activadas" else "Silenciadas", icon = Icons.Outlined.ThumbUp, checked = likes, onCheckedChange = { SettingsManager.setNotifyLikesComments(context, it); upd("notifyLikesComments", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Nuevos seguidores", subtitle = if (followers) "Activadas" else "Silenciadas", icon = Icons.Outlined.PersonAdd, checked = followers, onCheckedChange = { SettingsManager.setNotifyFollowers(context, it); upd("notifyNewFollowers", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Mensajes directos", subtitle = if (dm) "Activadas" else "Silenciadas", icon = Icons.Outlined.Message, checked = dm, onCheckedChange = { SettingsManager.setNotifyDm(context, it); upd("notifyDirectMessages", it) }, showDivider = true)
                    VividSettingsSwitchItem(title = "Recordatorios de stories", subtitle = if (stories) "Activadas" else "Silenciadas", icon = Icons.Outlined.AvTimer, checked = stories, onCheckedChange = { SettingsManager.setNotifyStoryReminders(context, it); upd("notifyStoryReminders", it) })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Almacenamiento
// ─────────────────────────────────────────────────────────────
@Composable
fun AlmacenamientoSettingsScreen(onBack: () -> Unit, onShowSnackbar: suspend (String)->Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val firestore = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    val quality = SettingsManager.downloadQualityOption
    var showQuality by remember { mutableStateOf(false) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showClearCacheConfirmation by remember { mutableStateOf(false) }
    var showCacheLimit by remember { mutableStateOf(false) }
    var showOfflineConfirmation by remember { mutableStateOf(false) }
    var cacheLimitMb by remember { mutableIntStateOf(VideoCacheManager.maxCacheMb(appContext)) }

    // Lee el tamaño real del caché (Room + Coil + archivos temporales)
    var cacheBreakdown by remember { mutableStateOf(VividCacheManager.CacheBreakdown()) }
    var cacheChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cacheBreakdown = runCatching { VividCacheManager.cacheBreakdown(appContext) }
            .getOrDefault(VividCacheManager.CacheBreakdown())
        cacheChecked = true
    }

    fun clearCache() {
        if (isClearingCache) return
        isClearingCache = true
        scope.launch {
            runCatching {
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext,
                    com.vivid.app.di.VividCacheEntryPoint::class.java
                )
                val db = entryPoint.database()
                val imageLoader = entryPoint.imageLoader()
                VividCacheManager.clearMediaCaches(appContext, imageLoader)
                SettingsManager.recordCacheClear(appContext)
                cacheBreakdown = VividCacheManager.CacheBreakdown()
                onShowSnackbar("Caché multimedia limpiada — se regenerará al recargar")
            }.onFailure { e ->
                onShowSnackbar("Error al limpiar caché: ${e.message}")
            }
            isClearingCache = false
        }
    }

    VividSettingsScaffold(title = "Almacenamiento", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(
                        title = "Borrar caché multimedia",
                        subtitle = if (isClearingCache) "Limpiando…"
                            else if (cacheChecked) "Aprox. ${String.format("%.1f", cacheBreakdown.totalBytes / (1024f * 1024f))} MB"
                            else "Calculando…",
                        icon = Icons.Outlined.Cached,
                        onClick = { showClearCacheConfirmation = true },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Limpiar stories vencidas",
                        subtitle = "Borra stories caducadas",
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = {
                            scope.launch {
                                val count = runCatching { deleteExpiredStoriesForCurrentUser(firestore, user?.uid.orEmpty()) }.getOrElse { onShowSnackbar(it.message ?: "Error"); return@launch }
                                onShowSnackbar(if (count>0) "$count stories eliminadas" else "Sin stories vencidas")
                            }
                        },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Calidad de descarga",
                        subtitle = quality,
                        icon = Icons.Outlined.Download,
                        value = quality,
                        onClick = { showQuality = true }
                    )
                }
            }
            item {
                VividSettingsGroup {
                    VividSettingsItem(title = "Eliminar contenido offline", subtitle = "Posts, stories y reels guardados localmente", icon = Icons.Outlined.CloudOff, onClick = { showOfflineConfirmation = true }, showDivider = true)
                    VividSettingsItem(title = "Límite de caché de video", subtitle = "$cacheLimitMb MB", icon = Icons.Outlined.Tune, onClick = { showCacheLimit = true }, showDivider = true)
                    VividSettingsItem(title = "Imágenes", subtitle = formatBytes(cacheBreakdown.imageBytes), icon = Icons.Outlined.Image)
                    VividSettingsItem(title = "Videos y audio", subtitle = formatBytes(cacheBreakdown.mediaBytes), icon = Icons.Outlined.Movie)
                    VividSettingsItem(title = "Archivos temporales", subtitle = formatBytes(cacheBreakdown.temporaryBytes), icon = Icons.Outlined.FolderDelete)
                    VividSettingsItem(title = "Datos offline", subtitle = formatBytes(cacheBreakdown.databaseBytes), icon = Icons.Outlined.Storage)
                }
            }
        }
    }
    if (showClearCacheConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmation = false },
            title = { Text("¿Borrar caché multimedia?") },
            text = { Text("Se eliminarán imágenes, videos, audio y archivos temporales. Tus mensajes y datos offline se conservarán.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheConfirmation = false
                    clearCache()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmation = false }) { Text("Cancelar") }
            }
        )
    }
    if (showOfflineConfirmation) {
        AlertDialog(
            onDismissRequest = { showOfflineConfirmation = false },
            title = { Text("¿Eliminar contenido offline?") },
            text = { Text("Se eliminarán posts, stories y reels guardados localmente. La caché multimedia no se modificará.") },
            confirmButton = {
                TextButton(onClick = {
                    showOfflineConfirmation = false
                    scope.launch {
                        runCatching {
                            val ep = dagger.hilt.android.EntryPointAccessors.fromApplication(appContext, com.vivid.app.di.VividCacheEntryPoint::class.java)
                            VividCacheManager.clearOfflineContent(ep.database())
                            cacheBreakdown = runCatching { VividCacheManager.cacheBreakdown(appContext) }.getOrDefault(VividCacheManager.CacheBreakdown())
                            onShowSnackbar("Contenido offline eliminado")
                        }.onFailure { onShowSnackbar("No se pudo eliminar el contenido offline") }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { showOfflineConfirmation = false }) { Text("Cancelar") } }
        )
    }
    if (showCacheLimit) {
        AlertDialog(
            onDismissRequest = { showCacheLimit = false },
            title = { Text("Límite de caché de video") },
            text = {
                Column {
                    listOf(100, 250, 500, 1000).forEach { value ->
                        TextButton(onClick = {
                            VideoCacheManager.setMaxCacheMb(appContext, value)
                            cacheLimitMb = value
                            showCacheLimit = false
                        }, modifier = Modifier.fillMaxWidth()) { Text("$value MB") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCacheLimit = false }) { Text("Cancelar") } }
        )
    }
    if (showQuality) {
        VividAlertDialog(
            onDismissRequest = { showQuality = false },
            title = { Text("Calidad de descarga", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Alta (HD)","Media (Equilibrada)","Baja (Ahorro de datos)").forEach { opt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                SettingsManager.setDownloadQuality(context, opt)
                                showQuality = false
                                scope.launch { onShowSnackbar("Calidad: $opt") }
                            }.padding(vertical = VividSpace.s, horizontal = VividSpace.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = quality == opt, onClick = { SettingsManager.setDownloadQuality(context, opt); showQuality = false })
                            Spacer(Modifier.width(VividSpace.s)); Text(opt)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQuality = false }) { Text("Cerrar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Ayuda
// ─────────────────────────────────────────────────────────────
@Composable
fun AyudaSettingsScreen(onBack: () -> Unit, onShowSnackbar: suspend (String)->Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showHelp by remember { mutableStateOf(false) }

    VividSettingsScaffold(title = "Ayuda", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(title = "Centro de ayuda y soporte", subtitle = "Contactar al desarrollador", icon = Icons.Outlined.HelpOutline, onClick = { showHelp = true }, showDivider = true)
                    VividSettingsItem(title = "Reportar un problema", subtitle = "Envía detalles por correo", icon = Icons.Default.BugReport, onClick = { showHelp = true }, showDivider = true)
                    VividSettingsItem(title = "Abrir repositorio", subtitle = "Ver Vivid en GitHub", icon = Icons.Outlined.OpenInBrowser, onClick = {
                        val ok = openUrl(context, "https://github.com/santes210/Vivid")
                        if (!ok) scope.launch { onShowSnackbar("No se pudo abrir GitHub") }
                    })
                }
            }
        }
    }
    if (showHelp) {
        VividAlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Centro de Ayuda", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("¿Problemas con Vivid? Escríbenos.")
                    Spacer(Modifier.height(VividSpace.s))
                    Text("poncho2010santes@gmail.com", style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val ok = composeEmail(context, "poncho2010santes@gmail.com", "Soporte Vivid App M3")
                    if (!ok) scope.launch { onShowSnackbar("Sin app de correo") }
                    showHelp = false
                }) { Icon(Icons.Default.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(VividSpace.xs)); Text("Enviar correo") }
            },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("Cerrar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Acerca de
// ─────────────────────────────────────────────────────────────
@Composable
fun AcercaSettingsScreen(onBack: () -> Unit, onShowSnackbar: suspend (String)->Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    @Suppress("NAME_SHADOWING")
    val onShowSnackbar: suspend (String) -> Unit = { snackbarHostState.showSnackbar(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var infoDialog by remember { mutableStateOf<Pair<String,String>?>(null) }
    var changelogDialog by remember { mutableStateOf(false) }

    VividSettingsScaffold(title = "Acerca de", onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
            verticalArrangement = Arrangement.spacedBy(VividSpace.m)
        ) {
            item {
                VividSettingsGroup {
                    VividSettingsItem(
                        title = "Versión",
                        subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Material You 3",
                        icon = Icons.Outlined.Info,
                        onClick = { scope.launch { onShowSnackbar("Estás en la última versión") } },
                        showDivider = true
                    )
                    VividSettingsItem(
                        title = "Novedades",
                        subtitle = "Qué cambia en cada versión",
                        icon = Icons.Outlined.NewReleases,
                        onClick = { changelogDialog = true },
                        showDivider = true
                    )
                    VividSettingsItem(title = "Términos del Servicio", subtitle = "Normas de la comunidad", icon = Icons.Outlined.Description, onClick = { infoDialog = "Términos" to "Al usar Vivid aceptas compartir contenido respetuoso y veraz." }, showDivider = true)
                    VividSettingsItem(title = "Política de Privacidad", subtitle = "Cómo protegemos tus datos", icon = Icons.Outlined.PrivacyTip, onClick = { infoDialog = "Privacidad" to "Tus datos, imágenes y mensajes están cifrados y protegidos." }, showDivider = true)
                    VividSettingsItem(title = "Licencias de código abierto", subtitle = "Bibliotecas usadas", icon = Icons.Outlined.Code, onClick = { infoDialog = "Licencias" to "Vivid usa Compose Material 3, Firebase, Coil, ExoPlayer, Hilt y Room." })
                }
            }
        }
    }
    infoDialog?.let { (t,m) ->
        VividAlertDialog(onDismissRequest = { infoDialog = null }, title = { Text(t, fontWeight = FontWeight.Bold) }, text = { Text(m) }, confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("Entendido") } }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    if (changelogDialog) {
        VividAlertDialog(
            onDismissRequest = { changelogDialog = false },
            title = { Text("Novedades", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(VividChangelog.releases, key = { it.version }) { release ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "Versión ${release.version}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            release.notes.forEach { note ->
                                Row(Modifier.padding(top = VividSpace.xxs)) {
                                    Text("•  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(note, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { changelogDialog = false }) { Text("Cerrar") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

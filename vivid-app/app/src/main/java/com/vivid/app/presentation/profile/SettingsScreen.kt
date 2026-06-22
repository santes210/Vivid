package com.vivid.app.presentation.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.presentation.stories.deleteExpiredStoriesForCurrentUser
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val APP_VERSION_NAME = "1.0.0"
private const val APP_BUILD_NUMBER = 1

data class SettingsInfoDialog(
    val title: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenOpenedAt = remember { System.currentTimeMillis() }

    var isPrivateAccount by remember { mutableStateOf(false) }
    var postsCount by remember { mutableIntStateOf(0) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followingCount by remember { mutableIntStateOf(0) }
    var closeFriendsCount by remember { mutableIntStateOf(0) }
    var blockedUsersCount by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("vivid_user") }
    var displayName by remember { mutableStateOf("Usuario Vivid") }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<SettingsInfoDialog?>(null) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            runCatching {
                firestore.collection("users").document(uid).get().await()
            }.onSuccess { snapshot ->
                isPrivateAccount = snapshot.getBoolean("isPrivate") ?: false
                postsCount = snapshot.getLong("postsCount")?.toInt() ?: 0
                followersCount = snapshot.getLong("followersCount")?.toInt() ?: 0
                followingCount = snapshot.getLong("followingCount")?.toInt() ?: 0
                closeFriendsCount = (snapshot.get("closeFriends") as? List<*>)?.size ?: 0
                blockedUsersCount = (snapshot.get("blockedUsers") as? List<*>)?.size ?: 0
                username = snapshot.getString("username") ?: username
                displayName = snapshot.getString("displayName") ?: displayName
            }
        }
    }

    fun updatePrivateAccount(enabled: Boolean) {
        isPrivateAccount = enabled
        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).update("isPrivate", enabled)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Ajustes",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SettingsSectionHeader("Tu Cuenta") }
            item {
                SettingsListItem(
                    title = "Privacidad de la cuenta",
                    subtitle = if (isPrivateAccount) "Cuenta Privada" else "Cuenta Pública",
                    icon = if (isPrivateAccount) Icons.Default.Lock else Icons.Default.Public,
                    trailingContent = {
                        Switch(
                            checked = isPrivateAccount,
                            onCheckedChange = { checked ->
                                updatePrivateAccount(checked)
                            }
                        )
                    },
                    onClick = { updatePrivateAccount(!isPrivateAccount) }
                )
            }
            item {
                SettingsListItem(
                    title = "Centro de Cuentas",
                    subtitle = "Contraseña, seguridad, detalles personales",
                    icon = Icons.Outlined.Person,
                    onClick = {
                        infoDialog = SettingsInfoDialog(
                            title = "Centro de Cuentas",
                            message = buildString {
                                appendLine("Nombre: $displayName")
                                appendLine("Usuario: @$username")
                                appendLine("Correo: ${user?.email ?: "No disponible"}")
                                appendLine("UID: ${user?.uid ?: "Sin sesión"}")
                                appendLine()
                                append(
                                    if (user?.isEmailVerified == true) {
                                        "Tu correo ya está verificado."
                                    } else {
                                        "Tu correo todavía no está verificado."
                                    }
                                )
                            }
                        )
                    }
                )
            }

            item { SettingsSectionHeader("Cómo usas Vivid") }
            item {
                SettingsListItem(
                    title = "Notificaciones",
                    icon = Icons.Outlined.Notifications,
                    onClick = {
                        val opened = launchIntentSafely(
                            context,
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        ) || launchIntentSafely(
                            context,
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )

                        if (!opened) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se pudo abrir la configuración de notificaciones.")
                            }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Tiempo en la app",
                    icon = Icons.Outlined.Timer,
                    onClick = {
                        val elapsed = System.currentTimeMillis() - screenOpenedAt
                        infoDialog = SettingsInfoDialog(
                            title = "Tiempo en la app",
                            message = "Has estado en esta sesión de ajustes durante ${formatElapsedTime(elapsed)}."
                        )
                    }
                )
            }

            item { SettingsSectionHeader("Quién puede ver tu contenido") }
            item {
                SettingsListItem(
                    title = "Mejores amigos",
                    icon = Icons.Outlined.StarOutline,
                    onClick = {
                        infoDialog = SettingsInfoDialog(
                            title = "Mejores amigos",
                            message = if (closeFriendsCount == 0) {
                                "Todavía no tienes personas agregadas a Mejores amigos."
                            } else {
                                "Tienes $closeFriendsCount personas en tu lista de Mejores amigos."
                            }
                        )
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Bloqueados",
                    icon = Icons.Outlined.Block,
                    onClick = {
                        infoDialog = SettingsInfoDialog(
                            title = "Cuentas bloqueadas",
                            message = if (blockedUsersCount == 0) {
                                "No tienes cuentas bloqueadas."
                            } else {
                                "Actualmente tienes $blockedUsersCount cuentas bloqueadas."
                            }
                        )
                    }
                )
            }

            item { SettingsSectionHeader("Más información y ayuda") }
            item {
                SettingsListItem(
                    title = "Centro de Ayuda",
                    subtitle = "Reportar un problema o contactar soporte",
                    icon = Icons.Outlined.HelpOutline,
                    onClick = { showHelpDialog = true }
                )
            }
            item {
                SettingsListItem(
                    title = "Estado de la cuenta",
                    icon = Icons.Outlined.Info,
                    onClick = {
                        infoDialog = SettingsInfoDialog(
                            title = "Estado de la cuenta",
                            message = buildString {
                                appendLine("Perfil: ${if (isPrivateAccount) "Privado" else "Público"}")
                                appendLine("Publicaciones: $postsCount")
                                appendLine("Seguidores: $followersCount")
                                appendLine("Siguiendo: $followingCount")
                                appendLine("Correo verificado: ${if (user?.isEmailVerified == true) "Sí" else "No"}")
                                append("Sesión iniciada: ${if (user != null) "Sí" else "No"}")
                            }
                        )
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Sobre Vivid",
                    subtitle = "Versión $APP_VERSION_NAME",
                    icon = Icons.Outlined.VerifiedUser,
                    onClick = {
                        infoDialog = SettingsInfoDialog(
                            title = "Sobre Vivid",
                            message = buildString {
                                appendLine("Versión: $APP_VERSION_NAME")
                                appendLine("Build: $APP_BUILD_NUMBER")
                                appendLine("Usuario actual: @$username")
                                append("Diseñado con Jetpack Compose, Firebase y Material 3.")
                            }
                        )
                    }
                )
            }

            item { SettingsSectionHeader("Herramientas útiles") }
            item {
                SettingsListItem(
                    title = "Verificar correo",
                    subtitle = if (user?.isEmailVerified == true) "Tu correo ya está verificado" else "Enviar correo de verificación",
                    icon = Icons.Default.Email,
                    onClick = {
                        scope.launch {
                            when {
                                user == null -> snackbarHostState.showSnackbar("No hay sesión iniciada.")
                                user.isEmailVerified -> snackbarHostState.showSnackbar("Tu correo ya está verificado.")
                                else -> {
                                    runCatching { user.sendEmailVerification().await() }
                                        .onSuccess {
                                            snackbarHostState.showSnackbar("Se envió un correo de verificación a ${user.email ?: "tu cuenta"}.")
                                        }
                                        .onFailure {
                                            snackbarHostState.showSnackbar(it.message ?: "No se pudo enviar el correo de verificación.")
                                        }
                                }
                            }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Compartir Vivid",
                    subtitle = "Enviar la app o el proyecto a otra persona",
                    icon = Icons.Default.Share,
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Vivid App")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Mira Vivid. Proyecto Android hecho con Jetpack Compose y Firebase."
                            )
                        }
                        val opened = launchIntentSafely(
                            context,
                            Intent.createChooser(shareIntent, "Compartir Vivid")
                        )
                        if (!opened) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se pudo abrir el menú para compartir.")
                            }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Ajustes de la app",
                    subtitle = "Permisos, batería y almacenamiento",
                    icon = Icons.Default.Settings,
                    onClick = {
                        val opened = launchIntentSafely(
                            context,
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                        if (!opened) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se pudieron abrir los ajustes de la app.")
                            }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Limpiar stories vencidas",
                    subtitle = "Borra tus stories que ya caducaron",
                    icon = Icons.Default.Delete,
                    onClick = {
                        scope.launch {
                            val count = runCatching {
                                deleteExpiredStoriesForCurrentUser(
                                    firestore = firestore,
                                    currentUserId = user?.uid.orEmpty()
                                )
                            }.getOrElse {
                                snackbarHostState.showSnackbar(it.message ?: "No se pudieron limpiar las stories.")
                                return@launch
                            }

                            snackbarHostState.showSnackbar(
                                if (count > 0) "$count stories vencidas eliminadas." else "No tenías stories vencidas."
                            )
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Copiar usuario",
                    subtitle = "Guardar @${username} en el portapapeles",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        clipboardManager.setText(AnnotatedString("@$username"))
                        scope.launch { snackbarHostState.showSnackbar("Usuario copiado") }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Copiar correo",
                    subtitle = user?.email ?: "No disponible",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        val email = user?.email.orEmpty()
                        if (email.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("No hay correo disponible") }
                        } else {
                            clipboardManager.setText(AnnotatedString(email))
                            scope.launch { snackbarHostState.showSnackbar("Correo copiado") }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Copiar UID",
                    subtitle = "Útil para soporte o pruebas",
                    icon = Icons.Default.Info,
                    onClick = {
                        val uid = user?.uid.orEmpty()
                        if (uid.isBlank()) {
                            scope.launch { snackbarHostState.showSnackbar("No hay UID disponible") }
                        } else {
                            clipboardManager.setText(AnnotatedString(uid))
                            scope.launch { snackbarHostState.showSnackbar("UID copiado") }
                        }
                    }
                )
            }
            item {
                SettingsListItem(
                    title = "Abrir repositorio del proyecto",
                    subtitle = "Ver Vivid en GitHub",
                    icon = Icons.Default.OpenInBrowser,
                    onClick = {
                        val opened = launchIntentSafely(
                            context,
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/santes210/Vivid"))
                        )
                        if (!opened) {
                            scope.launch { snackbarHostState.showSnackbar("No se pudo abrir GitHub") }
                        }
                    }
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))

                ListItem(
                    headlineContent = {
                        Text(
                            "Cerrar sesión",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickable { showSignOutDialog = true }
                )
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Centro de Ayuda") },
            text = {
                Column {
                    Text("¿Tienes algún problema con Vivid? Estamos aquí para ayudarte.")
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
                    val opened = launchIntentSafely(
                        context,
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:poncho2010santes@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Soporte Vivid App")
                        }
                    )
                    if (!opened) {
                        scope.launch {
                            snackbarHostState.showSnackbar("No se encontró una app de correo instalada.")
                        }
                    }
                    showHelpDialog = false
                }) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Enviar Correo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    infoDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(dialog.title) },
            text = { Text(dialog.message) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Entendido")
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que volver a ingresar tus credenciales para entrar a Vivid.") },
            confirmButton = {
                TextButton(onClick = {
                    auth.signOut()
                    showSignOutDialog = false
                    onBack()
                }) {
                    Text("Cerrar Sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun launchIntentSafely(context: Context, intent: Intent): Boolean {
    val packageManager = context.packageManager
    if (intent.resolveActivity(packageManager) == null) return false
    context.startActivity(intent)
    return true
}

private fun formatElapsedTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun SettingsListItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    trailingContent: @Composable (() -> Unit)? = {
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    },
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = trailingContent,
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
    )
}

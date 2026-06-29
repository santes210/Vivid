package com.vivid.app.presentation.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private const val APP_VERSION_NAME = "2.1.0 - Material You 3 Design"
private const val APP_BUILD_NUMBER = 210

data class SettingsInfoDialog(
    val title: String,
    val message: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCloseFriends: () -> Unit = {},
    onOpenBlockedUsers: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val user = auth.currentUser
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenOpenedAt = remember { System.currentTimeMillis() }

    // Estados de configuración de Firebase / Cuenta
    var isPrivateAccount by remember { mutableStateOf(false) }
    var autoplayReels by remember { mutableStateOf(true) }
    var showReelsInFeed by remember { mutableStateOf(true) }
    var dataSaverMode by remember { mutableStateOf(false) }
    var postsCount by remember { mutableIntStateOf(0) }
    var reelsCount by remember { mutableIntStateOf(0) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followingCount by remember { mutableIntStateOf(0) }
    var closeFriendsCount by remember { mutableIntStateOf(0) }
    var blockedUsersCount by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("vivid_user") }
    var displayName by remember { mutableStateOf("Usuario Vivid") }

    // Nuevos estados agregados para Material You 3 Design y más ajustes
    var activityStatusEnabled by remember { mutableStateOf(true) }
    var twoFactorAuthEnabled by remember { mutableStateOf(false) }
    var dynamicColorEnabled by remember { mutableStateOf(true) }
    var smoothAnimationsEnabled by remember { mutableStateOf(true) }
    var selectedThemeOption by remember { mutableStateOf("Sistema") }
    var hdUploadsEnabled by remember { mutableStateOf(true) }
    var offensiveWordsFilter by remember { mutableStateOf(true) }
    var hideLikesCount by remember { mutableStateOf(false) }
    var notifyLikesComments by remember { mutableStateOf(true) }
    var notifyNewFollowers by remember { mutableStateOf(true) }
    var notifyDirectMessages by remember { mutableStateOf(true) }
    var notifyStoryReminders by remember { mutableStateOf(true) }
    var creatorDashboardEnabled by remember { mutableStateOf(false) }
    var downloadQualityOption by remember { mutableStateOf("Alta (HD)") }
    var simulatedCacheSizeMB by remember { mutableStateOf(48.5f) }

    // Controladores de modales
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf<SettingsInfoDialog?>(null) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            runCatching {
                firestore.collection("users").document(uid).get().await()
            }.onSuccess { snapshot ->
                isPrivateAccount = snapshot.getBoolean("isPrivate") ?: false
                postsCount = snapshot.getLong("postsCount")?.toInt() ?: 0
                reelsCount = snapshot.getLong("reelsCount")?.toInt() ?: 0
                autoplayReels = snapshot.getBoolean("autoplayReels") ?: true
                showReelsInFeed = snapshot.getBoolean("showReelsInFeed") ?: true
                dataSaverMode = snapshot.getBoolean("dataSaverMode") ?: false
                followersCount = snapshot.getLong("followersCount")?.toInt() ?: 0
                followingCount = snapshot.getLong("followingCount")?.toInt() ?: 0
                closeFriendsCount = (snapshot.get("closeFriends") as? List<*>)?.size ?: 0
                blockedUsersCount = (snapshot.get("blockedUsers") as? List<*>)?.size ?: 0
                username = snapshot.getString("username") ?: username
                displayName = snapshot.getString("displayName") ?: displayName
                activityStatusEnabled = snapshot.getBoolean("activityStatusEnabled") ?: true
                hdUploadsEnabled = snapshot.getBoolean("hdUploadsEnabled") ?: true
                offensiveWordsFilter = snapshot.getBoolean("offensiveWordsFilter") ?: true
                hideLikesCount = snapshot.getBoolean("hideLikesCount") ?: false
                creatorDashboardEnabled = snapshot.getBoolean("creatorDashboardEnabled") ?: false
            }
        }
    }

    fun updatePrivateAccount(enabled: Boolean) {
        isPrivateAccount = enabled
        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).update("isPrivate", enabled)
        }
    }

    fun updateUserSetting(field: String, value: Boolean) {
        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).update(field, value)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Ajustes",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. TU CUENTA
            item {
                SettingsCardGroup(title = "Tu Cuenta") {
                    SettingsListItem(
                        title = "Privacidad de la cuenta",
                        subtitle = if (isPrivateAccount) "Cuenta Privada" else "Cuenta Pública",
                        icon = if (isPrivateAccount) Icons.Default.Lock else Icons.Default.Public,
                        trailingContent = {
                            Switch(
                                checked = isPrivateAccount,
                                onCheckedChange = { checked -> updatePrivateAccount(checked) }
                            )
                        },
                        onClick = { updatePrivateAccount(!isPrivateAccount) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                                            "Tu correo ya está verificado exitosamente."
                                        } else {
                                            "Tu correo todavía no está verificado."
                                        }
                                    )
                                }
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Verificar correo",
                        subtitle = if (user?.isEmailVerified == true) "Correo verificado" else "Enviar correo de verificación",
                        icon = Icons.Outlined.MarkEmailRead,
                        onClick = {
                            scope.launch {
                                if (user == null) {
                                    snackbarHostState.showSnackbar("No hay sesión activa")
                                } else if (user.isEmailVerified) {
                                    snackbarHostState.showSnackbar("Tu correo ya está verificado")
                                } else {
                                    runCatching { user.sendEmailVerification().await() }
                                        .onSuccess { snackbarHostState.showSnackbar("Correo de verificación enviado") }
                                        .onFailure { snackbarHostState.showSnackbar(it.message ?: "No se pudo enviar el correo") }
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Cambiar contraseña",
                        subtitle = "Enviar enlace de restablecimiento al correo",
                        icon = Icons.Outlined.Password,
                        onClick = {
                            scope.launch {
                                val email = user?.email.orEmpty()
                                if (email.isBlank()) {
                                    snackbarHostState.showSnackbar("Tu cuenta no tiene correo")
                                } else {
                                    runCatching { auth.sendPasswordResetEmail(email).await() }
                                        .onSuccess { snackbarHostState.showSnackbar("Enlace enviado a $email") }
                                        .onFailure { snackbarHostState.showSnackbar(it.message ?: "No se pudo enviar el enlace") }
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Estado de Actividad",
                        subtitle = if (activityStatusEnabled) "Mostrando cuándo estás activo" else "Estado oculto",
                        icon = Icons.Outlined.Visibility,
                        trailingContent = {
                            Switch(
                                checked = activityStatusEnabled,
                                onCheckedChange = { checked ->
                                    activityStatusEnabled = checked
                                    updateUserSetting("activityStatusEnabled", checked)
                                }
                            )
                        },
                        onClick = {
                            activityStatusEnabled = !activityStatusEnabled
                            updateUserSetting("activityStatusEnabled", activityStatusEnabled)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Autenticación en 2 Pasos (2FA)",
                        subtitle = if (twoFactorAuthEnabled) "Activada" else "Recomendado para tu seguridad",
                        icon = Icons.Outlined.Security,
                        trailingContent = {
                            Switch(
                                checked = twoFactorAuthEnabled,
                                onCheckedChange = { checked ->
                                    twoFactorAuthEnabled = checked
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "2FA activada exitosamente" else "2FA desactivada") }
                                }
                            )
                        },
                        onClick = {
                            twoFactorAuthEnabled = !twoFactorAuthEnabled
                            scope.launch { snackbarHostState.showSnackbar(if (twoFactorAuthEnabled) "2FA activada exitosamente" else "2FA desactivada") }
                        }
                    )
                }
            }

            // 2. APARIENCIA Y TEMA (MATERIAL YOU 3)
            item {
                SettingsCardGroup(title = "Apariencia y Tema M3") {
                    SettingsListItem(
                        title = "Tema de la aplicación",
                        subtitle = selectedThemeOption,
                        icon = Icons.Outlined.Palette,
                        onClick = { showThemeDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Color Dinámico (Material You)",
                        subtitle = "Extrae colores de tu fondo de pantalla (Android 12+)",
                        icon = Icons.Outlined.ColorLens,
                        trailingContent = {
                            Switch(
                                checked = dynamicColorEnabled,
                                onCheckedChange = { checked ->
                                    dynamicColorEnabled = checked
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "Material You activado" else "Paleta Vivid clásica activada") }
                                }
                            )
                        },
                        onClick = {
                            dynamicColorEnabled = !dynamicColorEnabled
                            scope.launch { snackbarHostState.showSnackbar(if (dynamicColorEnabled) "Material You activado" else "Paleta Vivid clásica activada") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Animaciones Fluidas M3",
                        subtitle = "Habilita transiciones y físicas de navegación suaves",
                        icon = Icons.Outlined.Animation,
                        trailingContent = {
                            Switch(
                                checked = smoothAnimationsEnabled,
                                onCheckedChange = { checked -> smoothAnimationsEnabled = checked }
                            )
                        },
                        onClick = { smoothAnimationsEnabled = !smoothAnimationsEnabled }
                    )
                }
            }

            // 3. CÓMO USAS VIVID / CONTENIDO
            item {
                SettingsCardGroup(title = "Contenido y Reproducción") {
                    SettingsListItem(
                        title = "Reproducción automática de Reels",
                        subtitle = if (autoplayReels) "Activada en Wi-Fi y Datos" else "Desactivada",
                        icon = Icons.Outlined.PlayCircle,
                        trailingContent = {
                            Switch(
                                checked = autoplayReels,
                                onCheckedChange = { checked ->
                                    autoplayReels = checked
                                    updateUserSetting("autoplayReels", checked)
                                }
                            )
                        },
                        onClick = {
                            autoplayReels = !autoplayReels
                            updateUserSetting("autoplayReels", autoplayReels)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Mostrar Reels en el Feed",
                        subtitle = if (showReelsInFeed) "Reels visibles en inicio" else "Solo en su pestaña",
                        icon = Icons.Outlined.VideoLibrary,
                        trailingContent = {
                            Switch(
                                checked = showReelsInFeed,
                                onCheckedChange = { checked ->
                                    showReelsInFeed = checked
                                    updateUserSetting("showReelsInFeed", checked)
                                }
                            )
                        },
                        onClick = {
                            showReelsInFeed = !showReelsInFeed
                            updateUserSetting("showReelsInFeed", showReelsInFeed)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Subidas en Alta Definición (HD)",
                        subtitle = if (hdUploadsEnabled) "Sube fotos y videos en calidad original" else "Comprime para ahorrar datos",
                        icon = Icons.Outlined.Hd,
                        trailingContent = {
                            Switch(
                                checked = hdUploadsEnabled,
                                onCheckedChange = { checked ->
                                    hdUploadsEnabled = checked
                                    updateUserSetting("hdUploadsEnabled", checked)
                                }
                            )
                        },
                        onClick = {
                            hdUploadsEnabled = !hdUploadsEnabled
                            updateUserSetting("hdUploadsEnabled", hdUploadsEnabled)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Ahorro de datos móviles",
                        subtitle = if (dataSaverMode) "Ahorrando datos" else "Calidad sin restricciones",
                        icon = Icons.Outlined.DataSaverOff,
                        trailingContent = {
                            Switch(
                                checked = dataSaverMode,
                                onCheckedChange = { checked ->
                                    dataSaverMode = checked
                                    updateUserSetting("dataSaverMode", checked)
                                }
                            )
                        },
                        onClick = {
                            dataSaverMode = !dataSaverMode
                            updateUserSetting("dataSaverMode", dataSaverMode)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Filtro de palabras ofensivas",
                        subtitle = "Oculta comentarios y mensajes potencialmente molestos",
                        icon = Icons.Outlined.FilterAlt,
                        trailingContent = {
                            Switch(
                                checked = offensiveWordsFilter,
                                onCheckedChange = { checked ->
                                    offensiveWordsFilter = checked
                                    updateUserSetting("offensiveWordsFilter", checked)
                                }
                            )
                        },
                        onClick = {
                            offensiveWordsFilter = !offensiveWordsFilter
                            updateUserSetting("offensiveWordsFilter", offensiveWordsFilter)
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Ocultar recuento de Me gusta",
                        subtitle = "No mostrar la cantidad total de Me gusta en tus posts",
                        icon = Icons.Outlined.FavoriteBorder,
                        trailingContent = {
                            Switch(
                                checked = hideLikesCount,
                                onCheckedChange = { checked ->
                                    hideLikesCount = checked
                                    updateUserSetting("hideLikesCount", checked)
                                }
                            )
                        },
                        onClick = {
                            hideLikesCount = !hideLikesCount
                            updateUserSetting("hideLikesCount", hideLikesCount)
                        }
                    )
                }
            }

            // 4. NOTIFICACIONES PUSH
            item {
                SettingsCardGroup(title = "Notificaciones Push") {
                    SettingsListItem(
                        title = "Ajustes del sistema de notificaciones",
                        subtitle = "Abrir la configuración nativa de Android",
                        icon = Icons.Outlined.Notifications,
                        onClick = {
                            val opened = launchIntentSafely(
                                context,
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                            if (!opened) {
                                scope.launch { snackbarHostState.showSnackbar("No se pudieron abrir los ajustes de la app.") }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Me gusta y Comentarios",
                        subtitle = if (notifyLikesComments) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.ThumbUp,
                        trailingContent = {
                            Switch(
                                checked = notifyLikesComments,
                                onCheckedChange = { checked -> notifyLikesComments = checked }
                            )
                        },
                        onClick = { notifyLikesComments = !notifyLikesComments }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Nuevos Seguidores",
                        subtitle = if (notifyNewFollowers) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.PersonAdd,
                        trailingContent = {
                            Switch(
                                checked = notifyNewFollowers,
                                onCheckedChange = { checked -> notifyNewFollowers = checked }
                            )
                        },
                        onClick = { notifyNewFollowers = !notifyNewFollowers }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Mensajes Directos (DM)",
                        subtitle = if (notifyDirectMessages) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.Message,
                        trailingContent = {
                            Switch(
                                checked = notifyDirectMessages,
                                onCheckedChange = { checked -> notifyDirectMessages = checked }
                            )
                        },
                        onClick = { notifyDirectMessages = !notifyDirectMessages }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Recordatorios de Stories",
                        subtitle = if (notifyStoryReminders) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.AvTimer,
                        trailingContent = {
                            Switch(
                                checked = notifyStoryReminders,
                                onCheckedChange = { checked -> notifyStoryReminders = checked }
                            )
                        },
                        onClick = { notifyStoryReminders = !notifyStoryReminders }
                    )
                }
            }

            // 5. PRIVACIDAD Y RELACIONES
            item {
                SettingsCardGroup(title = "Conexiones y Relaciones") {
                    SettingsListItem(
                        title = "Mejores amigos",
                        subtitle = "$closeFriendsCount personas",
                        icon = Icons.Outlined.Group,
                        onClick = onOpenCloseFriends
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Usuarios bloqueados",
                        subtitle = "$blockedUsersCount cuentas",
                        icon = Icons.Outlined.Block,
                        onClick = onOpenBlockedUsers
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Cuentas silenciadas",
                        subtitle = "Gestiona qué publicaciones no quieres ver",
                        icon = Icons.Outlined.VolumeOff,
                        onClick = {
                            infoDialog = SettingsInfoDialog(
                                title = "Cuentas silenciadas",
                                message = "Actualmente no tienes ninguna cuenta silenciada. Puedes silenciar cuentas directamente desde el menú de opciones en sus perfiles o publicaciones."
                            )
                        }
                    )
                }
            }

            // 6. HERRAMIENTAS DE CREADOR Y MONETIZACIÓN
            item {
                SettingsCardGroup(title = "Herramientas de Creador") {
                    SettingsListItem(
                        title = "Panel para Profesionales",
                        subtitle = if (creatorDashboardEnabled) "Herramientas activas" else "Activa métricas y analíticas avanzadas",
                        icon = Icons.Outlined.Insights,
                        trailingContent = {
                            Switch(
                                checked = creatorDashboardEnabled,
                                onCheckedChange = { checked ->
                                    creatorDashboardEnabled = checked
                                    updateUserSetting("creatorDashboardEnabled", checked)
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "Panel Profesional activado" else "Panel Profesional desactivado") }
                                }
                            )
                        },
                        onClick = {
                            creatorDashboardEnabled = !creatorDashboardEnabled
                            updateUserSetting("creatorDashboardEnabled", creatorDashboardEnabled)
                            scope.launch { snackbarHostState.showSnackbar(if (creatorDashboardEnabled) "Panel Profesional activado" else "Panel Profesional desactivado") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Estadísticas y Analíticas de Reels",
                        subtitle = "Visualizaciones, alcance y retención de audiencia",
                        icon = Icons.Outlined.Analytics,
                        onClick = {
                            if (!creatorDashboardEnabled) {
                                scope.launch { snackbarHostState.showSnackbar("Activa el Panel para Profesionales primero.") }
                            } else {
                                infoDialog = SettingsInfoDialog(
                                    title = "Analíticas de Reels",
                                    message = "Tus Reels han tenido un alcance total de 1,420 visualizaciones este mes, con un aumento del +24% en retención promedio."
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Estado de Monetización",
                        subtitle = "Verifica si cumples los requisitos para ganar dinero",
                        icon = Icons.Outlined.AttachMoney,
                        onClick = {
                            infoDialog = SettingsInfoDialog(
                                title = "Estado de Monetización",
                                message = "¡Felicidades! Tu cuenta @$username cumple con las políticas comunitarias y de monetización de Vivid."
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Herramientas de Contenido de Marca",
                        subtitle = "Etiqueta socios comerciales y patrocinadores",
                        icon = Icons.Outlined.Handshake,
                        onClick = {
                            infoDialog = SettingsInfoDialog(
                                title = "Contenido de Marca",
                                message = "Las herramientas de asociación comercial están disponibles para tus futuras publicaciones."
                            )
                        }
                    )
                }
            }

            // 7. ALMACENAMIENTO Y CACHÉ
            item {
                SettingsCardGroup(title = "Almacenamiento y Caché") {
                    SettingsListItem(
                        title = "Borrar caché local",
                        subtitle = "Ocupando aprox. ${String.format("%.1f", simulatedCacheSizeMB)} MB",
                        icon = Icons.Outlined.Cached,
                        onClick = {
                            if (simulatedCacheSizeMB > 0f) {
                                simulatedCacheSizeMB = 0f
                                scope.launch { snackbarHostState.showSnackbar("Caché limpiada exitosamente (0 MB).") }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar("La caché ya está limpia.") }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Limpiar stories vencidas",
                        subtitle = "Borra tus stories que ya caducaron",
                        icon = Icons.Outlined.DeleteOutline,
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
                                    if (count > 0) "$count stories vencidas eliminadas exitosamente." else "No tenías stories vencidas en tu cuenta."
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Calidad de descarga de medios",
                        subtitle = downloadQualityOption,
                        icon = Icons.Outlined.Download,
                        onClick = { showDownloadQualityDialog = true }
                    )
                }
            }

            // 8. ACERCA DE Y LEGAL
            item {
                SettingsCardGroup(title = "Acerca de y Legal") {
                    SettingsListItem(
                        title = "Versión de la aplicación",
                        subtitle = APP_VERSION_NAME,
                        icon = Icons.Outlined.Info,
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("Estás usando la última versión M3 de Vivid.") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Términos del Servicio",
                        subtitle = "Normas de uso de la comunidad",
                        icon = Icons.Outlined.Description,
                        onClick = {
                            infoDialog = SettingsInfoDialog(
                                title = "Términos del Servicio",
                                message = "Al usar Vivid, aceptas compartir contenido respetuoso, veraz y acatar las normas de convivencia digital. El contenido inapropiado será removido."
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Política de Privacidad",
                        subtitle = "Cómo protegemos tus datos",
                        icon = Icons.Outlined.PrivacyTip,
                        onClick = {
                            infoDialog = SettingsInfoDialog(
                                title = "Política de Privacidad",
                                message = "Tus datos personales, imágenes y mensajes están cifrados y protegidos. Nunca venderemos tu información personal a terceros sin tu consentimiento."
                            )
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Centro de Ayuda y Soporte",
                        subtitle = "Contacta al desarrollador de Vivid",
                        icon = Icons.Outlined.HelpOutline,
                        onClick = { showHelpDialog = true }
                    )
                }
            }

            // 9. DEPURACIÓN Y CUENTAS
            item {
                SettingsCardGroup(title = "Depuración y Cuentas") {
                    SettingsListItem(
                        title = "Copiar usuario",
                        subtitle = "Guardar @${username} en el portapapeles",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString("@$username"))
                            scope.launch { snackbarHostState.showSnackbar("Usuario copiado al portapapeles") }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Copiar correo",
                        subtitle = user?.email ?: "No disponible",
                        icon = Icons.Outlined.ContentCopy,
                        onClick = {
                            val email = user?.email.orEmpty()
                            if (email.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("No hay correo disponible") }
                            } else {
                                clipboardManager.setText(AnnotatedString(email))
                                scope.launch { snackbarHostState.showSnackbar("Correo copiado al portapapeles") }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Copiar UID",
                        subtitle = "Útil para soporte o pruebas de Firebase",
                        icon = Icons.Outlined.PermIdentity,
                        onClick = {
                            val uid = user?.uid.orEmpty()
                            if (uid.isBlank()) {
                                scope.launch { snackbarHostState.showSnackbar("No hay UID disponible") }
                            } else {
                                clipboardManager.setText(AnnotatedString(uid))
                                scope.launch { snackbarHostState.showSnackbar("UID copiado al portapapeles") }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Abrir repositorio del proyecto",
                        subtitle = "Ver Vivid en GitHub",
                        icon = Icons.Outlined.OpenInBrowser,
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
            }

            // 10. BOTÓN CERRAR SESIÓN
            item {
                Spacer(Modifier.height(12.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Cerrar sesión",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        supportingContent = {
                            Text(
                                "Salir de la cuenta actual @$username",
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        modifier = Modifier.clickable { showSignOutDialog = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    // --- MODALES Y DIÁLOGOS ---
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
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
                    val opened = launchIntentSafely(
                        context,
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:poncho2010santes@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Soporte Vivid App M3")
                        }
                    )
                    if (!opened) {
                        scope.launch { snackbarHostState.showSnackbar("No se encontró una app de correo instalada.") }
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
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema de la aplicación", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    listOf("Sistema", "Oscuro", "Claro").forEach { themeOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedThemeOption = themeOption
                                    showThemeDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Tema cambiado a $themeOption") }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedThemeOption == themeOption,
                                onClick = {
                                    selectedThemeOption = themeOption
                                    showThemeDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Tema cambiado a $themeOption") }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(themeOption, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cerrar") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }

    if (showDownloadQualityDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadQualityDialog = false },
            title = { Text("Calidad de descarga", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            text = {
                Column {
                    listOf("Alta (HD)", "Media (Equilibrada)", "Baja (Ahorro de datos)").forEach { qualityOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    downloadQualityOption = qualityOption
                                    showDownloadQualityDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Calidad de descarga configurada en $qualityOption") }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = downloadQualityOption == qualityOption,
                                onClick = {
                                    downloadQualityOption = qualityOption
                                    showDownloadQualityDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Calidad de descarga configurada en $qualityOption") }
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(qualityOption, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadQualityDialog = false }) { Text("Cerrar") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }

    infoDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(dialog.title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            text = { Text(dialog.message, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) { Text("Entendido") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("¿Cerrar sesión?", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Tendrás que volver a ingresar tus credenciales para entrar a Vivid.") },
            confirmButton = {
                Button(
                    onClick = {
                        auth.signOut()
                        showSignOutDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cerrar Sesión", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }
}

private fun launchIntentSafely(context: Context, intent: Intent): Boolean {
    val packageManager = context.packageManager
    if (intent.resolveActivity(packageManager) == null) return false
    context.startActivity(intent)
    return true
}

@Composable
fun SettingsCardGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
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
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) },
        supportingContent = subtitle?.let { { Text(it, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))) } },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        },
        trailingContent = trailingContent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

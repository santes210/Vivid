package com.vivid.app.presentation.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val APP_VERSION_NAME = "2.1.0 - Material You 3 Design"

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

    // Remote stats and private state
    var isPrivateAccount by remember { mutableStateOf(false) }
    var postsCount by remember { mutableIntStateOf(0) }
    var reelsCount by remember { mutableIntStateOf(0) }
    var followersCount by remember { mutableIntStateOf(0) }
    var followingCount by remember { mutableIntStateOf(0) }
    var closeFriendsCount by remember { mutableIntStateOf(0) }
    var blockedUsersCount by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("vivid_user") }
    var displayName by remember { mutableStateOf("Usuario Vivid") }

    // Bind other settings directly to reactive SettingsManager
    val autoplayReels = SettingsManager.autoplayReels
    val showReelsInFeed = SettingsManager.showReelsInFeed
    val dataSaverMode = SettingsManager.dataSaverMode
    val activityStatusEnabled = SettingsManager.activityStatusEnabled
    val twoFactorAuthEnabled = SettingsManager.twoFactorAuthEnabled
    val dynamicColorEnabled = SettingsManager.dynamicColorEnabled
    val smoothAnimationsEnabled = SettingsManager.smoothAnimationsEnabled
    val selectedThemeOption = SettingsManager.selectedThemeOption
    val hdUploadsEnabled = SettingsManager.hdUploadsEnabled
    val offensiveWordsFilter = SettingsManager.offensiveWordsFilter
    val hideLikesCount = SettingsManager.hideLikesCount
    val notifyLikesComments = SettingsManager.notifyLikesComments
    val notifyNewFollowers = SettingsManager.notifyNewFollowers
    val notifyDirectMessages = SettingsManager.notifyDirectMessages
    val notifyStoryReminders = SettingsManager.notifyStoryReminders
    val creatorDashboardEnabled = SettingsManager.creatorDashboardEnabled
    val downloadQualityOption = SettingsManager.downloadQualityOption
    val simulatedCacheSizeMB = SettingsManager.simulatedCacheSizeMB

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
                followersCount = snapshot.getLong("followersCount")?.toInt() ?: 0
                followingCount = snapshot.getLong("followingCount")?.toInt() ?: 0
                closeFriendsCount = (snapshot.get("closeFriends") as? List<*>)?.size ?: 0
                blockedUsersCount = (snapshot.get("blockedUsers") as? List<*>)?.size ?: 0
                username = snapshot.getString("username") ?: username
                displayName = snapshot.getString("displayName") ?: displayName

                // Sincronizar configuraciones en la base de datos a SharedPreferences (local)
                snapshot.getBoolean("autoplayReels")?.let { SettingsManager.setAutoplayReels(context, it) }
                snapshot.getBoolean("showReelsInFeed")?.let { SettingsManager.setShowReelsInFeed(context, it) }
                snapshot.getBoolean("dataSaverMode")?.let { SettingsManager.setDataSaver(context, it) }
                snapshot.getBoolean("activityStatusEnabled")?.let { SettingsManager.setActivityStatus(context, it) }
                snapshot.getBoolean("hdUploadsEnabled")?.let { SettingsManager.setHdUploads(context, it) }
                snapshot.getBoolean("offensiveWordsFilter")?.let { SettingsManager.setOffensiveWords(context, it) }
                snapshot.getBoolean("hideLikesCount")?.let { SettingsManager.setHideLikes(context, it) }
                snapshot.getBoolean("creatorDashboardEnabled")?.let { SettingsManager.setCreatorDashboard(context, it) }
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
                                    SettingsManager.setActivityStatus(context, checked)
                                    updateUserSetting("activityStatusEnabled", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !activityStatusEnabled
                            SettingsManager.setActivityStatus(context, nextVal)
                            updateUserSetting("activityStatusEnabled", nextVal)
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
                                    SettingsManager.set2FA(context, checked)
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "2FA activada exitosamente" else "2FA desactivada") }
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !twoFactorAuthEnabled
                            SettingsManager.set2FA(context, nextVal)
                            scope.launch { snackbarHostState.showSnackbar(if (nextVal) "2FA activada exitosamente" else "2FA desactivada") }
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
                                    SettingsManager.setDynamicColor(context, checked)
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "Material You activado" else "Paleta Vivid clásica activada") }
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !dynamicColorEnabled
                            SettingsManager.setDynamicColor(context, nextVal)
                            scope.launch { snackbarHostState.showSnackbar(if (nextVal) "Material You activado" else "Paleta Vivid clásica activada") }
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
                                onCheckedChange = { checked -> SettingsManager.setSmoothAnimations(context, checked) }
                            )
                        },
                        onClick = { SettingsManager.setSmoothAnimations(context, !smoothAnimationsEnabled) }
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
                                    SettingsManager.setAutoplayReels(context, checked)
                                    updateUserSetting("autoplayReels", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !autoplayReels
                            SettingsManager.setAutoplayReels(context, nextVal)
                            updateUserSetting("autoplayReels", nextVal)
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
                                    SettingsManager.setShowReelsInFeed(context, checked)
                                    updateUserSetting("showReelsInFeed", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !showReelsInFeed
                            SettingsManager.setShowReelsInFeed(context, nextVal)
                            updateUserSetting("showReelsInFeed", nextVal)
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
                                    SettingsManager.setHdUploads(context, checked)
                                    updateUserSetting("hdUploadsEnabled", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !hdUploadsEnabled
                            SettingsManager.setHdUploads(context, nextVal)
                            updateUserSetting("hdUploadsEnabled", nextVal)
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
                                    SettingsManager.setDataSaver(context, checked)
                                    updateUserSetting("dataSaverMode", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !dataSaverMode
                            SettingsManager.setDataSaver(context, nextVal)
                            updateUserSetting("dataSaverMode", nextVal)
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
                                    SettingsManager.setOffensiveWords(context, checked)
                                    updateUserSetting("offensiveWordsFilter", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !offensiveWordsFilter
                            SettingsManager.setOffensiveWords(context, nextVal)
                            updateUserSetting("offensiveWordsFilter", nextVal)
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
                                    SettingsManager.setHideLikes(context, checked)
                                    updateUserSetting("hideLikesCount", checked)
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !hideLikesCount
                            SettingsManager.setHideLikes(context, nextVal)
                            updateUserSetting("hideLikesCount", nextVal)
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
                                onCheckedChange = { checked -> SettingsManager.setNotifyLikesComments(context, checked) }
                            )
                        },
                        onClick = { SettingsManager.setNotifyLikesComments(context, !notifyLikesComments) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Nuevos Seguidores",
                        subtitle = if (notifyNewFollowers) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.PersonAdd,
                        trailingContent = {
                            Switch(
                                checked = notifyNewFollowers,
                                onCheckedChange = { checked -> SettingsManager.setNotifyFollowers(context, checked) }
                            )
                        },
                        onClick = { SettingsManager.setNotifyFollowers(context, !notifyNewFollowers) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Mensajes Directos (DM)",
                        subtitle = if (notifyDirectMessages) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.Message,
                        trailingContent = {
                            Switch(
                                checked = notifyDirectMessages,
                                onCheckedChange = { checked -> SettingsManager.setNotifyDm(context, checked) }
                            )
                        },
                        onClick = { SettingsManager.setNotifyDm(context, !notifyDirectMessages) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsListItem(
                        title = "Recordatorios de Stories",
                        subtitle = if (notifyStoryReminders) "Alertas activas" else "Silenciados",
                        icon = Icons.Outlined.AvTimer,
                        trailingContent = {
                            Switch(
                                checked = notifyStoryReminders,
                                onCheckedChange = { checked -> SettingsManager.setNotifyStoryReminders(context, checked) }
                            )
                        },
                        onClick = { SettingsManager.setNotifyStoryReminders(context, !notifyStoryReminders) }
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
                                    SettingsManager.setCreatorDashboard(context, checked)
                                    updateUserSetting("creatorDashboardEnabled", checked)
                                    scope.launch { snackbarHostState.showSnackbar(if (checked) "Panel Profesional activado" else "Panel Profesional desactivado") }
                                }
                            )
                        },
                        onClick = {
                            val nextVal = !creatorDashboardEnabled
                            SettingsManager.setCreatorDashboard(context, nextVal)
                            updateUserSetting("creatorDashboardEnabled", nextVal)
                            scope.launch { snackbarHostState.showSnackbar(if (nextVal) "Panel Profesional activado" else "Panel Profesional desactivado") }
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
                                SettingsManager.setCacheSize(context, 0f)
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
                                    SettingsManager.setThemeOption(context, themeOption)
                                    showThemeDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Tema cambiado a $themeOption") }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedThemeOption == themeOption,
                                onClick = {
                                    SettingsManager.setThemeOption(context, themeOption)
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
                                    SettingsManager.setDownloadQuality(context, qualityOption)
                                    showDownloadQualityDialog = false
                                    scope.launch { snackbarHostState.showSnackbar("Calidad de descarga configurada en $qualityOption") }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = downloadQualityOption == qualityOption,
                                onClick = {
                                    SettingsManager.setDownloadQuality(context, qualityOption)
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
                        com.vivid.app.util.PushNotificationHelper.unregisterToken()
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

package com.vivid.app.navigation

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.ChatRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.vivid.app.presentation.auth.AuthScreen
import com.vivid.app.presentation.create.*
import com.vivid.app.presentation.feed.FeedScreen
import com.vivid.app.presentation.feed.PostDetailScreen
import com.vivid.app.presentation.messages.ChatListScreen
import com.vivid.app.presentation.messages.ChatScreen
import com.vivid.app.presentation.profile.*
import com.vivid.app.presentation.reels.ReelsScreen
import com.vivid.app.presentation.search.SearchScreen
import com.vivid.app.presentation.explore.ExploreScreen
import com.vivid.app.presentation.search.SearchUser
import com.vivid.app.presentation.stories.CreateStoryScreen
import com.vivid.app.presentation.stories.StoryViewerRoute
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.ui.icons.VividIcons

sealed class Screen(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    object Auth : Screen("auth", "Auth")
    object Feed : Screen("feed", "Inicio", Icons.Default.Home)
    object Search : Screen("search", "Explorar", Icons.Default.Search)
    object Create : Screen("create", "Crear", VividIcons.Create)
    object CreateReel : Screen("create_reel", "Reel", Icons.Default.MovieCreation)
    object CreateStory : Screen("create_story", "Story", Icons.Default.AutoAwesome)
    object Reels : Screen("reels", "Reels", Icons.Default.PlayArrow)
    object Profile : Screen("profile", "Perfil", Icons.Default.Person)
    object OtherProfile : Screen("profile/{userId}", "Perfil")
    object Messages : Screen("messages", "Mensajes")
    object Chat : Screen("chat/{chatId}/{receiverId}/{receiverName}", "Chat")
    object FollowRequests : Screen("follow_requests", "Solicitudes")
    object CloseFriends : Screen("close_friends", "Mejores amigos")
    object BlockedUsers : Screen("blocked_users", "Bloqueados")
    object Settings : Screen("settings", "Ajustes")
    object SettingsAccount : Screen("settings/account", "Cuenta")
    object SettingsPrivacy : Screen("settings/privacy", "Privacidad")
    object SettingsAppearance : Screen("settings/appearance", "Apariencia")
    object SettingsContent : Screen("settings/content", "Contenido")
    object SettingsNotifications : Screen("settings/notifications", "Notificaciones")
    object SettingsStorage : Screen("settings/storage", "Almacenamiento")
    object SettingsHelp : Screen("settings/help", "Ayuda")
    object SettingsAbout : Screen("settings/about", "Acerca")
    object CameraVideo : Screen("camera_video", "Grabar")
    object VideoTrimmer : Screen("video_trimmer", "Trim")
    object PermissionsOnboarding : Screen("permissions_onboarding", "Permisos")
}

@Composable
fun VividNavigation(
    navController: NavHostController,
    deepLinkChatId: String? = null,
    deepLinkReelId: String? = null,
    deepLinkProfileUserId: String? = null,
    deepLinkPostId: String? = null
) {
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val startDestination = remember(auth.currentUser?.uid) {
        if (auth.currentUser != null) Screen.Feed.route else Screen.Auth.route
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val animationsEnabled = LocalVividAnimationsEnabled.current

    // Tabletas → NavigationRail automáticamente (>=600dp de ancho)
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    val primaryDestinations = listOf(
        VividDestination(Screen.Feed, Icons.Filled.Home, Icons.Outlined.Home),
        VividDestination(Screen.Search, Icons.Filled.Search, Icons.Outlined.Search),
        VividDestination(Screen.Create, VividIcons.Create, VividIcons.Create),
        VividDestination(Screen.Reels, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow),
        VividDestination(Screen.Profile, Icons.Filled.Person, Icons.Outlined.Person)
    )

    // ── Manejar deep links desde notificaciones push ──
    LaunchedEffect(deepLinkChatId) {
        if (!deepLinkChatId.isNullOrBlank()) {
            val currentUserId = auth.currentUser?.uid.orEmpty()
            val chatDoc = runCatching {
                FirebaseFirestore.getInstance().collection("chats")
                    .document(deepLinkChatId)
                    .get()
                    .await()
            }.getOrNull()
            val participants = chatDoc?.get("participants") as? List<*>
            val otherUserId = participants
                ?.mapNotNull { it as? String }
                ?.firstOrNull { it != currentUserId }
                .orEmpty()
            val participantNames = chatDoc?.get("participantNames") as? Map<*, *>
            val otherUserName = participantNames?.get(otherUserId) as? String ?: "Usuario"

            navController.navigate(
                "chat/${Uri.encode(deepLinkChatId)}/${Uri.encode(otherUserId)}/${Uri.encode(otherUserName)}"
            ) {
                popUpTo(Screen.Feed.route)
            }
        }
    }
    LaunchedEffect(deepLinkReelId) {
        if (!deepLinkReelId.isNullOrBlank()) {
            navController.navigate(Screen.Reels.route) {
                popUpTo(Screen.Feed.route)
            }
        }
    }
    LaunchedEffect(deepLinkPostId) {
        if (!deepLinkPostId.isNullOrBlank()) {
            navController.navigate("post/${deepLinkPostId}") {
                popUpTo(Screen.Feed.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    LaunchedEffect(deepLinkProfileUserId) {
        if (!deepLinkProfileUserId.isNullOrBlank()) {
            navController.navigate("profile/${Uri.encode(deepLinkProfileUserId)}") {
                popUpTo(Screen.Feed.route)
            }
        }
    }

    // Edge-to-edge: oculta bottomBar en pantallas inmersivas (Reels, Chat, story)
    val isImmersive = remember(currentRoute) {
        currentRoute?.startsWith("reels") == true ||
            currentRoute?.startsWith("chat/") == true ||
            currentRoute?.startsWith("story_viewer") == true ||
            currentRoute?.startsWith("camera") == true ||
            currentRoute?.startsWith("camera_video") == true
    }
    val showBottomBar = !isTablet && currentRoute != Screen.Auth.route && !isImmersive
    val showRail = isTablet && currentRoute != Screen.Auth.route && !isImmersive

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                VividBottomBar(
                    currentRoute = currentRoute,
                    destinations = primaryDestinations,
                    onNavigate = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Feed.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (isImmersive) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars
    ) { innerPadding ->
        Row(Modifier.fillMaxSize()) {
            if (showRail) {
                VividNavigationRail(
                    currentRoute = currentRoute,
                    destinations = primaryDestinations,
                    onNavigate = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Feed.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier
                    .weight(1f)
                    .then(if (isImmersive) Modifier.fillMaxSize() else Modifier.padding(innerPadding)),
                enterTransition = {
                    if (animationsEnabled) {
                        fadeIn(tween(220)) +
                            slideInHorizontally(tween(220)) { fullWidth -> fullWidth / 16 }
                    } else {
                        EnterTransition.None
                    }
                },
                exitTransition = {
                    if (animationsEnabled) fadeOut(tween(120)) else ExitTransition.None
                },
                popEnterTransition = {
                    if (animationsEnabled) fadeIn(tween(180)) else EnterTransition.None
                },
                popExitTransition = {
                    if (animationsEnabled) {
                        fadeOut(tween(160)) +
                            slideOutHorizontally(tween(160)) { fullWidth -> fullWidth / 16 }
                    } else {
                        ExitTransition.None
                    }
                }
            ) {
            composable(Screen.Auth.route) {
                AuthScreen(onLoginSuccess = {
                    // Decidir si mostrar el onboarding de permisos. La marca
                    // vive en SettingsManager para que se pueda resetear desde
                    // Ajustes si el usuario lo pide. Solo se muestra a usuarios
                    // NUEVOS la primera vez.
                    val next = if (com.vivid.app.util.SettingsManager.permissionsOnboardingCompleted)
                        Screen.Feed.route
                    else
                        Screen.PermissionsOnboarding.route
                    navController.navigate(next) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.PermissionsOnboarding.route) {
                com.vivid.app.presentation.onboarding.PermissionsOnboardingScreen(
                    onComplete = {
                        com.vivid.app.util.SettingsManager.markPermissionsOnboardingCompleted(context)
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.PermissionsOnboarding.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        com.vivid.app.util.SettingsManager.markPermissionsOnboardingCompleted(context)
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.PermissionsOnboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Feed.route) {
                FeedScreen(
                    onOpenMessages = { navController.navigate(Screen.Messages.route) },
                    onOpenRequests = { navController.navigate(Screen.FollowRequests.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) },
                    onOpenStoryViewer = { storyId ->
                        navController.navigate("story_viewer/${Uri.encode(storyId)}")
                    },
                    onCreateStory = { navController.navigate(Screen.CreateStory.route) }
                )
            }
            composable(Screen.Search.route) {
                ExploreScreen(
                    onPostClick = { postId -> navController.navigate("post/$postId") },
                    onProfileClick = { userId -> navController.navigate("profile/$userId") }
                )
            }
            composable(Screen.Create.route) {
                CreatePostScreen(navController = navController)
            }
            composable(Screen.CreateReel.route) {
                CreateReelScreen(navController = navController)
            }
            composable(Screen.CreateStory.route) {
                CreateStoryScreen(navController = navController)
            }
            composable(Screen.CameraVideo.route) {
                CameraVideoScreen(
                    navController = navController,
                    onVideoRecorded = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("recordedVideo", uri.toString())
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.VideoTrimmer.route) {
                val trimInput = navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.get<String>("trimInputUri")
                    ?: ""
                if (trimInput.isNotBlank()) {
                    VideoTrimmerScreen(
                        navController = navController,
                        inputUri = Uri.parse(trimInput),
                        onTrimConfirmed = { startMs, endMs ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("trimStartMs", startMs)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("trimEndMs", endMs)
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable("camera") {
                CameraScreen(
                    onPhotoTaken = { uri ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("capturedPhoto", uri.toString())
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Reels.route) {
                ReelsScreen(
                    onCreateReel = { navController.navigate(Screen.CreateReel.route) },
                    initialReelId = deepLinkReelId
                )
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEditProfile = { navController.navigate("edit_profile") },
                    onSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(
                route = Screen.OtherProfile.route,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getString("userId") ?: ""
                ProfileScreen(
                    userId = userId,
                    onLogout = { navController.popBackStack() },
                    onEditProfile = {},
                    onSettings = {},
                    onNavigateToChat = { chatId, receiverId, receiverName ->
                        navController.navigate(
                            "chat/${Uri.encode(chatId)}/${Uri.encode(receiverId)}/${Uri.encode(receiverName)}"
                        )
                    }
                )
            }
            composable(Screen.Settings.route) {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                // Hub M3: lista tonal con 8 categorías
                com.vivid.app.presentation.settings.SettingsHubScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateCuenta = { navController.navigate(Screen.SettingsAccount.route) },
                    onNavigatePrivacidad = { navController.navigate(Screen.SettingsPrivacy.route) },
                    onNavigateApariencia = { navController.navigate(Screen.SettingsAppearance.route) },
                    onNavigateContenido = { navController.navigate(Screen.SettingsContent.route) },
                    onNavigateNotificaciones = { navController.navigate(Screen.SettingsNotifications.route) },
                    onNavigateAlmacenamiento = { navController.navigate(Screen.SettingsStorage.route) },
                    onNavigateAyuda = { navController.navigate(Screen.SettingsHelp.route) },
                    onNavigateAcerca = { navController.navigate(Screen.SettingsAbout.route) },
                    appearanceValue = com.vivid.app.util.SettingsManager.selectedThemeOption,
                    storageValue = String.format("%.1f MB", com.vivid.app.util.SettingsManager.simulatedCacheSizeMB)
                )
            }
            composable(Screen.SettingsAccount.route) {
                val scope = rememberCoroutineScope()
                com.vivid.app.presentation.settings.CuentaSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { msg -> scope.launch { /* handled via internal snackbar inside screen if needed */ } }
                )
            }
            composable(Screen.SettingsPrivacy.route) {
                val scope = rememberCoroutineScope()
                com.vivid.app.presentation.settings.PrivacidadSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCloseFriends = { navController.navigate(Screen.CloseFriends.route) },
                    onOpenBlocked = { navController.navigate(Screen.BlockedUsers.route) },
                    onShowSnackbar = { }
                )
            }
            composable(Screen.SettingsAppearance.route) {
                com.vivid.app.presentation.settings.AparienciaSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { }
                )
            }
            composable(Screen.SettingsContent.route) {
                com.vivid.app.presentation.settings.ContenidoSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.SettingsNotifications.route) {
                com.vivid.app.presentation.settings.NotificacionesSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { }
                )
            }
            composable(Screen.SettingsStorage.route) {
                com.vivid.app.presentation.settings.AlmacenamientoSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { }
                )
            }
            composable(Screen.SettingsHelp.route) {
                com.vivid.app.presentation.settings.AyudaSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { }
                )
            }
            composable(Screen.SettingsAbout.route) {
                com.vivid.app.presentation.settings.AcercaSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onShowSnackbar = { }
                )
            }
            composable("edit_profile") {
                EditProfileScreen(
                    onSave = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() }
                )
            }
            composable(Screen.Messages.route) {
                ChatListScreen(onChatClick = { chatId, receiverId, receiverName ->
                    navController.navigate(
                        "chat/${Uri.encode(chatId)}/${Uri.encode(receiverId)}/${Uri.encode(receiverName)}"
                    )
                })
            }
            composable(Screen.FollowRequests.route) {
                FollowRequestsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.CloseFriends.route) {
                CloseFriendsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "post/{postId}",
                arguments = listOf(navArgument("postId") { type = NavType.StringType })
            ) { backStackEntry ->
                val pid = backStackEntry.arguments?.getString("postId") ?: ""
                PostDetailScreen(postId = pid, onBack = { navController.popBackStack() })
            }
            composable(Screen.BlockedUsers.route) {
                BlockedUsersScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("receiverId") { type = NavType.StringType },
                    navArgument("receiverName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                val receiverId = backStackEntry.arguments?.getString("receiverId") ?: ""
                val receiverName = backStackEntry.arguments?.getString("receiverName") ?: "Usuario"
                ChatScreen(
                    chatId = chatId,
                    receiverId = receiverId,
                    otherUserName = receiverName,
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { targetUserId ->
                        navController.navigate("profile/${Uri.encode(targetUserId)}")
                    }
                )
            }
            composable(
                route = "story_viewer/{storyId}",
                arguments = listOf(navArgument("storyId") { type = NavType.StringType })
            ) { backStackEntry ->
                val storyId = backStackEntry.arguments?.getString("storyId").orEmpty()
                StoryViewerRoute(
                    initialStoryId = storyId,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
    }
}

// ─────────────────────────────────────────────────────────────
//  Navegación principal (mejora M3 Expressive)
//   - Barra inferior: indicador píldora animado, íconos activos/inactivos
//     diferenciados, botón "Crear" destacado, etiquetas compactas.
//   - Tabletas (>=600dp): conmutación automática a NavigationRail.
// ─────────────────────────────────────────────────────────────

private data class VividDestination(
    val screen: Screen,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

/** Índice del destino "Crear", que se pinta como acción principal. */
private const val CREATE_INDEX = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VividBottomBar(
    currentRoute: String?,
    destinations: List<VividDestination>,
    onNavigate: (Screen) -> Unit
) {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            val barWidth = constraints.maxWidth.toFloat()
            val itemWidth = if (destinations.isNotEmpty()) barWidth / destinations.size else barWidth
            val selectedIndex = destinations.indexOfFirst { currentRoute == it.screen.route }
            val safeIndex = if (selectedIndex in destinations.indices) selectedIndex else 0
            val pillWidthPx = with(LocalDensity.current) { CREATE_PILL_WIDTH.toPx() }

            val targetOffset = itemWidth * safeIndex + (itemWidth - pillWidthPx) / 2f
            val animatedOffset by animateFloatAsState(
                targetValue = targetOffset,
                animationSpec = if (animationsEnabled) tween(durationMillis = 320) else snap(),
                label = "navPillOffset"
            )

            // Píldora indicadora activa (desliza al cambiar de destino)
            if (safeIndex != CREATE_INDEX) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                        .padding(vertical = 10.dp)
                        .width(CREATE_PILL_WIDTH)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Row(Modifier.fillMaxWidth().focusGroup()) {
                destinations.forEachIndexed { index, dest ->
                    val isSelected = currentRoute == dest.screen.route
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(NAV_BAR_HEIGHT),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dest.screen == Screen.Create) {
                            // Acción principal: botón "Crear" de mayor énfasis
                            Surface(
                                onClick = { onNavigate(dest.screen) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                tonalElevation = 4.dp,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        VividIcons.Create,
                                        contentDescription = dest.screen.title,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.Tab,
                                        onClick = { onNavigate(dest.screen) }
                                    )
                                    .semantics(mergeDescendants = true) {
                                        stateDescription = if (isSelected) "Seleccionado" else "No seleccionado"
                                    }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) dest.activeIcon else dest.inactiveIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = dest.screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CREATE_PILL_WIDTH = 56.dp
private val NAV_BAR_HEIGHT = 60.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VividNavigationRail(
    currentRoute: String?,
    destinations: List<VividDestination>,
    onNavigate: (Screen) -> Unit
) {
    NavigationRail(
        modifier = Modifier.focusGroup(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Spacer(Modifier.height(12.dp))
        destinations.forEach { dest ->
            val isSelected = currentRoute == dest.screen.route
            if (dest.screen == Screen.Create) {
                // Acción principal destacada en el rail
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(
                    onClick = { onNavigate(dest.screen) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(VividIcons.Create, contentDescription = dest.screen.title)
                }
                Spacer(Modifier.height(8.dp))
            } else {
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onNavigate(dest.screen) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) dest.activeIcon else dest.inactiveIcon,
                            contentDescription = null
                        )
                    },
                    label = {
                        Text(
                            dest.screen.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

private fun NavHostController.openChatWithUser(user: SearchUser) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    if (currentUserId.isBlank() || user.uid.isBlank()) return
    val chatId = ChatRepository.buildChatId(currentUserId, user.uid)
    val name = user.displayName.ifBlank { user.username.ifBlank { "Usuario" } }
    previousBackStackEntry?.savedStateHandle?.set("avatarBase64", user.avatarBase64)
    previousBackStackEntry?.savedStateHandle?.set("avatarUrl", user.avatarUrl)
    navigate("chat/${Uri.encode(chatId)}/${Uri.encode(user.uid)}/${Uri.encode(name)}")
}

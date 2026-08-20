package com.vivid.app.presentation.profile

import com.vivid.app.presentation.report.ReportHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.util.CrashReporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "ProfileScreen"


// Data models moved to ProfileModels.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNavigateToChat: (chatId: String, receiverId: String, name: String) -> Unit = { _, _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()
    val isOwnProfile = userId == currentUserId
    val db = FirebaseFirestore.getInstance()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(ProfileUiState(uid = userId, isCurrentUser = isOwnProfile)) }
    var photoPosts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var reelPosts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var savedPosts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var selectedPost by remember { mutableStateOf<ProfilePost?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Posts, 1: Reels, 2: Guardados

    // Skeleton: solo se muestran placeholders hasta que llega la primera snapshot real
    var isProfileLoaded by remember { mutableStateOf(false) }
    var postsLoaded by remember { mutableStateOf(false) }
    var reelsLoaded by remember { mutableStateOf(false) }
    var savedLoaded by remember { mutableStateOf(false) }

    // Error de carga (red / Firestore): muestra un estado con reintento en
    // lugar de un skeleton eterno o un perfil vacío engañoso.
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }

    val relationshipState by viewModel.relationshipState.collectAsState()
    val isFollowActionLoading by viewModel.isFollowActionLoading.collectAsState()
    val followActionError by viewModel.followActionError.collectAsState()
    val followActionMessage by viewModel.followActionMessage.collectAsState()

    val canViewContent = isOwnProfile || !profile.isPrivate || profile.isFollowedByCurrentUser

    LaunchedEffect(userId) {
        if (!isOwnProfile) viewModel.checkFollowStatus(userId)
    }
    LaunchedEffect(followActionError) {
        followActionError?.let { snackbarHostState.showSnackbar(it); viewModel.clearFollowActionError() }
    }
    LaunchedEffect(followActionMessage) {
        followActionMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearFollowActionMessage() }
    }

    DisposableEffect(userId, retryKey) {
        var profileListener: ListenerRegistration? = null
        var followListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null
        var reelsListener: ListenerRegistration? = null
        var savedListener: ListenerRegistration? = null

        if (userId.isNotBlank()) {
            // ── Perfil ──
            profileListener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.profile")
                        isProfileLoaded = true
                        loadFailed = true
                        return@addSnapshotListener
                    }
                    loadFailed = false
                    val data = snapshot?.data.orEmpty()
                    profile = ProfileUiState(
                        uid = userId,
                        username = data["username"] as? String ?: "vivid_user",
                        displayName = data["displayName"] as? String ?: "Usuario Vivid",
                        bio = data["bio"] as? String ?: "",
                        avatarUrl = data["avatarUrl"] as? String ?: "",
                        avatarBase64 = data["avatarBase64"] as? String ?: "",
                        postsCount = (data["postsCount"] as? Long)?.toInt() ?: 0,
                        reelsCount = (data["reelsCount"] as? Long)?.toInt() ?: 0,
                        followersCount = (data["followersCount"] as? Long)?.toInt() ?: 0,
                        followingCount = (data["followingCount"] as? Long)?.toInt() ?: 0,
                        isPrivate = data["isPrivate"] as? Boolean ?: false,
                        isFollowedByCurrentUser = profile.isFollowedByCurrentUser,
                        isCurrentUser = isOwnProfile
                    )
                    isProfileLoaded = true
                }

            if (!isOwnProfile) {
                followListener = db.collection("users").document(userId)
                    .collection("followers").document(currentUserId)
                    .addSnapshotListener { snap, _ ->
                        val followed = snap?.exists() == true
                        profile = profile.copy(isFollowedByCurrentUser = followed)
                        db.collection("users").document(userId)
                            .collection("followRequests").document(currentUserId)
                            .get().addOnSuccessListener { reqSnap ->
                                profile = profile.copy(isFollowRequestPending = reqSnap.exists())
                            }
                    }
            }

            // ── Posts y Reels del usuario ──
            postsListener = db.collection("posts")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.posts")
                        postsLoaded = true
                        loadFailed = true
                        return@addSnapshotListener
                    }
                    loadFailed = false
                    photoPosts = snapshot?.documents.orEmpty().map { doc ->
                        ProfilePost(
                            id = doc.id,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageBase64 = doc.getString("imageBase64").orEmpty(),
                            storageKey = doc.getString("storageKey").orEmpty(),
                            caption = doc.getString("caption").orEmpty(),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            username = doc.getString("username").orEmpty()
                        )
                    }.sortedByDescending { it.timestamp }
                    postsLoaded = true

                    snapshot?.documents.orEmpty().forEach { doc ->
                        val key = doc.getString("storageKey").orEmpty()
                        if (key.isNotBlank()) {
                            scope.launch {
                                viewModel.refreshSignedUrl(key)?.let { freshUrl ->
                                    photoPosts = photoPosts.map {
                                        if (it.id == doc.id && it.imageUrl != freshUrl) it.copy(imageUrl = freshUrl) else it
                                    }
                                }
                            }
                        }
                    }
                }

            reelsListener = db.collection("reels")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.reels")
                        reelsLoaded = true
                        loadFailed = true
                        return@addSnapshotListener
                    }
                    loadFailed = false
                    reelPosts = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val videoUrl = doc.getString("videoUrl").orEmpty()
                        if (videoUrl.isBlank()) return@mapNotNull null
                        ProfilePost(
                            id = "reel_${doc.id}",
                            imageUrl = doc.getString("thumbnailUrl").orEmpty(),
                            videoUrl = videoUrl,
                            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                            isVideo = true,
                            caption = doc.getString("caption").orEmpty(),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            username = doc.getString("username").orEmpty()
                        )
                    }.sortedByDescending { it.timestamp }
                    reelsLoaded = true
                }

            // ── Pestaña Guardados (solo en el perfil propio) ──
            if (isOwnProfile) {
                savedListener = db.collection("users").document(userId)
                    .collection("savedPosts")
                    .addSnapshotListener { snap, _ ->
                        val savedIds = snap?.documents.orEmpty().map { it.id }
                        if (savedIds.isEmpty()) {
                            savedPosts = emptyList()
                            savedLoaded = true
                        } else {
                            scope.launch {
                                val list = mutableListOf<ProfilePost>()
                                for (savedId in savedIds) {
                                    runCatching {
                                        val postDoc = db.collection("posts").document(savedId).get().await()
                                        if (postDoc.exists()) {
                                            list.add(
                                                ProfilePost(
                                                    id = postDoc.id,
                                                    imageUrl = postDoc.getString("imageUrl").orEmpty(),
                                                    imageBase64 = postDoc.getString("imageBase64").orEmpty(),
                                                    storageKey = postDoc.getString("storageKey").orEmpty(),
                                                    videoUrl = postDoc.getString("videoUrl").orEmpty(),
                                                    thumbnailUrl = postDoc.getString("thumbnailUrl").orEmpty(),
                                                    isVideo = postDoc.getBoolean("isVideo") ?: false,
                                                    caption = postDoc.getString("caption").orEmpty(),
                                                    timestamp = postDoc.getLong("timestamp") ?: 0L,
                                                    username = postDoc.getString("username").orEmpty(),
                                                    isSaved = true
                                                )
                                            )
                                        }
                                    }
                                }
                                savedPosts = list.sortedByDescending { it.timestamp }
                                savedLoaded = true
                            }
                        }
                    }
            }
        }

        onDispose {
            profileListener?.remove()
            followListener?.remove()
            postsListener?.remove()
            reelsListener?.remove()
            savedListener?.remove()
        }
    }

    val currentDisplayList = when (selectedTabIndex) {
        0 -> photoPosts
        1 -> reelPosts
        2 -> savedPosts
        else -> photoPosts
    }

    val activeTabLoading = when (selectedTabIndex) {
        0 -> !postsLoaded
        1 -> !reelsLoaded
        2 -> !savedLoaded
        else -> false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            if (isOwnProfile) profile.displayName else "@${profile.username}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (profile.isPrivate && !isOwnProfile) {
                            Text(
                                "Cuenta privada",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isOwnProfile) {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        // Acciones secundarias agrupadas en un menú
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones de perfil")
                            }
                            DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Ajustes") },
                                    onClick = {
                                        showProfileMenu = false
                                        onSettings()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showProfileMenu = false
                                        com.vivid.app.util.PushNotificationHelper.unregisterToken()
                                        // Credential Manager cachea la cuenta usada: sin
                                        // limpiarla, el próximo login la reutilizaría.
                                        com.vivid.app.presentation.auth.GoogleCredentialSignIn
                                            .clearCredentialState(context)
                                        auth.signOut(); onLogout()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (relationshipState.isBlocked) "Desbloquear" else "Bloquear") },
                                    onClick = {
                                        if (relationshipState.isBlocked) viewModel.unblockUser(userId)
                                        else viewModel.blockUser(userId)
                                        showProfileMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Block, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Enviar mensaje") },
                                    onClick = {
                                        val chatId = com.vivid.app.domain.repository.ChatRepository.buildChatId(currentUserId, userId)
                                        onNavigateToChat(chatId, userId, profile.username)
                                        showProfileMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Email, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reportar usuario", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showProfileMenu = false
                                        val opened = ReportHelper.sendUserReport(
                                            context = context,
                                            userId = userId,
                                            username = profile.username,
                                            reason = "Contenido o comportamiento inapropiado"
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (opened) "Redactando reporte en tu correo."
                                                else "No se encontró una app de correo instalada."
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Header del perfil (skeleton mientras carga) ──
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                if (isProfileLoaded) {
                    ProfileHeader(
                        profile = profile, isOwnProfile = isOwnProfile,
                        relationshipState = relationshipState,
                        isFollowActionLoading = isFollowActionLoading,
                        onToggleFollow = { viewModel.toggleFollow(userId) },
                        onEditProfile = onEditProfile
                    )
                } else {
                    ProfileHeaderSkeleton()
                }
            }

            // ── Pestañas primarias M3 (Posts / Reels / Guardados) ──
            if (canViewContent) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        divider = {},
                        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            icon = { Icon(Icons.Default.GridView, contentDescription = "Posts") },
                            text = { Text("Posts") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            icon = { Icon(Icons.Default.Movie, contentDescription = "Reels") },
                            text = { Text("Reels") }
                        )
                        if (isOwnProfile) {
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = { selectedTabIndex = 2 },
                                icon = { Icon(Icons.Default.Bookmark, contentDescription = "Guardados") },
                                text = { Text("Guardados") }
                            )
                        }
                    }
                }
            }

            // ── Contenido de la pestaña activa ──
            if (!canViewContent) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    PrivateProfileLock(
                        username = profile.username,
                        hasPendingRequest = profile.isFollowRequestPending
                    )
                }
            } else if (loadFailed && currentDisplayList.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    VividErrorState(
                        title = "No se pudieron cargar los datos del perfil",
                        onRetry = {
                            loadFailed = false
                            isProfileLoaded = false
                            postsLoaded = false
                            reelsLoaded = false
                            savedLoaded = false
                            retryKey++
                        }
                    )
                }
            } else if (activeTabLoading) {
                items(9) { ProfileGridSkeletonCell() }
            } else if (selectedTabIndex == 2 && savedPosts.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    EmptySavedPostsPlaceholder()
                }
            } else if (currentDisplayList.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                    EmptyPostsPlaceholder()
                }
            } else {
                items(currentDisplayList, key = { it.id }) { post ->
                    ProfilePostThumbnail(post = post, onClick = { selectedPost = post })
                }
            }
        }
    }

    // ── Visor de post ──
    selectedPost?.let { post ->
        ProfilePostViewerDialog(
            post = post,
            currentUserId = currentUserId,
            onUnsave = {
                scope.launch {
                    runCatching {
                        db.collection("users").document(currentUserId)
                            .collection("savedPosts").document(post.id).delete().await()
                        savedPosts = savedPosts.filter { it.id != post.id }
                        snackbarHostState.showSnackbar("Publicación eliminada de guardados")
                    }
                }
                selectedPost = null
            },
            onDismiss = { selectedPost = null }
        )
    }
}

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
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.theme.LocalVividAnimationsEnabled
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val uid: String = "",
    val username: String = "vivid_user",
    val displayName: String = "Usuario Vivid",
    val bio: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val postsCount: Int = 0,
    val reelsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isPrivate: Boolean = false,
    val isFollowedByCurrentUser: Boolean = false,
    val isCurrentUser: Boolean = false,
    val isFollowRequestPending: Boolean = false
)

data class ProfilePost(
    val id: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val storageKey: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val caption: String = "",
    val timestamp: Long = 0L,
    val username: String = "",
    val isSaved: Boolean = false
)

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

    DisposableEffect(userId) {
        var profileListener: ListenerRegistration? = null
        var followListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null
        var reelsListener: ListenerRegistration? = null
        var savedListener: ListenerRegistration? = null

        if (userId.isNotBlank()) {
            // ── Perfil ──
            profileListener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, _ ->
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
                .addSnapshotListener { snapshot, _ ->
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
                .addSnapshotListener { snapshot, _ ->
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

@Composable
private fun ProfileHeader(
    profile: ProfileUiState,
    isOwnProfile: Boolean,
    relationshipState: com.vivid.app.domain.repository.FollowRelationshipState,
    isFollowActionLoading: Boolean,
    onToggleFollow: () -> Unit,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar como elemento hero con anillo degradado ──
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatar(profile.displayName, profile.avatarUrl, profile.avatarBase64)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Nombre y bio ──
        Text(
            profile.displayName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            "@${profile.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (profile.bio.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Badge privado ──
        if (profile.isPrivate) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                    Text("Privada", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Estadísticas en un grupo coherente (contenedor tonal) ──
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileStat(profile.postsCount.toString(), "Publicaciones", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.reelsCount.toString(), "Reels", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.followersCount.toString(), "Seguidores", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.followingCount.toString(), "Siguiendo", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Acción principal ──
        if (isOwnProfile) {
            FilledTonalButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editar perfil")
            }
        } else {
            Button(
                onClick = onToggleFollow,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = !isFollowActionLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (relationshipState.isFollowing || relationshipState.hasPendingRequest)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (relationshipState.isFollowing || relationshipState.hasPendingRequest)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isFollowActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val text = when {
                        relationshipState.isBlocked -> "Bloqueado"
                        relationshipState.isFollowing -> "Siguiendo"
                        relationshipState.hasPendingRequest -> "Solicitado"
                        else -> "Seguir"
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/**
 * Skeleton del header mientras carga la primera snapshot de Firestore.
 * Pulsa suavemente cuando las animaciones están activadas.
 */
@Composable
private fun ProfileHeaderSkeleton() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val transition = rememberInfiniteTransition(label = "profileSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest
        .copy(alpha = if (animationsEnabled) alpha else 0.45f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(Modifier.size(116.dp).clip(CircleShape).background(blockColor))
        Spacer(Modifier.height(16.dp))
        // Nombre
        Box(Modifier.width(190.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(blockColor))
        Spacer(Modifier.height(8.dp))
        // @usuario
        Box(Modifier.width(130.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(blockColor))
        Spacer(Modifier.height(20.dp))
        // Grupo de estadísticas
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    if (index > 0) {
                        Spacer(Modifier.width(1.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.width(48.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(blockColor))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(70.dp).height(11.dp).clip(RoundedCornerShape(5.dp)).background(blockColor))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Botón de acción
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(blockColor))
    }
}

@Composable
private fun ProfileGridSkeletonCell() {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
    )
}

@Composable
private fun PrivateProfileLock(username: String, hasPendingRequest: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Esta cuenta es privada",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sigue a @$username para ver sus publicaciones y reels.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasPendingRequest) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Solicitud enviada",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPostsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Aún no hay publicaciones.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptySavedPostsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Aún no has guardado publicaciones",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Toca el ícono de marcador en cualquier publicación del feed para guardarla aquí. Solo tú puedes ver tus elementos guardados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Componentes reutilizables ──

@Composable
private fun ProfileAvatar(displayName: String, avatarUrl: String, avatarBase64: String, size: Dp = 100.dp) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try { val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Avatar",
                modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
            return
        }
    }
    if (avatarUrl.isNotBlank()) {
        AsyncImage(model = avatarUrl, contentDescription = "Avatar",
            modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ProfilePostThumbnail(post: ProfilePost, onClick: () -> Unit) {
    var bitmap by remember(post.imageBase64) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(post.imageBase64) {
        bitmap = if (post.imageBase64.isNotBlank()) try { val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null } else null
    }
    Box(
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { onClick() }.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            post.imageUrl.isNotBlank() -> AsyncImage(model = post.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        if (post.isVideo) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayArrow, "Reel", tint = Color.White, modifier = Modifier.padding(8.dp).size(24.dp))
            }
        }
    }
}

@Composable
private fun ProfilePostViewerDialog(
    post: ProfilePost,
    currentUserId: String = "",
    onUnsave: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(post.username.ifBlank { "Publicación" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        if (post.timestamp > 0)
                            Text(java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(post.timestamp)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (post.isSaved) {
                            IconButton(onClick = onUnsave) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Eliminar de guardados", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = onDismiss) { Text("Cerrar", fontWeight = FontWeight.Bold) }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        post.isVideo && post.videoUrl.isNotBlank() -> {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val player = remember(post.videoUrl) {
                                ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(post.videoUrl)); repeatMode = ExoPlayer.REPEAT_MODE_ALL; prepare(); playWhenReady = true }
                            }
                            DisposableEffect(player) { onDispose { player.release() } }
                            AndroidView(factory = { ctx2 -> PlayerView(ctx2).apply { this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                        }
                        post.imageBase64.isNotBlank() -> {
                            val bmp = remember(post.imageBase64) { try { val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null } }
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        post.imageUrl.isNotBlank() -> AsyncImage(model = post.imageUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                if (post.caption.isNotBlank()) Text(post.caption, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ProfileStat(count: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

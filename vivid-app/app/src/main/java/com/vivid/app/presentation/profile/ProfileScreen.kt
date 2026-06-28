package com.vivid.app.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.vivid.app.domain.repository.ChatRepository

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
    val isPrivate: Boolean = false
)

data class ProfilePost(
    val id: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val caption: String = "",
    val timestamp: Long = 0L,
    val username: String = ""
)

@Composable
fun ProfileScreen(
    userId: String,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNavigateToChat: (chatId: String, receiverId: String, name: String) -> Unit = { _, _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()
    val isOwnProfile = userId == currentUserId
    val db = FirebaseFirestore.getInstance()
    val snackbarHostState = remember { SnackbarHostState() }

    var profile by remember { mutableStateOf(ProfileUiState(uid = userId)) }
    var posts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var selectedPost by remember { mutableStateOf<ProfilePost?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }
    val relationshipState by viewModel.relationshipState.collectAsState()
    val isFollowActionLoading by viewModel.isFollowActionLoading.collectAsState()
    val followActionError by viewModel.followActionError.collectAsState()
    val followActionMessage by viewModel.followActionMessage.collectAsState()

    LaunchedEffect(userId) {
        viewModel.checkFollowStatus(userId)
    }

    LaunchedEffect(followActionError) {
        followActionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFollowActionError()
        }
    }

    LaunchedEffect(followActionMessage) {
        followActionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFollowActionMessage()
        }
    }

    DisposableEffect(userId) {
        var profileListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null
        var reelsListener: ListenerRegistration? = null

        if (userId.isNotBlank()) {
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
                        isPrivate = data["isPrivate"] as? Boolean ?: false
                    )
                }

            var photoPosts = emptyList<ProfilePost>()
            var reelPosts = emptyList<ProfilePost>()
            fun publishProfileContent() {
                posts = (photoPosts + reelPosts).sortedByDescending { it.timestamp }
            }

            postsListener = db.collection("posts")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, _ ->
                    photoPosts = snapshot?.documents.orEmpty().map { doc ->
                        ProfilePost(
                            id = doc.id,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageBase64 = doc.getString("imageBase64").orEmpty(),
                            caption = doc.getString("caption").orEmpty(),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            username = doc.getString("username").orEmpty()
                        )
                    }
                    publishProfileContent()
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
                    }
                    publishProfileContent()
                }
        }

        onDispose {
            profileListener?.remove()
            postsListener?.remove()
            reelsListener?.remove()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isOwnProfile) "Mi Perfil" else "@${profile.username}") },
                navigationIcon = {
                    if (!isOwnProfile) {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                        IconButton(onClick = {
                            auth.signOut()
                            onLogout()
                        }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (relationshipState.isBlocked) "Desbloquear" else "Bloquear") },
                                    onClick = {
                                        showProfileMenu = false
                                        if (relationshipState.isBlocked) {
                                            viewModel.unblockUser(userId)
                                        } else {
                                            viewModel.blockUser(userId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(profile.displayName, profile.avatarUrl, profile.avatarBase64)

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                AssistChip(
                    onClick = { },
                    label = { Text(if (profile.isPrivate) "Cuenta privada" else "Cuenta pública") }
                )
                if (profile.bio.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProfileStat((profile.postsCount + profile.reelsCount).toString(), "Posts/Reels")
                    ProfileStat(profile.followersCount.toString(), "Seguidores")
                    ProfileStat(profile.followingCount.toString(), "Siguiendo")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isOwnProfile) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Button(onClick = onEditProfile, modifier = Modifier.weight(1f)) {
                            Text("Editar perfil")
                        }
                        OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                            Text("Ajustes")
                        }
                    }
                } else if (!relationshipState.isBlocked) {
                    val followButtonText = when {
                        relationshipState.isFollowing -> "Siguiendo"
                        relationshipState.hasPendingRequest -> "Pendiente"
                        profile.isPrivate -> "Solicitar"
                        else -> "Seguir"
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Button(
                            onClick = { viewModel.toggleFollow(userId) },
                            modifier = Modifier.weight(1f),
                            enabled = !isFollowActionLoading,
                            colors = if (relationshipState.isFollowing || relationshipState.hasPendingRequest) {
                                ButtonDefaults.filledTonalButtonColors()
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            if (isFollowActionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(followButtonText)
                            }
                        }

                        if (!profile.isPrivate || relationshipState.isFollowing) {
                            OutlinedButton(
                                onClick = {
                                    val chatId = ChatRepository.buildChatId(currentUserId, userId)
                                    onNavigateToChat(chatId, userId, profile.displayName)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mensaje")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            if (relationshipState.isBlocked) {
                BlockedAccountOverlay()
            } else if (profile.isPrivate && !isOwnProfile && !relationshipState.isFollowing) {
                PrivateAccountOverlay(hasPendingRequest = relationshipState.hasPendingRequest)
            } else {
                ProfilePostsGrid(posts = posts, onPostClick = { selectedPost = it })
            }
        }
    }

    selectedPost?.let { post ->
        ProfilePostViewerDialog(post = post, onDismiss = { selectedPost = null })
    }
}

@Composable
private fun PrivateAccountOverlay(hasPendingRequest: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("Esta cuenta es privada", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hasPendingRequest) {
                "Tu solicitud está pendiente de aprobación."
            } else {
                "Envía una solicitud para ver sus fotos y videos."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun BlockedAccountOverlay() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("Has bloqueado esta cuenta", style = MaterialTheme.typography.titleMedium)
        Text(
            "Desbloquéala desde el menú de opciones para volver a ver su contenido.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ProfilePostsGrid(
    posts: List<ProfilePost>,
    onPostClick: (ProfilePost) -> Unit
) {
    Text(
        "Publicaciones y reels",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp)
    )

    if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Aún no hay publicaciones.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(posts, key = { it.id }) { post ->
                ProfilePostThumbnail(post = post, onClick = { onPostClick(post) })
            }
        }
    }
}

@Composable
private fun ProfileAvatar(displayName: String, avatarUrl: String, avatarBase64: String) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            fallbackAvatar(displayName)
        }
    } else if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Foto de perfil",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        fallbackAvatar(displayName)
    }
}

@Composable
private fun fallbackAvatar(displayName: String) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ProfilePostThumbnail(post: ProfilePost, onClick: () -> Unit) {
    var bitmap by remember(post.imageBase64) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(post.imageBase64) {
        bitmap = if (post.imageBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            post.imageUrl.isNotBlank() -> AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            else -> Text("Vivid", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (post.isVideo) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Reel",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(8.dp).size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfilePostViewerDialog(post: ProfilePost, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(post.username.ifBlank { "Publicación" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (post.timestamp > 0) java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(post.timestamp)) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        post.isVideo && post.videoUrl.isNotBlank() -> {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val player = remember(post.videoUrl) {
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(post.videoUrl))
                                    repeatMode = ExoPlayer.REPEAT_MODE_ALL
                                    prepare()
                                    playWhenReady = true
                                }
                            }
                            DisposableEffect(player) { onDispose { player.release() } }
                            AndroidView(
                                factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
                                update = { it.player = player },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        post.imageBase64.isNotBlank() -> {
                            val bitmap = remember(post.imageBase64) {
                                try {
                                    val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        post.imageUrl.isNotBlank() -> {
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                if (post.caption.isNotBlank()) {
                    Text(
                        post.caption,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileStat(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

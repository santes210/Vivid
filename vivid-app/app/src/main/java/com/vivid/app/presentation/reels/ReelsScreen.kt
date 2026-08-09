package com.vivid.app.presentation.reels

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onCreateReel: () -> Unit = {},
    initialReelId: String? = null,
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    // Estado del pager: el count se actualiza automáticamente con reels.size
    val pagerState = rememberPagerState(pageCount = { reels.size })

    // Deep link: saltar al reel específico si viene desde notificación
    LaunchedEffect(initialReelId, reels) {
        val reelId = initialReelId ?: return@LaunchedEffect
        if (reels.isEmpty()) return@LaunchedEffect
        val index = reels.indexOfFirst { it.id == reelId }
        if (index >= 0 && index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }

    // Scroll infinito TikTok: cuando llegas a los últimos 3, carga más
    LaunchedEffect(pagerState.currentPage, reels.size, hasMore) {
        if (reels.isNotEmpty() && hasMore && pagerState.currentPage >= reels.size - 3) {
            viewModel.loadMore()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            reels.isEmpty() -> EmptyReelsState(onCreateReel = onCreateReel)

            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { index -> if (index < reels.size) reels[index].id else index }
            ) { page ->
                if (page in reels.indices) {
                    val reel = reels[page]
                    ReelPage(
                        reel = reel,
                        isPlaying = page == pagerState.currentPage
                    )
                }
            }
        }

        // Header "Reels" flotante
        Surface(
            color = Color.Black.copy(alpha = 0.25f),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .fillMaxWidth(0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reels", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        // Indicador de progreso vertical estilo TikTok (derecha)
        if (reels.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp, top = 80.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                reels.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(3.dp)
                            .height(if (index == pagerState.currentPage) 24.dp else 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == pagerState.currentPage) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // Loading more indicator
        if (isLoadingMore) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        // FAB Crear Reel
        ExtendedFloatingActionButton(
            onClick = onCreateReel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Reel", modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Crear", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun EmptyReelsState(onCreateReel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MovieCreation,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text("No hay reels todavía", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Sé el primero en crear uno", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(
                onClick = onCreateReel,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Crear Reel")
            }
        }
    }
}

@Composable
private fun ReelPage(
    reel: Reel,
    isPlaying: Boolean
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val followRepository = remember { FollowRepository(firestore, FirebaseAuth.getInstance()) }

    // ExoPlayer con remember keyed por videoUrl para que se recicle correctamente
    val exoPlayer = remember(reel.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(reel.videoUrl))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
        }
    }

    var isLiked by remember(reel.id) { mutableStateOf(false) }
    var likeCount by remember(reel.id) { mutableIntStateOf(reel.likes) }
    var commentCount by remember(reel.id) { mutableIntStateOf(reel.commentsCount) }
    var isPausedByUser by remember(reel.id) { mutableStateOf(false) }
    var isPlayerReady by remember(reel.id) { mutableStateOf(false) }
    var showHeartAnimation by remember(reel.id) { mutableStateOf(false) }
    var lastTapTime by remember(reel.id) { mutableStateOf(0L) }
    var isMuted by remember(reel.id) { mutableStateOf(false) }
    var showComments by remember(reel.id) { mutableStateOf(false) }
    var relationshipState by remember(reel.userId) { mutableStateOf(FollowRelationshipState()) }
    var isFollowLoading by remember(reel.userId) { mutableStateOf(false) }

    LaunchedEffect(reel.id, currentUserId) {
        if (currentUserId.isNotBlank()) {
            isLiked = runCatching {
                firestore.collection("reels").document(reel.id)
                    .collection("likes")
                    .document(currentUserId)
                    .get()
                    .await()
                    .exists()
            }.getOrDefault(false)
        }
    }

    LaunchedEffect(reel.userId, currentUserId) {
        if (reel.userId.isNotBlank() && reel.userId != currentUserId) {
            relationshipState = runCatching {
                followRepository.getRelationshipState(reel.userId)
            }.getOrDefault(FollowRelationshipState())
        }
    }

    // Control de reproducción tipo TikTok: solo el reel visible reproduce
    LaunchedEffect(isPlaying, isPausedByUser) {
        if (isPlaying && !isPausedByUser && SettingsManager.autoplayReels) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    LaunchedEffect(showHeartAnimation) {
        if (showHeartAnimation) {
            delay(800)
            showHeartAnimation = false
        }
    }

    if (showComments) {
        ReelCommentsDialog(
            reel = reel,
            onDismiss = { showComments = false },
            onCommentAdded = { commentCount++ }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 300) {
                    // Doble tap = like
                    if (!isLiked) {
                        isLiked = true
                        likeCount++
                        showHeartAnimation = true
                        scope.launch {
                            runCatching { setReelLike(reel.id, currentUserId, true) }
                                .onFailure {
                                    isLiked = false
                                    likeCount = (likeCount - 1).coerceAtLeast(0)
                                }
                        }
                    } else {
                        showHeartAnimation = true
                    }
                } else {
                    isPausedByUser = !isPausedByUser
                }
                lastTapTime = now
            }
    ) {
        // Thumbnail mientras carga
        if (!isPlayerReady && reel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                isPlayerReady = true
                            }
                        }
                    })
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlayerReady) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 2.dp
            )
        }

        AnimatedVisibility(
            visible = showHeartAnimation,
            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Like",
                tint = Color.Red.copy(alpha = 0.85f),
                modifier = Modifier.size(100.dp)
            )
        }

        AnimatedVisibility(
            visible = isPausedByUser,
            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.padding(20.dp).size(52.dp)
                )
            }
        }

        // Degradado inferior para legibilidad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // Info usuario + caption (abajo izquierda)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 24.dp, end = 80.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reel.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = reel.userAvatar,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            reel.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    reel.username,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.width(12.dp))

                if (reel.userId.isNotBlank() && reel.userId != currentUserId) {
                    Surface(
                        color = if (relationshipState.isFollowing || relationshipState.hasPendingRequest) {
                            Color.White.copy(alpha = 0.18f)
                        } else {
                            Color.White
                        },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable(enabled = !isFollowLoading) {
                            scope.launch {
                                isFollowLoading = true
                                runCatching { followRepository.toggleFollow(reel.userId) }
                                relationshipState = runCatching {
                                    followRepository.getRelationshipState(reel.userId)
                                }.getOrDefault(relationshipState)
                                isFollowLoading = false
                            }
                        }
                    ) {
                        Text(
                            when {
                                isFollowLoading -> "..."
                                relationshipState.isFollowing -> "Siguiendo"
                                relationshipState.hasPendingRequest -> "Solicitado"
                                else -> "Seguir"
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (relationshipState.isFollowing || relationshipState.hasPendingRequest) Color.White else Color.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                reel.caption.ifBlank { "Sin descripción" },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Acciones derecha (like, comment, share, mute)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    val newLiked = !isLiked
                    isLiked = newLiked
                    likeCount = (likeCount + if (newLiked) 1 else -1).coerceAtLeast(0)
                    if (newLiked) showHeartAnimation = true
                    scope.launch {
                        runCatching { setReelLike(reel.id, currentUserId, newLiked) }
                            .onFailure {
                                isLiked = !newLiked
                                likeCount = (likeCount + if (newLiked) -1 else 1).coerceAtLeast(0)
                            }
                    }
                }) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color.Red else Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Text(
                    if (SettingsManager.hideLikesCount) "—" else likeCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(Modifier.height(6.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { showComments = true }) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(commentCount.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(6.dp))

            IconButton(onClick = { shareReel(context = context, reel = reel) }) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Compartir",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            IconButton(onClick = { isMuted = !isMuted }) {
                Icon(
                    if (isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                    contentDescription = if (isMuted) "Activar audio" else "Silenciar",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReelCommentsDialog(
    reel: Reel,
    onDismiss: () -> Unit,
    onCommentAdded: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var comments by remember(reel.id) { mutableStateOf<List<ReelComment>>(emptyList()) }
    var commentText by remember(reel.id) { mutableStateOf("") }
    var isSending by remember(reel.id) { mutableStateOf(false) }
    var errorMsg by remember(reel.id) { mutableStateOf<String?>(null) }

    DisposableEffect(reel.id) {
        val registration = firestore.collection("reels").document(reel.id).collection("comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.mapNotNull { doc ->
                    ReelComment(
                        id = doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        username = doc.getString("username") ?: "usuario",
                        text = doc.getString("text").orEmpty(),
                        avatarUrl = doc.getString("avatarUrl").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64").orEmpty(),
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                } ?: emptyList()
            }
        onDispose { registration.remove() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comentarios", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (comments.isEmpty()) {
                    Text("No hay comentarios todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            ReelCommentRow(comment)
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Escribe un comentario...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (commentText.isBlank()) return@FilledTonalButton
                            isSending = true
                            errorMsg = null
                            scope.launch {
                                runCatching {
                                    val userDoc = firestore.collection("users").document(currentUserId).get().await()
                                    firestore.collection("reels").document(reel.id).collection("comments").add(
                                        mapOf(
                                            "userId" to currentUserId,
                                            "username" to (userDoc.getString("username") ?: "yo"),
                                            "text" to commentText.trim(),
                                            "timestamp" to System.currentTimeMillis(),
                                            "avatarUrl" to (userDoc.getString("avatarUrl") ?: ""),
                                            "avatarBase64" to (userDoc.getString("avatarBase64") ?: "")
                                        )
                                    ).await()
                                    firestore.collection("reels").document(reel.id)
                                        .update("comments", FieldValue.increment(1))
                                        .await()
                                }.onSuccess {
                                    commentText = ""
                                    onCommentAdded()
                                }.onFailure {
                                    errorMsg = it.message
                                }
                                isSending = false
                            }
                        },
                        enabled = !isSending,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (isSending) "..." else "Enviar", fontWeight = FontWeight.Bold)
                    }
                }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private data class ReelComment(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val avatarUrl: String,
    val avatarBase64: String,
    val timestamp: Long
)

@Composable
private fun ReelCommentRow(comment: ReelComment) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (comment.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.username,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    comment.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(comment.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun setReelLike(reelId: String, currentUserId: String, shouldLike: Boolean) {
    if (currentUserId.isBlank()) error("No hay sesión activa")
    val firestore = FirebaseFirestore.getInstance()
    val likeRef = firestore.collection("reels").document(reelId).collection("likes").document(currentUserId)
    val reelRef = firestore.collection("reels").document(reelId)
    if (shouldLike) {
        likeRef.set(mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis())).await()
        reelRef.update("likes", FieldValue.increment(1)).await()
    } else {
        likeRef.delete().await()
        reelRef.update("likes", FieldValue.increment(-1)).await()
    }
}

private fun shareReel(context: android.content.Context, reel: Reel) {
    val text = buildString {
        append("Mira este reel de @${reel.username} en Vivid")
        if (reel.caption.isNotBlank()) append("\n\n${reel.caption}")
        if (reel.videoUrl.isNotBlank()) append("\n\n${reel.videoUrl}")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir reel"))
}

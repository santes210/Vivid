package com.vivid.app.presentation.reels

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.PushSender
import com.vivid.app.util.rememberPooledExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
internal fun EmptyReelsState(onCreateReel: () -> Unit) {
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
            Spacer(Modifier.height(VividSpace.xs))
            Text("Sé el primero en crear uno", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))
            FilledTonalButton(
                onClick = onCreateReel,
                shape = VividExpressiveShapes.HeroCard,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(VividSpace.xs))
                Text("Crear Reel")
            }
        }
    }
}

@Composable
internal fun ReelPage(
    reel: Reel,
    isPlaying: Boolean,
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val haptics = rememberVividHaptics()
    val firestore = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()
    val followRepository = remember { FollowRepository(firestore, FirebaseAuth.getInstance()) }

    var isPausedByUser by remember(reel.id) { mutableStateOf(false) }
    var isMuted by remember(reel.id) { mutableStateOf(false) }

    // Pooled ExoPlayer + local video cache. VerticalPager keeps ~3 pages
    // composed; the pool recycles decoders instead of leaking one per reel.
    val exoPlayer = rememberPooledExoPlayer(
        mediaUrl = reel.videoUrl,
        playWhenReady = isPlaying && !isPausedByUser && SettingsManager.autoplayReels,
        repeatMode = ExoPlayer.REPEAT_MODE_ALL,
        volume = if (isMuted) 0f else 1f
    )

    // URL firmada vencida → error de reproducción → pedir una firma nueva.
    // refreshReelUrl cambia reel.videoUrl, el remember() recrea el player y
    // la reproducción continúa con la URL fresca (cacheadada en Room).
    var resignAttempted by remember(reel.id) { mutableStateOf(false) }
    DisposableEffect(exoPlayer, reel.id, reel.storageKey) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (!resignAttempted && reel.storageKey.isNotBlank()) {
                    resignAttempted = true
                    scope.launch {
                        val refreshed = viewModel.refreshReelUrl(reel.id)
                        if (!refreshed) {
                            // Si no se pudo re-firmar (sin red), permite
                            // reintentarlo en el siguiente error.
                            resignAttempted = false
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    var isLiked by remember(reel.id) { mutableStateOf(false) }
    var likeCount by remember(reel.id) { mutableIntStateOf(reel.likes) }
    var commentCount by remember(reel.id) { mutableIntStateOf(reel.commentsCount) }
    var isPlayerReady by remember(reel.id) { mutableStateOf(false) }
    var showHeartAnimation by remember(reel.id) { mutableStateOf(false) }
    var lastTapTime by remember(reel.id) { mutableStateOf(0L) }
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

    LaunchedEffect(showHeartAnimation) {
        if (showHeartAnimation) {
            delay(800)
            showHeartAnimation = false
        }
    }

    if (showComments) {
        ReelCommentsSheet(
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
                        haptics.confirm()
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
                        haptics.tick()
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
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.7f),
                polygons = VividMaterialShapes.LoadingSequence
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
                // Acento de marca armonizado, no un Color.Red suelto.
                tint = LocalVividAccents.current.like.copy(alpha = 0.9f),
                modifier = Modifier.size(96.dp)
            )
        }

        // Indicador de pausa — compacto y centrado (sin saturar la pantalla)
        AnimatedVisibility(
            visible = isPausedByUser,
            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = CircleShape,
                shadowElevation = 0.dp
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.padding(VividSpace.s).size(24.dp)
                )
            }
        }

        // Degradado inferior para legibilidad
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
        )

        // Información del creador + caption agrupadas (abajo izquierda).
        // Se ocultan al pausar para no acumular texto e íconos flotantes a la vez.
        AnimatedVisibility(
            visible = !isPausedByUser,
            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            ReelCreatorCard(
                reel = reel,
                currentUserId = currentUserId,
                relationshipState = relationshipState,
                isFollowLoading = isFollowLoading,
                onFollow = {
                    scope.launch {
                        isFollowLoading = true
                        runCatching { followRepository.toggleFollow(reel.userId) }
                        relationshipState = runCatching {
                            followRepository.getRelationshipState(reel.userId)
                        }.getOrDefault(relationshipState)
                        isFollowLoading = false
                    }
                },
                modifier = Modifier.padding(start = 14.dp, end = 96.dp, bottom = 20.dp)
            )
        }

        // Acciones derecha (like, comment, share, mute) — contenedores negros translúcidos consistentes
        AnimatedVisibility(
            visible = !isPausedByUser,
            enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
            exit = if (animationsEnabled) fadeOut() else ExitTransition.None,
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 10.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(VividSpace.s)
            ) {
                ReelActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) LocalVividAccents.current.like else Color.White,
                    count = if (SettingsManager.hideLikesCount) "—" else likeCount.toString(),
                    onClick = {
                        val newLiked = !isLiked
                        haptics.toggle(newLiked)
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
                    }
                )
                ReelActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    contentDescription = "Comentarios",
                    tint = Color.White,
                    count = commentCount.toString(),
                    onClick = {
                        haptics.tick()
                        showComments = true
                    }
                )
                ReelActionButton(
                    icon = Icons.Default.Share,
                    contentDescription = "Compartir",
                    tint = Color.White,
                    onClick = {
                        haptics.confirm()
                        shareReel(context = context, reel = reel)
                    }
                )
                ReelActionButton(
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                    contentDescription = if (isMuted) "Activar audio" else "Silenciar",
                    tint = Color.White,
                    onClick = { isMuted = !isMuted }
                )
            }
        }
    }
}

@Composable
internal fun ReelCreatorCard(
    reel: Reel,
    currentUserId: String,
    relationshipState: FollowRelationshipState,
    isFollowLoading: Boolean,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.32f),
        shape = VividExpressiveShapes.MediumCard,
        shadowElevation = 0.dp,
        modifier = modifier.widthIn(max = 330.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VividSpace.s, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar con anillo blanco — información del creador agrupada
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.95f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (reel.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = reel.userAvatar,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
            }
            Spacer(Modifier.width(VividSpace.s))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        reel.username,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (reel.userId.isNotBlank() && reel.userId != currentUserId) {
                        Spacer(Modifier.width(VividSpace.xs))
                        ReelFollowPill(
                            label = when {
                                isFollowLoading -> "…"
                                relationshipState.isFollowing -> "Siguiendo"
                                relationshipState.hasPendingRequest -> "Solicitado"
                                else -> "Seguir"
                            },
                            isActive = relationshipState.isFollowing || relationshipState.hasPendingRequest,
                            enabled = !isFollowLoading,
                            onClick = onFollow
                        )
                    }
                }
                if (reel.caption.isNotBlank()) {
                    Spacer(Modifier.height(VividSpace.xxs))
                    Text(
                        reel.caption,
                        color = Color.White.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReelFollowPill(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) Color.White.copy(alpha = 0.22f) else Color.White,
        shape = VividExpressiveShapes.ChipSelected,
        shadowElevation = 0.dp,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = VividSpace.s, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isActive) Color.White else Color.Black
        )
    }
}

@Composable
internal fun ReelActionButton(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    count: String? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color.Black.copy(alpha = 0.32f),
            shape = CircleShape,
            shadowElevation = 0.dp,
            modifier = Modifier.size(46.dp)
        ) {
            IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        if (count != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                count,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReelCommentsSheet(
    reel: Reel,
    onDismiss: () -> Unit,
    onCommentAdded: () -> Unit
) {
    val firestore = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = VividSpace.m)
        ) {
            Text("Comentarios", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(VividSpace.xxs))
            Text(
                "En el reel de @${reel.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(VividSpace.s))

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay comentarios todavía.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 380.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        ReelCommentRow(comment)
                        Spacer(Modifier.height(VividSpace.s))
                    }
                }
            }

            Spacer(Modifier.height(VividSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Escribe un comentario…") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = VividExpressiveShapes.SearchBar,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    )
                )
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        if (commentText.isBlank()) return@FilledIconButton
                        isSending = true
                        errorMsg = null
                        scope.launch {
                            runCatching {
                                val userDoc = firestore.collection("users").document(currentUserId).get().await()
                                val commentRef = firestore.collection("reels").document(reel.id).collection("comments").add(
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
                                PushSender.reelComment(reel.id, commentRef.id)
                            }.onSuccess {
                                commentText = ""
                                onCommentAdded()
                            }.onFailure {
                                errorMsg = it.message
                            }
                            isSending = false
                        }
                    },
                    enabled = !isSending && commentText.isNotBlank(),
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSending) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            polygons = VividMaterialShapes.LoadingSequence
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Enviar")
                    }
                }
            }
            errorMsg?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Spacer(Modifier.height(VividSpace.l))
        }
    }
}

@androidx.compose.runtime.Immutable
internal data class ReelComment(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val avatarUrl: String,
    val avatarBase64: String,
    val timestamp: Long
)

@Composable
internal fun ReelCommentRow(comment: ReelComment) {
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
        Spacer(Modifier.width(VividSpace.s))
        Column(Modifier.weight(1f)) {
            Text(comment.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Like idempotente de reel: transacción que verifica el doc de like antes de
 * tocar el contador (mismo fix que togglePostLike en FeedScreen: sin esto,
 * un estado de UI desactualizado permitía sumar likes infinitos).
 */
internal suspend fun setReelLike(reelId: String, currentUserId: String, shouldLike: Boolean) {
    if (currentUserId.isBlank()) error("No hay sesión activa")
    val firestore = FirebaseFirestore.getInstance()
    val likeRef = firestore.collection("reels").document(reelId).collection("likes").document(currentUserId)
    val reelRef = firestore.collection("reels").document(reelId)
    val created = firestore.runTransaction { txn ->
        val alreadyLiked = txn.get(likeRef).exists()
        when {
            shouldLike && !alreadyLiked -> {
                txn.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis()))
                txn.update(reelRef, "likes", FieldValue.increment(1))
                true
            }
            !shouldLike && alreadyLiked -> {
                txn.delete(likeRef)
                txn.update(reelRef, "likes", FieldValue.increment(-1))
                false
            }
            else -> false
        }
    }.await()
    if (created) PushSender.reelLike(reelId)
}

internal fun shareReel(context: android.content.Context, reel: Reel) {
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

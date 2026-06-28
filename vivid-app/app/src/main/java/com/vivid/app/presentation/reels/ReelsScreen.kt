package com.vivid.app.presentation.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * ReelsScreen v2 — Material You 3.
 *
 * Mejoras:
 *   - Miniatura mientras carga (no pantalla negra).
 *   - FAB "+" extendido para crear reel.
 *   - Pull-to-refresh (placeholder, ya viene en M3).
 *   - Thumbnail con crossfade.
 */
@Composable
fun ReelsScreen(
    onCreateReel: () -> Unit = {},
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var autoplayReels by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isNotBlank()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener { autoplayReels = it.getBoolean("autoplayReels") ?: true }
        }
    }

    val listState = rememberLazyListState()
    var currentPlayingIndex by remember { mutableStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { visibleItems -> visibleItems.maxByOrNull { it.size }?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> currentPlayingIndex = index }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            reels.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No hay reels todavía",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Toca el botón + para crear el primero",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(reels, key = { _, reel -> reel.id }) { index, reel ->
                    ReelItem(
                        reel = reel,
                        isPlaying = autoplayReels && index == currentPlayingIndex,
                        onLike = { isLiked -> updateReelLikeInFirebase(reel.id, isLiked) }
                    )
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Reels", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        // FAB extendido estilo IG
        ExtendedFloatingActionButton(
            onClick = onCreateReel,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear Reel")
            Spacer(Modifier.width(8.dp))
            Text("Crear")
        }
    }
}

private fun updateReelLikeInFirebase(reelId: String, isLiked: Boolean) {
    FirebaseFirestore.getInstance()
        .collection("reels")
        .document(reelId)
        .update("likes", FieldValue.increment(if (isLiked) 1L else -1L))
}

@Composable
fun ReelItem(reel: Reel, isPlaying: Boolean, onLike: (Boolean) -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember(reel.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(reel.videoUrl))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            prepare()
        }
    }

    var isLiked by remember(reel.id) { mutableStateOf(false) }
    var likeCount by remember(reel.id) { mutableStateOf(reel.likes) }
    var isPausedByUser by remember(reel.id) { mutableStateOf(false) }
    var isPlayerReady by remember(reel.id) { mutableStateOf(false) }

    LaunchedEffect(isPlaying, isPausedByUser, exoPlayer) {
        if (isPlaying && !isPausedByUser) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(720.dp)
            .background(Color.Black)
            .clickable { isPausedByUser = !isPausedByUser }
    ) {
        // Thumbnail mientras carga el video
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
                    exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == androidx.media3.common.Player.STATE_READY) {
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
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isPausedByUser) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.padding(18.dp).size(48.dp)
                )
            }
        }

        // Gradiente inferior para legibilidad (Material You surface tint)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reel.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = reel.userAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            reel.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    reel.username,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                reel.caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = {
                isLiked = !isLiked
                likeCount += if (isLiked) 1 else -1
                onLike(isLiked)
            }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Red else Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text("$likeCount", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(20.dp))

            IconButton(onClick = { isPausedByUser = !isPausedByUser }) {
                Icon(
                    imageVector = if (isPlaying && !isPausedByUser) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying && !isPausedByUser) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

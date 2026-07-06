package com.vivid.app.presentation.reels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.delay

/**
 * ReelsScreen — TikTok-style, un reel a la vez con VerticalPager.
 *
 * Mejoras:
 *   - VerticalPager en vez de LazyColumn (desliz vertical uno-por-uno)
 *   - Auto-play solo del reel visible
 *   - Like con animación de corazón
 *   - UI overlay pulida Material You 3
 *   - Gestos: tap para pausar/reproducir, doble tap para like
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onCreateReel: () -> Unit = {},
    viewModel: ReelsViewModel = hiltViewModel()
) {
    val reels by viewModel.reels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val pagerState = rememberPagerState(pageCount = { reels.size.coerceAtLeast(1) })

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            reels.isEmpty() -> EmptyReelsState(onCreateReel = onCreateReel)
            else -> VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val reel = reels[page]
                ReelPage(
                    reel = reel,
                    isCurrentPage = page == pagerState.currentPage,
                    isPlaying = page == pagerState.settledPage || page == pagerState.currentPage
                )
            }
        }

        // ── Top bar semi-transparente ──
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

        // ── Indicador de página (barras laterales) ──
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

        // ── FAB para crear reel ──
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
                Icons.Default.MovieCreation, contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(72.dp)
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
fun ReelPage(
    reel: Reel,
    isCurrentPage: Boolean,
    isPlaying: Boolean
) {
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
    var showHeartAnimation by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }

    // Control de reproducción según visibilidad
    LaunchedEffect(isPlaying, isPausedByUser) {
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

    // Animación del corazón al dar like
    LaunchedEffect(showHeartAnimation) {
        if (showHeartAnimation) {
            delay(800)
            showHeartAnimation = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 300) {
                    // Doble tap → like
                    if (!isLiked) {
                        isLiked = true
                        likeCount++
                        showHeartAnimation = true
                        updateReelLikeInFirebase(reel.id, true)
                    }
                } else {
                    isPausedByUser = !isPausedByUser
                }
                lastTapTime = now
            }
    ) {
        // ── Thumbnail mientras carga ──
        if (!isPlayerReady && reel.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // ── ExoPlayer ──
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

        // ── Loading spinner ──
        if (!isPlayerReady) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
                strokeWidth = 2.dp
            )
        }

        // ── Corazón animado (doble tap) ──
        AnimatedVisibility(
            visible = showHeartAnimation,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Like",
                tint = Color.Red.copy(alpha = 0.85f),
                modifier = Modifier.size(100.dp)
            )
        }

        // ── Icono de pausa ──
        AnimatedVisibility(
            visible = isPausedByUser,
            enter = fadeIn(),
            exit = fadeOut(),
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

        // ── Gradiente inferior ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )

        // ── Info del reel (inferior izquierda) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp, end = 80.dp)
        ) {
            // Usuario
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reel.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = reel.userAvatar, contentDescription = null,
                        modifier = Modifier.size(42.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.size(42.dp).clip(CircleShape)
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
                // Botón seguir (placeholder)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable { /* TODO: seguir */ }
                ) {
                    Text(
                        "Seguir",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Caption
            Text(
                reel.caption.ifBlank { "Sin descripción" },
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Botones de acción (inferior derecha) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Like
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = {
                    isLiked = !isLiked
                    likeCount += if (isLiked) 1 else -1
                    updateReelLikeInFirebase(reel.id, isLiked)
                    if (isLiked) showHeartAnimation = true
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

            // Comentarios
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { /* TODO: abrir comentarios */ }) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text("0", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(6.dp))

            // Compartir
            IconButton(onClick = { /* TODO: compartir */ }) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Compartir",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Sound / Mute
            IconButton(onClick = { /* TODO: mute toggle */ }) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = "Audio",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private fun updateReelLikeInFirebase(reelId: String, isLiked: Boolean) {
    FirebaseFirestore.getInstance()
        .collection("reels").document(reelId)
        .update("likes", FieldValue.increment(if (isLiked) 1L else -1L))
}

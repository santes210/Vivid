package com.vivid.app.presentation.create

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pantalla de trim de video (estilo Instagram Reels editor).
 *
 *   ┌─────────────────────────────────────────┐
 *   │           [Video preview]               │
 *   │                                          │
 *   │                                          │
 *   ├─────────────────────────────────────────┤
 *   │ [start thumb][middle thumbs][end thumb] │  ← pista de frames
 *   │      │                            │     │
 *   │      └───── rango seleccionado ───┘     │
 *   │  00:05                              00:25│
 *   ├─────────────────────────────────────────┤
 *   │  [Cancelar]              [Continuar]    │
 *   └─────────────────────────────────────────┘
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun VideoTrimmerScreen(
    navController: NavController,
    inputUri: Uri,
    onTrimConfirmed: (startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    var durationMs by remember { mutableStateOf(0L) }
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(0L) }
    var frameThumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Cargar duración + generar thumbnails de la pista
    LaunchedEffect(inputUri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, inputUri)
                val dur = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
                durationMs = dur
                endMs = dur

                // Genera 8 frames de muestra para la pista
                frameThumbs = (0 until 8).map { i ->
                    val ts = (dur / 7) * i
                    retriever.getFrameAtTime(
                        ts * 1000,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    ) ?: placeholderBitmap()
                }
            } catch (e: Exception) {
                durationMs = 0L
                endMs = 0L
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            isLoading = false
        }
    }

    // ExoPlayer para el preview
    val player = remember(inputUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(inputUri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(startMs, endMs) {
        if (player.currentPosition < startMs || player.currentPosition > endMs) {
            player.seekTo(startMs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Reel", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Text(
                        "${formatMs(startMs)} – ${formatMs(endMs)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Slider de trim
            if (durationMs > 0) {
                TrimSlider(
                    durationMs = durationMs,
                    startMs = startMs,
                    endMs = endMs,
                    onChange = { newStart, newEnd ->
                        startMs = newStart.coerceIn(0L, endMs - 500L)
                        endMs = newEnd.coerceIn(startMs + 500L, durationMs)
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Pista de frames
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                frameThumbs.forEachIndexed { idx, bmp ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.dp)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Dim si está fuera del rango
                        val ts = (durationMs / 7) * idx
                        val inRange = ts in startMs..endMs
                        if (!inRange) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Duración: ${formatMs(endMs - startMs)}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { onTrimConfirmed(startMs, endMs) },
                enabled = endMs - startMs >= 1000L && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Continuar")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ============================================================================
// TrimSlider — two thumbs sobre un track
// ============================================================================

@Composable
private fun TrimSlider(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onChange: (Long, Long) -> Unit
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val thumbRadiusDp = 14.dp
    val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbRadiusDp * 2)
            .padding(horizontal = thumbRadiusDp)
            .onSizeChanged { sizePx = it }
    ) {
        // Track base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        if (sizePx.width > 0 && durationMs > 0) {
            val widthFloat = sizePx.width.toFloat()
            val startFrac = (startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val endFrac = (endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val startPx = startFrac * widthFloat
            val endPx = endFrac * widthFloat

            // Track activo entre los dos thumbs
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = startPx.toDp(density), end = (widthFloat - endPx).toDp(density))
                    .align(Alignment.CenterStart)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            // Thumb start
            TrimThumb(
                offsetX = startPx - thumbRadiusPx,
                onDragDelta = { delta ->
                    val newStartMs = ((startPx + delta) / widthFloat * durationMs).toLong()
                        .coerceIn(0L, endMs - 500L)
                    onChange(newStartMs, endMs)
                }
            )

            // Thumb end
            TrimThumb(
                offsetX = endPx - thumbRadiusPx,
                onDragDelta = { delta ->
                    val newEndMs = ((endPx + delta) / widthFloat * durationMs).toLong()
                        .coerceIn(startMs + 500L, durationMs)
                    onChange(startMs, newEndMs)
                }
            )
        }
    }
}

@Composable
private fun TrimThumb(
    offsetX: Float,
    onDragDelta: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    onDragDelta(dragAmount)
                }
            }
    )
}

// ============================================================================
// Helpers
// ============================================================================

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    val cs = (ms % 1000) / 10
    return "%02d:%02d.%02d".format(m, s, cs)
}

private fun placeholderBitmap(): Bitmap {
    val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.DKGRAY)
    return bmp
}

private fun Float.toDp(density: androidx.compose.ui.unit.Density) =
    with(density) { this@toDp.toDp() }

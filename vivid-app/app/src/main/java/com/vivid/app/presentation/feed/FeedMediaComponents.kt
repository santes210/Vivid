package com.vivid.app.presentation.feed

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.vivid.app.util.ExoPlayerPool
import com.vivid.app.util.MusicAssets
import com.vivid.app.util.VideoCacheManager
import com.vivid.app.util.rememberPooledExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── PostVideoPlayer ──

@Composable
internal fun PostVideoPlayer(
    videoUrl: String,
    thumbnailUrl: String,
    onUrlExpired: () -> Unit = {}
) {
    var isReady by remember(videoUrl) { mutableStateOf(false) }
    var expiredReported by remember(videoUrl) { mutableStateOf(false) }

    val player = rememberPooledExoPlayer(
        mediaUrl = videoUrl,
        playWhenReady = false,
        repeatMode = Player.REPEAT_MODE_OFF
    )
    DisposableEffect(player, videoUrl) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!expiredReported) {
                    expiredReported = true
                    onUrlExpired()
                }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) isReady = true
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(Modifier.fillMaxWidth().height(380.dp).background(Color.Black)) {
        if (!isReady && thumbnailUrl.isNotBlank()) {
            AsyncImage(model = thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        AndroidView(
            factory = { ctx2 ->
                PlayerView(ctx2).apply {
                    this.player = player
                    useController = true
                    player?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(s: Int) {
                            if (s == Player.STATE_READY) isReady = true
                        }
                    })
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ── PostMusicChip ──

@Composable
internal fun PostMusicChip(post: PostData, onMusicUrlExpired: () -> Unit = {}) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var resolvedAssetFile by remember(post.musicAssetFile) { mutableStateOf<File?>(null) }
    var isPreparingAsset by remember { mutableStateOf(false) }

    // Copy APK asset to cache for reliable playback
    LaunchedEffect(post.musicAssetFile) {
        if (post.musicAssetFile.isNotBlank() && post.musicUrl.isBlank()) {
            isPreparingAsset = true
            resolvedAssetFile = withContext(Dispatchers.IO) {
                try {
                    val assetPath = MusicAssets.resolvePackedPath(post.musicAssetFile)
                    val input = MusicAssets.openAsset(context.assets, post.musicAssetFile)
                    val tempFile = File(context.cacheDir, "post_music_asset_${post.id}_${assetPath.substringAfterLast("/")}")
                    if (!tempFile.exists() || tempFile.length() < 1024) {
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    input.close()
                    tempFile
                } catch (e: Exception) {
                    Log.w("PostMusicChip", "Could not copy asset ${post.musicAssetFile}: ${e.message}")
                    null
                }
            }
            isPreparingAsset = false
        } else {
            resolvedAssetFile = null
        }
    }

    val musicUriString = remember(post, resolvedAssetFile) {
        val raf = resolvedAssetFile
        when {
            post.musicUrl.isNotBlank() -> post.musicUrl
            raf != null && raf.exists() -> "file://${raf.absolutePath}"
            post.musicAssetFile.isNotBlank() -> "asset:///${MusicAssets.resolvePackedPath(post.musicAssetFile)}"
            else -> null
        }
    }

    DisposableEffect(musicUriString, isPlaying) {
        val shouldPlay = isPlaying
        val uri = musicUriString
        if (shouldPlay && uri != null) {
            try {
                val parsedUri = Uri.parse(uri)
                val p = ExoPlayerPool.acquire(context).apply {
                    if (VideoCacheManager.isCacheable(uri)) {
                        setMediaSource(VideoCacheManager.buildCachedMediaSource(context, uri))
                    } else {
                        setMediaItem(MediaItem.fromUri(parsedUri))
                    }
                    prepare()
                    playWhenReady = true
                    volume = 1.0f
                    repeatMode = ExoPlayer.REPEAT_MODE_OFF
                }
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("PostMusicChip", "Error playing ${post.musicTitle}: ${error.message}", error)
                        if (post.musicStorageKey.isNotBlank()) {
                            onMusicUrlExpired()
                            isPlaying = false
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) isPlaying = false
                    }
                })
                player = p
            } catch (e: Exception) {
                Log.e("PostMusicChip", "Could not create player for ${post.musicTitle}: ${e.message}", e)
                player = null
                isPlaying = false
            }
        } else {
            player?.let { ExoPlayerPool.release(it) }
            player = null
        }
        onDispose {
            player?.let { ExoPlayerPool.release(it) }
            player = null
        }
    }

    val hasMusic = post.musicTitle.isNotBlank() || post.musicAssetFile.isNotBlank() || post.musicUrl.isNotBlank()
    if (!hasMusic) return

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.musicTitle.ifBlank { "Music" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (post.musicArtist.isNotBlank()) {
                    Text(
                        text = post.musicArtist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                } else if (musicUriString != null) {
                    Text(
                        text = if (post.musicAssetFile.isNotBlank()) "From APK library" else "Post audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            if (isPreparingAsset) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (musicUriString != null) {
                FilledTonalIconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

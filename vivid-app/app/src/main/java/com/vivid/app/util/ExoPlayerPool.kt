package com.vivid.app.util

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Small pool of [ExoPlayer] instances for feed / reels / stories.
 *
 * Creating a decoder per list item is a classic source of ANRs, dropped
 * frames and battery drain. Reels keep at most ~3 pages composed
 * (`beyondViewportPageCount = 1`); the pool matches that budget and
 * recycles players when a row leaves composition or the lifecycle stops.
 */
object ExoPlayerPool {
    const val DEFAULT_MAX_SIZE = 3

    private val available = ArrayDeque<ExoPlayer>()
    private val inUse = mutableSetOf<ExoPlayer>()

    @Synchronized
    fun acquire(context: Context): ExoPlayer {
        val appContext = context.applicationContext
        val player = available.removeFirstOrNull() ?: ExoPlayer.Builder(appContext).build()
        reset(player)
        inUse.add(player)
        return player
    }

    @Synchronized
    fun release(player: ExoPlayer) {
        // A duplicate release must not destroy an instance already in the pool.
        if (!inUse.remove(player)) return
        reset(player)
        if (available.size < DEFAULT_MAX_SIZE) {
            available.addLast(player)
        } else {
            runCatching { player.release() }
        }
    }

    @Synchronized
    fun pauseAll() {
        inUse.forEach { it.pause() }
    }

    @Synchronized
    fun releaseAll() {
        (available + inUse).forEach { runCatching { it.release() } }
        available.clear()
        inUse.clear()
    }

    @Synchronized
    fun inUseCount(): Int = inUse.size

    @Synchronized
    fun availableCount(): Int = available.size

    private fun reset(player: ExoPlayer) {
        runCatching {
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
            player.volume = 1f
            player.setPlaybackSpeed(1f)
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
    }
}

/**
 * Acquires a pooled player, binds [mediaUrl], and pauses / recycles it
 * with the composition and the host [androidx.lifecycle.Lifecycle].
 */
@Composable
fun rememberPooledExoPlayer(
    mediaUrl: String,
    playWhenReady: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    volume: Float = 1f,
    prepareMedia: Boolean = true
): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { ExoPlayerPool.acquire(context) }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val latestPlayWhenReady = rememberUpdatedState(playWhenReady)
    val shouldPrepare = prepareMedia && resumed
    var boundUrl by remember { mutableStateOf("") }
    var resumePosition by remember { mutableLongStateOf(0L) }
    RememberPlaybackMetrics(player, playWhenReady && resumed)

    LaunchedEffect(mediaUrl, shouldPrepare) {
        if (boundUrl != mediaUrl) {
            resumePosition = 0L
            boundUrl = mediaUrl
        } else if (player.mediaItemCount > 0) {
            resumePosition = player.currentPosition.coerceAtLeast(0L)
        }
        player.stop()
        player.clearMediaItems()
        if (mediaUrl.isBlank() || !shouldPrepare) return@LaunchedEffect
        if (VideoCacheManager.isCacheable(mediaUrl)) {
            player.setMediaSource(VideoCacheManager.buildCachedMediaSource(context, mediaUrl))
        } else {
            player.setMediaItem(MediaItem.fromUri(mediaUrl))
        }
        player.seekTo(resumePosition)
        player.prepare()
        player.playWhenReady = latestPlayWhenReady.value && resumed
    }

    LaunchedEffect(repeatMode) {
        player.repeatMode = repeatMode
    }

    LaunchedEffect(volume) {
        player.volume = volume
    }

    LaunchedEffect(playWhenReady, resumed) {
        val allowed = playWhenReady && resumed
        player.playWhenReady = allowed
        if (allowed) player.play() else player.pause()
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            // Pause immediately; do not wait for the next recomposition.
            if (!resumed) {
                player.playWhenReady = false
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(player) {
        onDispose { ExoPlayerPool.release(player) }
    }
    return player
}

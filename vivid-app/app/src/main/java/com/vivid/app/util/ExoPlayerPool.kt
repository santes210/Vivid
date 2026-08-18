package com.vivid.app.util

import android.content.Context
import androidx.compose.runtime.Composable
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
        if (!inUse.remove(player)) {
            runCatching { player.release() }
            return
        }
        reset(player)
        if (available.size < DEFAULT_MAX_SIZE) {
            available.addLast(player)
        } else {
            runCatching { player.release() }
        }
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
    volume: Float = 1f
): ExoPlayer {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember { ExoPlayerPool.acquire(context) }
    val latestPlayWhenReady = rememberUpdatedState(playWhenReady)

    LaunchedEffect(mediaUrl) {
        player.stop()
        player.clearMediaItems()
        if (mediaUrl.isBlank()) return@LaunchedEffect
        if (VideoCacheManager.isCacheable(mediaUrl)) {
            player.setMediaSource(VideoCacheManager.buildCachedMediaSource(context, mediaUrl))
        } else {
            player.setMediaItem(MediaItem.fromUri(mediaUrl))
        }
        player.prepare()
        player.playWhenReady = latestPlayWhenReady.value
    }

    LaunchedEffect(repeatMode) {
        player.repeatMode = repeatMode
    }

    LaunchedEffect(volume) {
        player.volume = volume
    }

    LaunchedEffect(playWhenReady) {
        player.playWhenReady = playWhenReady
        if (playWhenReady) player.play() else player.pause()
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    if (latestPlayWhenReady.value) {
                        player.playWhenReady = true
                        player.play()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ExoPlayerPool.release(player)
        }
    }
    return player
}

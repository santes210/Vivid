package com.vivid.app.util

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/** Aggregate metrics only: never attach media URLs, account IDs or chat contents. */
private fun startTrace(name: String): Trace? = runCatching {
    val perf = FirebasePerformance.getInstance()
    if (perf.isPerformanceCollectionEnabled) perf.newTrace(name).also { it.start() } else null
}.getOrNull()

@Composable
fun RememberPlaybackMetrics(player: ExoPlayer, active: Boolean) {
    DisposableEffect(player, active) {
        val trace = if (active) startTrace("video_visible_session") else null
        val startedAt = SystemClock.elapsedRealtime()
        var firstFrame = false
        var bufferStarted: Long? = null
        var bufferMs = 0L
        var dropped = 0L
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!firstFrame) {
                    firstFrame = true
                    trace?.putMetric("first_frame_ms", SystemClock.elapsedRealtime() - startedAt)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val now = SystemClock.elapsedRealtime()
                if (playbackState == Player.STATE_BUFFERING && bufferStarted == null) {
                    bufferStarted = now
                    trace?.incrementMetric("buffer_events", 1)
                } else if (playbackState != Player.STATE_BUFFERING) {
                    bufferStarted?.let { bufferMs += now - it }
                    bufferStarted = null
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) { dropped += droppedFrames }
        }
        if (active) {
            player.addListener(listener)
            player.addAnalyticsListener(analytics)
            listener.onPlaybackStateChanged(player.playbackState)
        }
        onDispose {
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            bufferStarted?.let { bufferMs += SystemClock.elapsedRealtime() - it }
            trace?.putMetric("buffer_ms", bufferMs)
            trace?.putMetric("dropped_frames", dropped)
            trace?.putMetric("rendered_first_frame", if (firstFrame) 1L else 0L)
            trace?.stop()
        }
    }
}

/** Time to first post in the feed layout, not time to finish downloading its image. */
@Composable
fun MeasureFirstFeedPost(visible: Boolean) {
    val trace = remember { startTrace("feed_first_post_layout") }
    val completed = remember { booleanArrayOf(false) }
    LaunchedEffect(visible) {
        if (visible && !completed[0]) {
            completed[0] = true
            trace?.putMetric("completed", 1)
            trace?.stop()
        }
    }
    DisposableEffect(trace) {
        onDispose {
            if (!completed[0]) {
                trace?.putMetric("completed", 0)
                trace?.stop()
            }
        }
    }
}

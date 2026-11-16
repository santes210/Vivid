package com.vivid.app.presentation.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vivid.app.R
import com.vivid.app.util.rememberPooledExoPlayer

/** One viewer per chat screen, not one auto-playing decoder per message bubble. */
@Composable
internal fun ChatReelViewer(
    reelId: String,
    onClose: () -> Unit,
    collection: String = "reels",
    onUnsave: (() -> Unit)? = null,
    viewModel: ChatReelViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(reelId, collection, viewModel) {
        com.vivid.app.util.ExoPlayerPool.pauseAll()
        viewModel.load(reelId, collection)
        onDispose { viewModel.close() }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.content_video_title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (onUnsave != null) IconButton(onClick = onUnsave) {
                        Icon(Icons.Default.BookmarkRemove, stringResource(R.string.profile_unsave))
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.content_video_close)) }
                }
                when (val current = state) {
                    ChatReelState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    ChatReelState.Unavailable -> ChatReelError { viewModel.load(reelId, collection) }
                    is ChatReelState.Ready -> if (current.video.id == reelId && current.video.collection == collection) {
                        key(current.video.id, current.video.url) {
                            if (current.video.isVideo) ChatReelPlayer(current.video, onRetry = { viewModel.load(reelId, collection) })
                            else ChatPostImage(current.video, onRetry = { viewModel.load(reelId, collection) })
                        }
                    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun ChatReelError(onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.content_video_unavailable))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.reel_share_retry)) }
    }
}

@Composable
private fun ChatReelPlayer(video: ChatReelVideo, onRetry: () -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val animationsEnabled = com.vivid.app.theme.LocalVividAnimationsEnabled.current
    var playRequested by remember { mutableStateOf(true) }
    var showShare by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var fill by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seekable by remember { mutableStateOf(false) }
    var actuallyPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    var feedbackSequence by remember { mutableIntStateOf(0) }
    val backwardLabel = stringResource(R.string.viewer_back_ten)
    val forwardLabel = stringResource(R.string.viewer_forward_ten)
    val player = rememberPooledExoPlayer(
        mediaUrl = video.url,
        playWhenReady = playRequested && !failed && !showShare,
        repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF,
        volume = if (muted) 0f else 1f
    )
    fun seek(target: Long) {
        if (player.isCurrentMediaItemSeekable && player.duration > 0) {
            player.seekTo(target.coerceIn(0, player.duration))
            position = target.coerceIn(0, player.duration)
        }
    }
    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
    LaunchedEffect(feedbackSequence) {
        if (seekFeedback != null) { delay(800); seekFeedback = null }
    }
    // Poll only the visible controls while foregrounded, never in the background.
    LaunchedEffect(player, lifecycleOwner, controlsVisible, locked) {
        if (!controlsVisible || locked) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (isActive) {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
                seekable = player.isCurrentMediaItemSeekable
                delay(250)
            }
        }
    }
    if (showShare) {
        com.vivid.app.presentation.reels.ContentShareSheet(
            content = com.vivid.app.presentation.reels.ShareContent(
                "vivid://${if (video.collection == "posts") "post" else "reel"}/${video.id}", video.caption),
            speed = speed, onSpeed = { speed = it }, muted = muted, onMuted = { muted = it },
            repeat = repeat, onRepeat = { repeat = it },
            onRestart = { seek(0); playRequested = true; showShare = false },
            onDismiss = { showShare = false }
        )
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { actuallyPlaying = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                ended = playbackState == Player.STATE_ENDED
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (failed) ChatReelError(onRetry)
    else AdaptiveVideoLayout(showControls = controlsVisible && !locked, media = { mediaModifier ->
        Box(mediaModifier.clip(MaterialTheme.shapes.extraLarge).background(Color.Black)) {
            AndroidView(
                factory = { context -> PlayerView(context).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                } },
                update = {
                    it.player = player
                    it.resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { it.player = null }, modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.matchParentSize().pointerInput(player, locked, backwardLabel, forwardLabel) {
                detectTapGestures(
                    onTap = { if (!locked) controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (!locked && player.isCurrentMediaItemSeekable && player.duration > 0) {
                            val forward = offset.x > size.width / 2
                            seek(VideoControlMath.seekTarget(player.currentPosition, player.duration, forward))
                            seekFeedback = if (forward) forwardLabel else backwardLabel
                            feedbackSequence++
                        }
                    }
                )
            })
            if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!locked) FilledTonalIconButton(onClick = { controlsVisible = !controlsVisible }) {
                    Icon(if (controlsVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        stringResource(if (controlsVisible) R.string.viewer_hide_controls else R.string.viewer_show_controls))
                }
                FilledTonalIconButton(onClick = { locked = !locked; if (!locked) controlsVisible = true }) {
                    Icon(if (locked) Icons.Default.LockOpen else Icons.Default.Lock,
                        stringResource(if (locked) R.string.viewer_unlock else R.string.viewer_lock))
                }
            }
            AnimatedVisibility(visible = seekFeedback != null, modifier = Modifier.align(Alignment.Center),
                enter = if (animationsEnabled) fadeIn() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() else ExitTransition.None) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                    Text(seekFeedback.orEmpty(), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (locked) Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(stringResource(R.string.viewer_locked), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }, controls = {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text("@${video.username}", style = MaterialTheme.typography.titleSmall)
                if (video.caption.isNotBlank()) Text(video.caption, maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ExpressiveVideoControls(position, duration, seekable, actuallyPlaying || (playRequested && buffering), ended,
                muted, repeat, speed, fill, onSeek = { seek(it) },
                onPlayPause = {
                    if (ended) { seek(0); playRequested = true; player.play() }
                    else playRequested = !playRequested
                }, onMuted = { muted = !muted }, onRepeat = { repeat = !repeat },
                onSpeed = { speed = it }, onFill = { fill = !fill }, onShare = { showShare = true })
    })
}

@Composable
private fun ChatPostImage(content: ChatReelVideo, onRetry: () -> Unit) {
    var fill by remember { mutableStateOf(false) }
    var failed by remember(content.url, content.imageBase64) { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    if (share) com.vivid.app.presentation.reels.ContentShareSheet(
        content = com.vivid.app.presentation.reels.ShareContent("vivid://post/${content.id}", content.caption),
        playbackControls = false, onDismiss = { share = false }
    )
    val imageBytes by produceState<ByteArray?>(null, content.imageBase64) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (content.imageBase64.isBlank()) null else runCatching {
                android.util.Base64.decode(content.imageBase64, android.util.Base64.DEFAULT)
            }.getOrNull()
        }
        if (content.imageBase64.isNotBlank() && value == null) failed = true
    }
    if (failed) ChatReelError(onRetry)
    else Column(Modifier.fillMaxSize()) {
        coil3.compose.AsyncImage(model = if (content.imageBase64.isNotBlank()) imageBytes else content.url, contentDescription = content.caption,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentScale = if (fill) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit,
            onError = { if (content.imageBase64.isBlank() || imageBytes != null) failed = true })
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("@${content.username}", style = MaterialTheme.typography.titleMedium)
                if (content.caption.isNotBlank()) Text(content.caption, maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { fill = !fill }) {
                    Text(stringResource(if (fill) R.string.viewer_fit else R.string.viewer_fill))
                }
            }
        }
        TextButton(onClick = { share = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.content_share_title))
        }
    }
}

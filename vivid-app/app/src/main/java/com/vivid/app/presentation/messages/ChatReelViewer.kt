package com.vivid.app.presentation.messages

import androidx.compose.foundation.background
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
    var playRequested by remember { mutableStateOf(true) }
    var showShare by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    // Opening the viewer is an explicit play action, even with data saver enabled.
    val player = rememberPooledExoPlayer(
        mediaUrl = video.url,
        playWhenReady = playRequested && !failed && !showShare,
        repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF,
        volume = if (muted) 0f else 1f
    )
    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
    if (showShare) {
        com.vivid.app.presentation.reels.ContentShareSheet(
            content = com.vivid.app.presentation.reels.ShareContent(
                "vivid://${if (video.collection == "posts") "post" else "reel"}/${video.id}", video.caption),
            speed = speed, onSpeed = { speed = it }, muted = muted, onMuted = { muted = it },
            repeat = repeat, onRepeat = { repeat = it },
            onRestart = { player.seekTo(0); playRequested = true; showShare = false },
            onDismiss = { showShare = false }
        )
    }
    val sheetOpen = rememberUpdatedState(showShare)
    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!sheetOpen.value && lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                    playRequested = playWhenReady
                }
            }
            override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false }
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    if (failed) {
        ChatReelError(onRetry)
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                AndroidView(
                    factory = { context -> PlayerView(context).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    } },
                    update = { it.player = player },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            Text("@${video.username}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleSmall)
            if (video.caption.isNotBlank()) Text(video.caption, Modifier.padding(horizontal = 16.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall)
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = { showShare = true }, label = { Text(stringResource(R.string.content_share_title)) }) }
                item { FilterChip(selected = muted, onClick = { muted = !muted }, label = { Text(stringResource(R.string.reel_tools_mute)) }) }
                item { FilterChip(selected = repeat, onClick = { repeat = !repeat }, label = { Text(stringResource(R.string.reel_tools_repeat)) }) }
                items(listOf(0.5f, 1f, 1.5f, 2f)) { value ->
                    FilterChip(selected = speed == value, onClick = { speed = value }, label = { Text("${value}×") })
                }
            }
        }
    }
}

@Composable
private fun ChatPostImage(content: ChatReelVideo, onRetry: () -> Unit) {
    var failed by remember(content.url) { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    if (share) com.vivid.app.presentation.reels.ContentShareSheet(
        content = com.vivid.app.presentation.reels.ShareContent("vivid://post/${content.id}", content.caption),
        playbackControls = false, onDismiss = { share = false }
    )
    if (failed) ChatReelError(onRetry)
    else Column(Modifier.fillMaxSize()) {
        coil3.compose.AsyncImage(model = content.url, contentDescription = content.caption,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit, onError = { failed = true })
        TextButton(onClick = { share = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.content_share_title))
        }
    }
}

package com.vivid.app.presentation.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes

/** Stateless player actions keep accessibility/testing independent of ExoPlayer. */
@Composable
internal fun ExpressiveVideoControls(
    positionMs: Long,
    durationMs: Long,
    seekable: Boolean,
    playing: Boolean,
    ended: Boolean,
    muted: Boolean,
    repeat: Boolean,
    speed: Float,
    fill: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onMuted: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: (Float) -> Unit,
    onFill: () -> Unit,
    onShare: () -> Unit
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0)
    val canSeek = seekable && duration > 0
    val seekDescription = stringResource(R.string.viewer_seek)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { onSeek(VideoControlMath.seekTarget(positionMs, duration, false)) }, enabled = canSeek,
                    modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Replay10, stringResource(R.string.viewer_back_ten)) }
                Spacer(Modifier.width(24.dp))
                FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp), shape = VividExpressiveShapes.MediumCard) {
                    Icon(if (ended) Icons.Default.Replay else if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        stringResource(if (ended) R.string.reel_tools_restart else if (playing) R.string.viewer_pause else R.string.viewer_play),
                        modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(24.dp))
                FilledTonalIconButton(onClick = { onSeek(VideoControlMath.seekTarget(positionMs, duration, true)) }, enabled = canSeek,
                    modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Forward10, stringResource(R.string.viewer_forward_ten)) }
            }
            Slider(value = (scrubPosition ?: positionMs.coerceIn(0, duration).toFloat()).coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = { scrubPosition?.let { onSeek(it.toLong().coerceIn(0, duration)) }; scrubPosition = null },
                enabled = canSeek, valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekDescription })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(VideoControlMath.timeLabel(scrubPosition?.toLong() ?: positionMs), style = MaterialTheme.typography.labelMedium)
                Text(VideoControlMath.timeLabel(duration), style = MaterialTheme.typography.labelMedium)
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                item { AssistChip(onClick = onShare, label = { Text(stringResource(R.string.content_share_title)) }, leadingIcon = { Icon(Icons.Default.Share, null) }) }
                item { FilterChip(selected = muted, onClick = onMuted, label = { Text(stringResource(R.string.reel_tools_mute)) }, leadingIcon = { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null) }) }
                item { FilterChip(selected = fill, onClick = onFill, label = { Text(stringResource(if (fill) R.string.viewer_fit else R.string.viewer_fill)) }, leadingIcon = { Icon(Icons.Default.AspectRatio, null) }) }
                item { FilterChip(selected = repeat, onClick = onRepeat, label = { Text(stringResource(R.string.reel_tools_repeat)) }) }
                items(listOf(0.5f, 1f, 1.5f, 2f)) { value ->
                    FilterChip(selected = speed == value, onClick = { onSpeed(value) }, label = { Text("${value}×") })
                }
            }
        }
    }
}

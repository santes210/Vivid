package com.vivid.app.presentation.messages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.vivid.app.R

@Composable
internal fun ZoomablePostImage(content: ChatReelVideo, onRetry: () -> Unit, onDeleted: () -> Unit = {}) {
    var zoom by remember(content.id, content.url) { mutableStateOf(ImageZoom()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var fill by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    var expandedCaption by remember { mutableStateOf(false) }
    val imageBytes by produceState<ByteArray?>(null, content.imageBase64) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            if (content.imageBase64.isBlank()) null else runCatching {
                android.util.Base64.decode(content.imageBase64, android.util.Base64.DEFAULT)
            }.getOrNull()
        }
        if (content.imageBase64.isNotBlank() && value == null) failed = true
    }
    LaunchedEffect(loading) {
        if (loading) { kotlinx.coroutines.delay(15_000); if (loading) failed = true }
    }
    LaunchedEffect(viewport, fill) { zoom = ImageZoom() }
    if (share) com.vivid.app.presentation.reels.ContentShareSheet(
        content = com.vivid.app.presentation.reels.ShareContent("vivid://post/${content.id}", content.caption, content.ownerId),
        onDeleted = onDeleted, playbackControls = false, onDismiss = { share = false }
    )
    if (failed) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.image_load_failed))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        return
    }
    fun resize(factor: Float) {
        zoom = zoom.transform(factor, 0f, 0f, viewport.width.toFloat(), viewport.height.toFloat())
    }
    AdaptiveVideoLayout(showControls = true, media = { mediaModifier ->
        Box(mediaModifier.clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(loading) {
                if (!loading) detectTransformGestures { centroid, pan, factor, _ ->
                    zoom = zoom.transform(factor, pan.x, pan.y, size.width.toFloat(), size.height.toFloat(),
                        centroid.x - size.width / 2f, centroid.y - size.height / 2f)
                }
            }
            .pointerInput(loading) {
                if (!loading) detectTapGestures(onDoubleTap = { point ->
                    zoom = if (zoom.scale > 1f) ImageZoom() else zoom.transform(
                        2.5f, 0f, 0f, size.width.toFloat(), size.height.toFloat(),
                        point.x - size.width / 2f, point.y - size.height / 2f)
                })
            }, contentAlignment = Alignment.Center) {
            if (content.imageBase64.isBlank() || imageBytes != null) {
                coil3.compose.AsyncImage(
                    model = if (content.imageBase64.isNotBlank()) imageBytes else content.url,
                    contentDescription = content.altText.ifBlank { content.caption }.ifBlank { stringResource(R.string.image_description) },
                    contentScale = if (fill) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.scale; scaleY = zoom.scale
                        translationX = zoom.x; translationY = zoom.y
                    },
                    onSuccess = { loading = false },
                    onError = { loading = false; failed = true }
                )
            }
            if (loading) CircularProgressIndicator()
        }
    }, controls = {
        Text("@${content.username}", style = MaterialTheme.typography.titleMedium)
        if (content.caption.isNotBlank()) {
            Text(content.caption, maxLines = if (expandedCaption) Int.MAX_VALUE else 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { expandedCaption = !expandedCaption }) {
                Text(stringResource(if (expandedCaption) R.string.image_caption_less else R.string.image_caption_more))
            }
        }
        ImageZoomControls(zoom.scale, !loading, onZoomIn = { resize(1.5f) },
            onZoomOut = { resize(1f / 1.5f) }, onReset = { zoom = ImageZoom(); fill = false })
        Text(stringResource(R.string.image_zoom_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { fill = !fill }, enabled = !loading) {
            Text(stringResource(if (fill) R.string.viewer_fit else R.string.viewer_fill))
        }
        FilledTonalButton(onClick = { share = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.content_share_title))
        }
    })
}

/** Buttons provide an alternative to multi-touch gestures for accessibility. */
@Composable
internal fun ImageZoomControls(scale: Float, enabled: Boolean,
    onZoomIn: () -> Unit, onZoomOut: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        FilledTonalIconButton(onClick = onZoomOut, enabled = enabled && scale > 1f) {
            Icon(Icons.Default.ZoomOut, stringResource(R.string.image_zoom_out))
        }
        Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        FilledTonalIconButton(onClick = onZoomIn, enabled = enabled && scale < 4f) {
            Icon(Icons.Default.ZoomIn, stringResource(R.string.image_zoom_in))
        }
        FilledTonalIconButton(onClick = onReset, enabled = enabled) {
            Icon(Icons.Default.RestartAlt, stringResource(R.string.image_zoom_reset))
        }
    }
}

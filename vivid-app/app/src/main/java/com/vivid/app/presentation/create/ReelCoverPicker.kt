package com.vivid.app.presentation.create

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.util.VideoThumbnailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Selection uses source-video time; publishing converts it to the trimmed timeline. */
@Composable
internal fun ReelCoverPicker(uri: Uri, trimStartMs: Long, trimEndMs: Long,
    selectedTimeMs: Long?, enabled: Boolean, onTimeSelected: (Long) -> Unit) {
    val context = LocalContext.current
    var duration by remember(uri) { mutableLongStateOf(0L) }
    LaunchedEffect(uri) { duration = VideoThumbnailer.durationMs(context, uri) }
    val start = trimStartMs.coerceAtLeast(0L)
    val end = (if (trimEndMs > start) minOf(trimEndMs, duration) else duration) - 1L
    val position = (selectedTimeMs ?: (start + 1000L)).coerceIn(start, end.coerceAtLeast(start))
    LaunchedEffect(position) { if (end >= start && selectedTimeMs != position) onTimeSelected(position) }
    var previewLoading by remember { mutableStateOf(true) }
    val preview by produceState<android.graphics.Bitmap?>(null, uri, position) {
        value = null
        previewLoading = true
        delay(250) // Debounce frame extraction while scrubbing.
        val file = File.createTempFile("cover_preview_", ".jpg", context.cacheDir)
        try {
            val extracted = VideoThumbnailer.extract(context, uri, file, targetWidth = 320, frameTimeMs = position)
            value = withContext(Dispatchers.IO) { extracted?.let { BitmapFactory.decodeFile(it.absolutePath) } }
        } finally { file.delete(); previewLoading = false }
    }
    val label = stringResource(R.string.reel_cover_title)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            preview?.let { Image(it.asImageBitmap(), label, Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit) }
                ?: Text(stringResource(if (previewLoading) R.string.reel_cover_preview_loading else R.string.reel_cover_preview_failed), style = MaterialTheme.typography.bodySmall)
            if (end > start) {
                Slider(value = position.toFloat(), onValueChange = { onTimeSelected(it.toLong()) },
                    enabled = enabled, valueRange = start.toFloat()..end.toFloat(),
                    modifier = Modifier.semantics { contentDescription = label })
                Text(stringResource(R.string.reel_cover_at, (position - start) / 1000f), style = MaterialTheme.typography.labelMedium)
            }
            Text(stringResource(R.string.reel_cover_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

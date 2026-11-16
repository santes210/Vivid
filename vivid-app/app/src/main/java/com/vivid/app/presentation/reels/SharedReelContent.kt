package com.vivid.app.presentation.reels

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import kotlinx.coroutines.CancellationException

@Composable
internal fun SharedReelContent(id: String, viewModel: ReelsViewModel, onClose: () -> Unit) {
    var reel by remember(id) { mutableStateOf<Reel?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var retry by remember(id) { mutableIntStateOf(0) }
    LaunchedEffect(id, retry) {
        loading = true
        reel = null
        try {
            reel = viewModel.fetchSharedReel(id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reel = null
        } finally {
            loading = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        val loaded = reel
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loaded != null -> ReelPage(loaded, isPlaying = true, viewModel = viewModel)
            else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.reel_share_unavailable))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.reel_share_retry)) }
            }
        }
        FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
            Icon(Icons.Default.Close, stringResource(R.string.reel_share_close))
        }
    }
}

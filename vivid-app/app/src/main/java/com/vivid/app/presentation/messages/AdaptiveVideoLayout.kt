package com.vivid.app.presentation.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keep video visible with large text; landscape uses a scrollable side dock. */
@Composable
internal fun AdaptiveVideoLayout(
    showControls: Boolean,
    media: @Composable (Modifier) -> Unit,
    controls: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (maxWidth > maxHeight && maxWidth >= 520.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                media(Modifier.weight(1f).fillMaxHeight())
                if (showControls) Column(Modifier.widthIn(max = 340.dp).weight(0.85f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        } else {
            val maximumDockHeight = maxHeight * 0.56f
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                media(Modifier.weight(1f).fillMaxWidth())
                if (showControls) Column(Modifier.fillMaxWidth().heightIn(max = maximumDockHeight)
                    .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() }
            }
        }
    }
}

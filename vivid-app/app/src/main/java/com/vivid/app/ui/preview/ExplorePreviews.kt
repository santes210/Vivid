package com.vivid.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.presentation.explore.ExploreTagChip
import com.vivid.app.presentation.explore.ExploreTopicHeader
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.presentation.explore.ExplorePostTile
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.HashtagCaption

@VividPreviewA11y
@Composable
private fun HashtagCaptionPreview() {
    VividPreviewSurface {
        HashtagCaption(
            text = "Atardecer en la costa. #viaje #Música y un poco de #arte.",
            onHashtagClick = {}
        )
    }
}

@VividPreview
@Composable
private fun ExploreTagChipsPreview() {
    VividPreviewSurface {
        var selected by remember { mutableStateOf("arte") }
        Column(verticalArrangement = Arrangement.spacedBy(VividSpace.s)) {
            ExploreTopicHeader(tag = selected)
            Row(horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
                ExplorePaging.TAGS.take(4).forEach { tag ->
                    ExploreTagChip(
                        tag = tag,
                        selected = selected == tag,
                        onClick = { selected = tag }
                    )
                }
            }
        }
    }
}

@VividPreview
@Composable
private fun ExplorePostTilePreview() {
    VividPreviewSurface(padding = 8) {
        ExplorePostTile(
            post = PostData(
                id = "preview-explore",
                userId = "u1",
                username = "ana.paredes",
                userProfilePicture = "",
                caption = "Hola #viaje",
                timestamp = 0L,
                isVideo = true
            ),
            featured = true,
            onClick = {}
        )
    }
}

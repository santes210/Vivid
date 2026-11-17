package com.vivid.app.presentation.feed

import androidx.compose.runtime.Composable
import com.vivid.app.presentation.reels.ContentShareSheet
import com.vivid.app.presentation.reels.ShareContent

/** Post links do not include media URLs or grant access to private content. */
@Composable
internal fun PostShareSheet(post: PostData, onDismiss: () -> Unit) {
    ContentShareSheet(
        content = ShareContent("vivid://post/${post.id}", post.caption, post.userId),
        playbackControls = false,
        onDismiss = onDismiss
    )
}

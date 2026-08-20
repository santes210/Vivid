package com.vivid.app.presentation.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vivid.app.ui.preview.VividPreview
import com.vivid.app.ui.preview.VividPreviewA11y
import com.vivid.app.ui.preview.VividPreviewSurface

/**
 * Previews de la tarjeta del feed.
 *
 * `PostCard` es el componente más visto de la app y hasta ahora no se podía
 * mirar sin arrancar sesión y esperar a Firestore. Aquí se ve en seco, con
 * datos falsos, en claro/oscuro y con fuente grande — que es donde se rompen
 * los nombres largos y los pies de foto.
 *
 * Los previews son interactivos: el like responde con su rebote y el doble
 * toque sobre la zona de la imagen suelta el corazón.
 */

private fun samplePost(
    isLiked: Boolean = false,
    caption: String = "Atardecer en la costa. Tres horas de camino para esto y volvería mañana.",
    username: String = "ana.paredes"
) = PostData(
    id = "preview-1",
    userId = "u1",
    username = username,
    userProfilePicture = "",
    imageUrl = "",
    caption = caption,
    likesCount = 128,
    commentsCount = 12,
    timestamp = System.currentTimeMillis(),
    isLiked = isLiked,
    isSaved = false
)

@VividPreviewA11y
@Composable
private fun PostCardPreview() {
    VividPreviewSurface(padding = 0) {
        var post by remember { mutableStateOf(samplePost()) }
        PostCard(
            post = post,
            currentUserId = "me",
            isFollowingAuthor = false,
            hasPendingRequestToAuthor = false,
            onOpenPost = {},
            onOpenComments = {},
            onOpenDetails = {},
            onEditPost = {},
            onDeletePost = {},
            onToggleFollow = {},
            onToggleSave = { post = post.copy(isSaved = !post.isSaved) },
            onToggleLike = {
                val liked = !post.isLiked
                post = post.copy(
                    isLiked = liked,
                    likesCount = (post.likesCount + if (liked) 1 else -1).coerceAtLeast(0)
                )
            },
            onShare = {}
        )
    }
}

@VividPreview
@Composable
private fun PostCardOwnPostPreview() {
    VividPreviewSurface(padding = 0) {
        PostCard(
            post = samplePost(isLiked = true, caption = "").copy(userId = "me"),
            currentUserId = "me",
            isFollowingAuthor = false,
            hasPendingRequestToAuthor = false,
            onOpenPost = {},
            onOpenComments = {},
            onOpenDetails = {},
            onEditPost = {},
            onDeletePost = {},
            onToggleFollow = {},
            onToggleSave = {},
            onToggleLike = {},
            onShare = {}
        )
    }
}

@VividPreview
@Composable
private fun FeedSkeletonPreview() {
    VividPreviewSurface(padding = 0) {
        Column { repeat(2) { FeedSkeleton() } }
    }
}

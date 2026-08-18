package com.vivid.app.presentation.feed

/**
 * Data models used across the feed feature.
 * Extracted from FeedScreen.kt for reuse and testability.
 */

data class PostData(
    val id: String, val userId: String, val username: String,
    val userProfilePicture: String, val userProfilePictureBase64: String = "",
    val imageUrl: String = "", val imageBase64: String = "",
    val storageKey: String = "",
    val videoUrl: String = "", val thumbnailUrl: String = "",
    val isVideo: Boolean = false, val caption: String,
    val likesCount: Int = 0, val commentsCount: Int = 0,
    val timestamp: Long, val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    // Optional music
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicAssetFile: String = "",
    val musicUrl: String = "",
    val musicStorageKey: String = ""
)

data class PostComment(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val timestamp: Long,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isEdited: Boolean = false,
    val parentId: String? = null,
    val replyToUsername: String = ""
)

internal data class FeedPageResult(
    val posts: List<PostData>,
    val lastDoc: com.google.firebase.firestore.DocumentSnapshot?
)

/** Minimum interval between full Room post cache writes (60 s). */
internal const val FEED_CACHE_WRITE_INTERVAL_MS = 60_000L

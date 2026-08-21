package com.vivid.shared.model

import kotlinx.serialization.Serializable

/**
 * Modelo de dominio para publicaciones (posts).
 * Compartido entre Android e iOS.
 */
@Serializable
data class Post(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userProfilePicture: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val caption: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long = 0L,
    val isLiked: Boolean = false,
    val storageKey: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val isPrivate: Boolean = false,
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicAssetFile: String = "",
    val musicUrl: String = "",
    val musicStorageKey: String = ""
)

/**
 * Modelo de dominio para reels.
 */
@Serializable
data class Reel(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val userAvatar: String = "",
    val videoUrl: String = "",
    val videoUrlExpiresAt: Long = 0L,
    val thumbnailUrl: String = "",
    val caption: String = "",
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = ""
)

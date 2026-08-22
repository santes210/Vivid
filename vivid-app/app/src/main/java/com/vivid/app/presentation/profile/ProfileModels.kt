package com.vivid.app.presentation.profile

/**
 * Data models used across the profile feature.
 * Extracted from ProfileScreen.kt for reuse and testability.
 */

data class ProfileUiState(
    val uid: String = "",
    val username: String = "vivid_user",
    val displayName: String = "Usuario Vivid",
    val bio: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val postsCount: Int = 0,
    val reelsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isPrivate: Boolean = false,
    val isFollowedByCurrentUser: Boolean = false,
    val isCurrentUser: Boolean = false,
    val isFollowRequestPending: Boolean = false,
    // Cuenta verificada: la lee ProfileScreen de Firestore (`isVerified`). Por
    // defecto `false`, así que el badge (VividVerifiedBadge) solo aparece para
    // quien tenga la marca — hoy nadie, pero el componente ya está listo.
    val isVerified: Boolean = false
)

@androidx.compose.runtime.Immutable
data class ProfilePost(
    val id: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val storageKey: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val caption: String = "",
    val timestamp: Long = 0L,
    val username: String = "",
    val isSaved: Boolean = false
)

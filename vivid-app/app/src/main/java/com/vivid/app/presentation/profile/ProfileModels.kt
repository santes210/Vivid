package com.vivid.app.presentation.profile

/**
 * Data models used across the profile feature.
 * Extracted from ProfileScreen.kt for reuse and testability.
 */

data class ProfileUiState(
    val uid: String = "",
    val pinnedContent: List<String> = emptyList(),
    val username: String = "vivid_user",
    val displayName: String = "Usuario Vivid",
    val bio: String = "",
    val website: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val postsCount: Int? = null,
    val reelsCount: Int? = null,
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
    val durationMs: Long = 0L,
    val altText: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val storageKey: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val caption: String = "",
    val timestamp: Long = 0L,
    val username: String = "",
    val isSaved: Boolean = false,
    val collection: String = "posts",
    val visibility: String = "public"
)

/** Unknown/denied/failed loads are not a count of zero. Never use legacy counters. */
internal fun profileContentCount(
    content: List<ProfilePost>, loaded: Boolean, failed: Boolean, canView: Boolean
): Int? = if (loaded && !failed && canView) content.distinctBy { it.collection to it.id }.size else null

internal fun ProfilePost.contentKey(): String = "$collection/$id"

/** Exact collection/id identities avoid collisions between a post and a reel. */
internal fun updatedProfilePins(current: List<String>, key: String): List<String> {
    val pins = current.distinct()
    if (key in pins) return pins - key
    require(pins.size < 3) { "pin_limit" }
    return pins + key
}

internal fun orderProfileContent(content: List<ProfilePost>, pins: List<String>): List<ProfilePost> =
    content.sortedWith(compareBy<ProfilePost> {
        pins.indexOf(it.contentKey()).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
    }.thenByDescending { it.timestamp })

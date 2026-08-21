package com.vivid.shared.model

import kotlinx.serialization.Serializable

/**
 * Modelo de dominio para chats (conversaciones).
 * Compartido entre Android e iOS.
 */
@Serializable
data class Chat(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserAvatar: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCount: Int = 0,
    val lastMessageSenderId: String = "",
    val lastMessageType: String = "text",
    val avatarBase64: String = ""
)

/**
 * Estado de la relación social entre dos usuarios.
 */
@Serializable
data class FollowRelationshipState(
    val isFollowing: Boolean = false,
    val hasPendingRequest: Boolean = false,
    val isTargetPrivate: Boolean = false,
    val isBlocked: Boolean = false
)

/**
 * Resultado de una acción de follow/unfollow.
 */
@Serializable
enum class FollowActionResult {
    FOLLOWED,
    UNFOLLOWED,
    REQUESTED,
    REQUEST_CANCELLED
}

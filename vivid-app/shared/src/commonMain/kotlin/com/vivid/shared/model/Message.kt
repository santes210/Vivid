package com.vivid.shared.model

import kotlinx.serialization.Serializable

/**
 * Modelo de dominio para mensajes de chat.
 * Compartido entre Android e iOS - sin dependencias de plataforma.
 */
@Serializable
data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val reaction: String = "",
    val type: MessageType = MessageType.TEXT,
    val imageUrl: String = "",
    val imageKey: String = "",
    val voiceUrl: String = "",
    val voiceKey: String = "",
    val voiceDurationMs: Long = 0L,
    val replyToStoryId: String = "",
    val lastEditedAt: Long = 0L
) {
    /** True si el mensaje fue editado al menos una vez. */
    val isEdited: Boolean get() = lastEditedAt > 0L

    /** Solo el emisor puede editar mensajes de texto no vacíos. */
    fun canBeEditedBy(userId: String): Boolean =
        userId.isNotBlank() && senderId == userId && type == MessageType.TEXT && text.isNotBlank()

    /**
     * Claves B2 asociadas a adjuntos del mensaje.
     * Borrar un mensaje propio no deja su binario en el bucket.
     */
    fun mediaStorageKeys(): List<String> = listOf(imageKey, voiceKey)
        .filter { it.isNotBlank() }
        .distinct()
}

/**
 * Tipos de mensaje soportados.
 * Usar enum en vez de String para type-safety en ambas plataformas.
 */
@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    VOICE,
    STORY_REPLY;

    companion object {
        fun fromString(value: String): MessageType = when (value.lowercase()) {
            "text" -> TEXT
            "image" -> IMAGE
            "video" -> VIDEO
            "voice" -> VOICE
            "story_reply" -> STORY_REPLY
            else -> TEXT
        }
    }

    fun toFirestoreString(): String = when (this) {
        TEXT -> "text"
        IMAGE -> "image"
        VIDEO -> "video"
        VOICE -> "voice"
        STORY_REPLY -> "story_reply"
    }
}

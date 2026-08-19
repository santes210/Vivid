package com.vivid.app.presentation.messages

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val reaction: String = "",
    val type: String = "text", // text | image | video | voice | story_reply
    val imageUrl: String = "",
    val imageKey: String = "",
    // Voice note
    val voiceUrl: String = "",
    val voiceKey: String = "",
    val voiceDurationMs: Long = 0L,
    // Story reply reference
    val replyToStoryId: String = "",
    // Última edición (0L = nunca editado). Solo mensajes de texto editables.
    // No se persiste en Room para no forzar migración; vive en Firestore.
    val lastEditedAt: Long = 0L
) {
    /** True si el mensaje fue editado al menos una vez. */
    val isEdited: Boolean get() = lastEditedAt > 0L

    /** Solo el emisor puede editar mensajes de texto no vacíos. */
    fun canBeEditedBy(userId: String): Boolean =
        userId.isNotBlank() && senderId == userId && type == "text" && text.isNotBlank()
}


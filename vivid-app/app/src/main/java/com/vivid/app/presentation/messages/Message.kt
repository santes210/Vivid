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
    val replyToStoryId: String = ""
)

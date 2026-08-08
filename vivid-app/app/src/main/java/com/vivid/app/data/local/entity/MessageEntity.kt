package com.vivid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val type: String = "text", // text, image, video, voice, story_reply
    val imageUrl: String = "",
    val imageKey: String = "",
    val voiceUrl: String = "",
    val voiceKey: String = "",
    val voiceDurationMs: Long = 0L,
    val replyToStoryId: String = ""
)

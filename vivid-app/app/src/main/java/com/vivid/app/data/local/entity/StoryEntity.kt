package com.vivid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class StoryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val mediaUrl: String = "",
    val mediaBase64: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val type: String = "photo",
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = "",
    val viewersCount: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)
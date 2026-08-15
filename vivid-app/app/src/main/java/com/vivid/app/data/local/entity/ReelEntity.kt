package com.vivid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reels")
data class ReelEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val userAvatar: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)
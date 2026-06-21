package com.vivid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val username: String,
    val userProfilePicture: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val caption: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long,
    val isLiked: Boolean = false
)
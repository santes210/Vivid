package com.vivid.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val username: String,
    val displayName: String,
    val bio: String = "",
    val profilePictureUrl: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
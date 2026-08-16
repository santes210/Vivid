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
    /**
     * Momento (epoch ms) en que expira la URL firmada guardada en [videoUrl].
     * 0 = desconocido (no reutilizar, re-firmar en la próxima carga).
     */
    val videoUrlExpiresAt: Long = 0L,
    val thumbnailUrl: String = "",
    val caption: String = "",
    val likes: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = "",
    val cachedAt: Long = System.currentTimeMillis()
)
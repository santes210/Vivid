package com.vivid.app.presentation.reels

/**
 * Modelo unificado de Reel para toda la app.
 */
data class Reel(
    val id: String,
    val userId: String = "",
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val username: String,
    val caption: String,
    val likes: Int,
    val commentsCount: Int = 0,
    val userAvatar: String = "",
    val storageKey: String = ""
)

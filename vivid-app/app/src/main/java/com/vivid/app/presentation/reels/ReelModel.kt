package com.vivid.app.presentation.reels

/**
 * Modelo unificado de Reel para toda la app.
 * Unifica la data class que estaba duplicada en ReelsScreen.kt y ReelsViewModel.kt
 */
data class Reel(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String = "",
    val username: String,
    val caption: String,
    val likes: Int,
    val userAvatar: String = ""
)

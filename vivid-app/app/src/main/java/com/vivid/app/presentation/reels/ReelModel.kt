package com.vivid.app.presentation.reels

import androidx.compose.runtime.Immutable

/**
 * Modelo unificado de Reel para toda la app.
 */
@Immutable
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
    val storageKey: String = "",
    val timestamp: Long = 0L,
    val isPrivate: Boolean = false,
    /**
     * Momento (epoch ms) en que expira la URL firmada de [videoUrl].
     * 0 = desconocido. Se persiste en el caché Room para poder reutilizar la
     * URL entre sesiones sin pedir una nueva a B2 en cada carga.
     */
    val videoUrlExpiresAt: Long = 0L
)

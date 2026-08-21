package com.vivid.shared.model

import kotlinx.serialization.Serializable

/**
 * Modelo de dominio para usuarios.
 * Compartido entre Android e iOS.
 */
@Serializable
data class User(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val email: String = "",
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isPrivate: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Vista previa ligera de un usuario para listas (seguidores, siguiendo, etc.).
 */
@Serializable
data class UserPreview(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = ""
)

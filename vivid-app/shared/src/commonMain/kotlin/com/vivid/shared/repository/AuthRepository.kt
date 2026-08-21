package com.vivid.shared.repository

import com.vivid.shared.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de autenticación.
 * Define el contrato que ambas plataformas deben implementar.
 */
interface AuthRepository {

    /** El UID del usuario actual, o vacío si no hay sesión. */
    val currentUserId: String

    /** Flujo del estado de autenticación (null si no hay sesión). */
    fun observeAuthState(): Flow<User?>

    /**
     * Inicia sesión con Google.
     * @param idToken Token de identidad de Google (obtenido del SDK nativo).
     */
    suspend fun signInWithGoogle(idToken: String): Result<User>

    /**
     * Inicia sesión con Apple (solo iOS, no-op en Android).
     * @param identityToken Token de identidad de Apple.
     */
    suspend fun signInWithApple(identityToken: String): Result<User>

    /** Cierra la sesión actual. */
    suspend fun signOut()

    /** Elimina la cuenta del usuario actual y todos sus datos. */
    suspend fun deleteAccount(): Result<Unit>

    /** Verifica si hay una sesión activa. */
    fun isLoggedIn(): Boolean
}

/**
 * Interfaz del repositorio de perfil de usuario.
 */
interface UserRepository {

    /** Obtiene el perfil completo de un usuario. */
    suspend fun getUser(userId: String): User?

    /** Flujo del perfil del usuario actual. */
    fun observeCurrentUser(): Flow<User?>

    /** Actualiza el perfil del usuario actual. */
    suspend fun updateProfile(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        avatarFilePath: String? = null
    ): Result<Unit>

    /**
     * Cambia la privacidad de la cuenta.
     * Al hacerla privada, todo el contenido existente se marca como privado.
     */
    suspend fun setAccountPrivate(isPrivate: Boolean)

    /** Busca usuarios por nombre de usuario o display name. */
    suspend fun searchUsers(query: String, limit: Int = 20): List<User>
}

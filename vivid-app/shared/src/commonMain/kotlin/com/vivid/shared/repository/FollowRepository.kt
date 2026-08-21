package com.vivid.shared.repository

import com.vivid.shared.model.FollowActionResult
import com.vivid.shared.model.FollowRelationshipState
import com.vivid.shared.model.UserPreview
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de relaciones sociales (follow/unfollow/bloqueos).
 * Define el contrato que ambas plataformas deben implementar.
 */
interface FollowRepository {

    /** Obtiene el estado de la relación con otro usuario. */
    suspend fun getRelationshipState(targetUserId: String): FollowRelationshipState

    /**
     * Alterna el estado de follow según el contexto actual:
     * - Si ya sigue → unfollow
     * - Si hay solicitud pendiente → cancelar solicitud
     * - Si el target es privado → enviar solicitud
     * - Si no hay relación → follow directo
     */
    suspend fun toggleFollow(targetUserId: String): FollowActionResult

    /** Sigue a un usuario (cuenta pública). */
    suspend fun followUser(targetUserId: String)

    /** Deja de seguir a un usuario. */
    suspend fun unfollowUser(targetUserId: String)

    /** Envía una solicitud de seguimiento (cuenta privada). */
    suspend fun sendFollowRequest(targetUserId: String)

    /** Cancela una solicitud de seguimiento pendiente. */
    suspend fun cancelFollowRequest(targetUserId: String)

    /** Acepta una solicitud de seguimiento recibida. */
    suspend fun acceptFollowRequest(requesterId: String)

    /** Rechaza una solicitud de seguimiento recibida. */
    suspend fun rejectFollowRequest(requesterId: String)

    /** Lista las solicitudes de seguimiento recibidas. */
    suspend fun getIncomingFollowRequests(): List<UserPreview>

    /** Cuenta las solicitudes de seguimiento recibidas. */
    suspend fun getIncomingFollowRequestsCount(): Int

    /** Lista los usuarios que el usuario actual sigue. */
    suspend fun getFollowingUsers(): List<UserPreview>

    /** Lista los amigos cercanos del usuario actual. */
    suspend fun getCloseFriends(): List<UserPreview>

    /** Añade un usuario a la lista de amigos cercanos. */
    suspend fun addCloseFriend(targetUserId: String)

    /** Quita un usuario de la lista de amigos cercanos. */
    suspend fun removeCloseFriend(targetUserId: String)

    /** Bloquea a un usuario. */
    suspend fun blockUser(targetUserId: String)

    /** Desbloquea a un usuario. */
    suspend fun unblockUser(targetUserId: String)

    /** Observe cambios en la lista de usuarios bloqueados (tiempo real). */
    fun observeBlockedUserIds(): Flow<Set<String>>

    /** Obtiene los IDs de usuarios bloqueados. */
    suspend fun getBlockedUserIds(): Set<String>

    /** Obtiene la lista completa de usuarios bloqueados. */
    suspend fun getBlockedUsers(): List<UserPreview>

    /** Verifica si el usuario actual sigue a otro. */
    suspend fun isFollowing(targetUserId: String): Boolean

    /** Obtiene el conteo de seguidores de un usuario. */
    suspend fun getFollowersCount(userId: String): Int

    /** Obtiene el conteo de seguidos de un usuario. */
    suspend fun getFollowingCount(userId: String): Int
}

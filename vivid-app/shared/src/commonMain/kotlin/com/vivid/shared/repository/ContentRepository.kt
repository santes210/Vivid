package com.vivid.shared.repository

import com.vivid.shared.model.Post
import com.vivid.shared.model.Reel
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de contenido (posts y reels).
 * Define el contrato que ambas plataformas deben implementar.
 */
interface ContentRepository {

    // ───────────────────── Posts ─────────────────────

    /** Flujo de posts del feed (usuarios seguidos + propios). */
    fun getFeedFlow(): Flow<List<Post>>

    /** Obtiene los posts de un usuario específico. */
    suspend fun getUserPosts(userId: String): List<Post>

    /** Obtiene un post por su ID. */
    suspend fun getPost(postId: String): Post?

    /** Da like o quita like a un post. */
    suspend fun toggleLike(postId: String)

    /** Crea una nueva publicación con imagen. */
    suspend fun createPost(
        imageFilePath: String,
        caption: String,
        isPrivate: Boolean = false
    ): Result<String>

    /** Crea una nueva publicación con video. */
    suspend fun createVideoPost(
        videoFilePath: String,
        thumbnailFilePath: String,
        caption: String,
        musicTitle: String = "",
        musicArtist: String = "",
        musicUrl: String = "",
        isPrivate: Boolean = false
    ): Result<String>

    /** Elimina un post propio. */
    suspend fun deletePost(postId: String): Result<Unit>

    // ───────────────────── Reels ─────────────────────

    /** Flujo de reels para la pantalla de reels. */
    fun getReelsFlow(): Flow<List<Reel>>

    /** Crea un nuevo reel. */
    suspend fun createReel(
        videoFilePath: String,
        thumbnailFilePath: String,
        caption: String,
        isPrivate: Boolean = false
    ): Result<String>

    /** Da like o quita like a un reel. */
    suspend fun toggleReelLike(reelId: String)

    /** Elimina un reel propio. */
    suspend fun deleteReel(reelId: String): Result<Unit>

    // ───────────────────── Explorar ─────────────────────

    /** Obtiene posts para la sección explorar (contenido público). */
    suspend fun getExplorePosts(page: Int = 0, pageSize: Int = 20): List<Post>
}

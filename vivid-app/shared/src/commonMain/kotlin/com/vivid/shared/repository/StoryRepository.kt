package com.vivid.shared.repository

import com.vivid.shared.model.Story
import com.vivid.shared.model.StoryGroup
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de stories.
 * Define el contrato que ambas plataformas deben implementar.
 */
interface StoryRepository {

    /** Flujo de todas las stories visibles para el usuario actual. */
    fun getStoriesFlow(): Flow<List<StoryGroup>>

    /** Obtiene las stories de un usuario específico. */
    suspend fun getUserStories(userId: String): List<Story>

    /** Crea una nueva story con foto. */
    suspend fun createPhotoStory(
        imageFilePath: String,
        caption: String = ""
    ): Result<String>

    /** Crea una nueva story con video. */
    suspend fun createVideoStory(
        videoFilePath: String,
        thumbnailFilePath: String,
        caption: String = ""
    ): Result<String>

    /** Elimina una story propia. */
    suspend fun deleteStory(storyId: String): Result<Unit>

    /** Marca una story como vista por el usuario actual. */
    suspend fun markStoryAsViewed(storyId: String)

    /** Limpia stories expiradas del usuario actual. */
    suspend fun deleteExpiredStories(): Int
}

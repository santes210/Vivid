package com.vivid.shared.model

import kotlinx.serialization.Serializable

/** Duración estándar de una story: 24 horas en milisegundos. */
const val STORY_DURATION_MILLIS: Long = 24L * 60L * 60L * 1000L

/**
 * Modelo de dominio para stories.
 * Compartido entre Android e iOS.
 */
@Serializable
data class Story(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val mediaUrl: String = "",
    val mediaBase64: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val type: StoryType = StoryType.PHOTO,
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = "",
    val hasUnseenStory: Boolean = true,
    val viewersCount: Int = 0
)

/**
 * Grupo de stories de un mismo usuario.
 * Se usa en la UI para mostrar los anillos de stories.
 */
@Serializable
data class StoryGroup(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val stories: List<Story> = emptyList()
)

@Serializable
enum class StoryType {
    PHOTO,
    VIDEO;

    companion object {
        fun fromString(value: String): StoryType = when (value.lowercase()) {
            "photo" -> PHOTO
            "video" -> VIDEO
            else -> PHOTO
        }
    }

    fun toFirestoreString(): String = when (this) {
        PHOTO -> "photo"
        VIDEO -> "video"
    }
}

/**
 * Agrupa stories por usuario, ordenadas por fecha de creación.
 * Lógica compartida entre ambas plataformas.
 */
fun groupStoriesByUser(stories: List<Story>): List<StoryGroup> {
    val grouped = linkedMapOf<String, MutableList<Story>>()

    stories
        .sortedByDescending { it.createdAt }
        .forEach { story ->
            grouped.getOrPut(story.userId) { mutableListOf() }.add(story)
        }

    return grouped.values.map { storyList ->
        val orderedStories = storyList.sortedBy { it.createdAt }
        val first = orderedStories.first()
        StoryGroup(
            userId = first.userId,
            username = first.username,
            avatarUrl = first.avatarUrl,
            avatarBase64 = first.avatarBase64,
            stories = orderedStories
        )
    }
}

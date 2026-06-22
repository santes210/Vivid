package com.vivid.app.presentation.stories

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

const val STORY_DURATION_MILLIS: Long = 24L * 60L * 60L * 1000L

data class Story(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val avatarBase64: String = "",
    val mediaUrl: String = "",
    val mediaBase64: String = "",
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val hasUnseenStory: Boolean = true
)

data class StoryGroup(
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val avatarBase64: String,
    val stories: List<Story>
)

suspend fun buildVisibleStories(
    firestore: FirebaseFirestore,
    currentUserId: String,
    storyDocs: List<DocumentSnapshot>
): List<Story> = withContext(Dispatchers.IO) {
    if (storyDocs.isEmpty()) return@withContext emptyList()

    val followingIds = if (currentUserId.isBlank()) {
        emptySet()
    } else {
        firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .get()
            .await()
            .documents
            .map { it.id }
            .toSet()
    }

    val userCache = mutableMapOf<String, Map<String, Any?>>()
    val userIds = storyDocs.mapNotNull { it.getString("userId") }.distinct()

    for (userId in userIds) {
        val snapshot = firestore.collection("users").document(userId).get().await()
        userCache[userId] = snapshot.data.orEmpty()
    }

    val visibleStories = storyDocs.mapNotNull { doc ->
        val userId = doc.getString("userId").orEmpty()
        if (userId.isBlank()) return@mapNotNull null

        val userData = userCache[userId].orEmpty()
        val isPrivate = (userData["isPrivate"] as? Boolean)
            ?: doc.getBoolean("isPrivate")
            ?: false

        val canView = userId == currentUserId || !isPrivate || followingIds.contains(userId)
        if (!canView) return@mapNotNull null

        Story(
            id = doc.id,
            userId = userId,
            username = userData["username"] as? String
                ?: doc.getString("username")
                ?: "usuario",
            avatarUrl = userData["avatarUrl"] as? String
                ?: doc.getString("avatarUrl").orEmpty(),
            avatarBase64 = userData["avatarBase64"] as? String
                ?: doc.getString("avatarBase64").orEmpty(),
            mediaUrl = doc.getString("mediaUrl").orEmpty(),
            mediaBase64 = doc.getString("mediaBase64").orEmpty(),
            caption = doc.getString("caption").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            hasUnseenStory = true
        )
    }

    groupStoriesByUser(visibleStories).flatMap { it.stories }
}

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

suspend fun uploadStoryWithCompression(
    context: Context,
    uri: Uri,
    caption: String
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val user = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("No hay sesión iniciada"))

        val compressedBase64 = ImageCompressor.compressToBase64(uri, context)
        if (compressedBase64.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No se pudo comprimir la imagen de la story"))
        }

        val userSnapshot = firestore.collection("users").document(user.uid).get().await()
        val username = userSnapshot.getString("username")
            ?: user.displayName
            ?: user.email?.substringBefore("@")
            ?: "usuario"
        val avatarUrl = userSnapshot.getString("avatarUrl").orEmpty()
        val avatarBase64 = userSnapshot.getString("avatarBase64").orEmpty()
        val isPrivate = userSnapshot.getBoolean("isPrivate") ?: false

        val now = System.currentTimeMillis()
        val storyId = UUID.randomUUID().toString()
        val storyData = mapOf(
            "userId" to user.uid,
            "username" to username,
            "avatarUrl" to avatarUrl,
            "avatarBase64" to avatarBase64,
            "mediaUrl" to "",
            "mediaBase64" to compressedBase64,
            "caption" to caption,
            "isPrivate" to isPrivate,
            "createdAt" to now,
            "expiresAt" to now + STORY_DURATION_MILLIS
        )

        firestore.collection("stories")
            .document(storyId)
            .set(storyData)
            .await()

        firestore.collection("users")
            .document(user.uid)
            .update("updatedAt", now)
            .await()

        Result.success(storyId)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun deleteExpiredStoriesForCurrentUser(
    firestore: FirebaseFirestore,
    currentUserId: String,
    now: Long = System.currentTimeMillis()
): Int = withContext(Dispatchers.IO) {
    if (currentUserId.isBlank()) return@withContext 0

    val expiredStories = firestore.collection("stories")
        .whereEqualTo("userId", currentUserId)
        .whereLessThanOrEqualTo("expiresAt", now)
        .get()
        .await()

    val batch = firestore.batch()
    expiredStories.documents.forEach { batch.delete(it.reference) }
    if (expiredStories.documents.isNotEmpty()) {
        batch.commit().await()
    }

    expiredStories.size()
}

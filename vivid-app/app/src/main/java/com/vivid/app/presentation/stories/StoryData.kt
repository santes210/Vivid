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
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val type: String = "photo",
    val caption: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val isPrivate: Boolean = false,
    val storageKey: String = "",
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
            videoUrl = doc.getString("videoUrl").orEmpty(),
            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
            type = doc.getString("type") ?: "photo",
            caption = doc.getString("caption").orEmpty(),
            createdAt = doc.getLong("createdAt") ?: 0L,
            expiresAt = doc.getLong("expiresAt") ?: 0L,
            isPrivate = doc.getBoolean("isPrivate") ?: false,
            storageKey = doc.getString("storageKey").orEmpty(),
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
    caption: String,
    storage: com.vivid.app.data.storage.StorageProvider? = null
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val user = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("No hay sesión iniciada"))

        val compressedFile = java.io.File(context.cacheDir, "story_photo_${System.currentTimeMillis()}.jpg")
        val compressed = ImageCompressor.compressToFile(uri, context, compressedFile)
        if (!compressed || !compressedFile.exists() || compressedFile.length() == 0L) {
            return@withContext Result.failure(IllegalStateException("No se pudo comprimir la imagen de la story"))
        }

        var mediaUrl = ""
        var mediaBase64 = ""
        var storageKey = ""
        if (storage != null) {
            val ts = System.currentTimeMillis()
            storageKey = "stories/${user.uid}/$ts.jpg"
            mediaUrl = storage.uploadFile(compressedFile.absolutePath, storageKey)
        } else {
            val bytes = compressedFile.readBytes()
            if (bytes.size > 900_000) {
                return@withContext Result.failure(IllegalStateException("La imagen es demasiado grande y no hay almacenamiento disponible"))
            }
            mediaBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
        compressedFile.delete()

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
        val storyData = mutableMapOf(
            "userId" to user.uid,
            "username" to username,
            "avatarUrl" to avatarUrl,
            "avatarBase64" to avatarBase64,
            "userAvatar" to avatarUrl,
            "mediaUrl" to mediaUrl,
            "mediaBase64" to mediaBase64,
            "thumbnailUrl" to mediaUrl,
            "caption" to caption,
            "type" to "photo",
            "isPrivate" to isPrivate,
            "createdAt" to now,
            "expiresAt" to now + STORY_DURATION_MILLIS,
            "viewersCount" to 0L
        )
        if (storageKey.isNotBlank()) storyData["storageKey"] = storageKey

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
    now: Long = System.currentTimeMillis(),
    storage: com.vivid.app.data.storage.StorageProvider? = null
): Int = withContext(Dispatchers.IO) {
    if (currentUserId.isBlank()) return@withContext 0

    val expiredStories = firestore.collection("stories")
        .whereEqualTo("userId", currentUserId)
        .whereLessThanOrEqualTo("expiresAt", now)
        .get()
        .await()

    if (storage != null) {
        expiredStories.documents.forEach { doc ->
            try {
                val storageKey = doc.getString("storageKey").orEmpty()
                if (storageKey.isNotBlank()) {
                    runCatching { storage.deleteFile(storageKey) }
                    val thumbKey = if (storageKey.endsWith(".mp4", true)) {
                        storageKey.substringBeforeLast(".") + ".jpg"
                    } else ""
                    if (thumbKey.isNotBlank()) {
                        runCatching { storage.deleteFile(thumbKey) }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val batch = firestore.batch()
    expiredStories.documents.forEach { batch.delete(it.reference) }
    if (expiredStories.documents.isNotEmpty()) {
        batch.commit().await()
    }

    expiredStories.size()
}

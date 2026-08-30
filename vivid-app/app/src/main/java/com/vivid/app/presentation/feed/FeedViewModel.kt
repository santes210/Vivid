package com.vivid.app.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.util.PushSender
import com.vivid.app.util.Hashtags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val storage: StorageProvider,
    private val postDao: PostDao,
    private val firestore: FirebaseFirestore,
    private val followRepository: FollowRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // Room cache (future offline feature). Firestore→Room loading is
    // on-demand; FeedScreen shows its own live data.
    val posts: StateFlow<List<PostEntity>> = postDao.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUserId: String get() = auth.currentUser?.uid.orEmpty()

    // ── Post like (idempotent, transactional) ──

    /**
     * Idempotent like: 1 like per user, enforced by a Firestore transaction.
     *
     * The transaction READS the doc posts/{id}/likes/{uid} and only touches
     * the counter when the state actually changes. Liking twice does not add
     * two; removing a non-existent like does not subtract.
     */
    fun togglePostLike(
        postId: String,
        currentUserId: String,
        shouldLike: Boolean,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (currentUserId.isBlank()) {
            onFailure(IllegalStateException("No active session"))
            return
        }
        viewModelScope.launch {
            runCatching {
                val likeRef = firestore.collection("posts").document(postId)
                    .collection("likes").document(currentUserId)
                val postRef = firestore.collection("posts").document(postId)
                val created = firestore.runTransaction { txn ->
                    val alreadyLiked = txn.get(likeRef).exists()
                    when {
                        shouldLike && !alreadyLiked -> {
                            txn.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis()))
                            txn.update(postRef, "likesCount", FieldValue.increment(1))
                            true
                        }
                        !shouldLike && alreadyLiked -> {
                            txn.delete(likeRef)
                            txn.update(postRef, "likesCount", FieldValue.increment(-1))
                            false
                        }
                        else -> false
                    }
                }.await()
                if (created) PushSender.postLike(postId)
            }.onSuccess { onSuccess() }
                .onFailure { onFailure(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Comment like (idempotent, transactional) ──

    fun toggleCommentLike(
        postId: String,
        commentId: String,
        currentUserId: String,
        shouldLike: Boolean,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val commentRef = firestore.collection("posts").document(postId)
                    .collection("comments").document(commentId)
                val likeRef = commentRef.collection("likes").document(currentUserId)
                firestore.runTransaction { txn ->
                    val alreadyLiked = txn.get(likeRef).exists()
                    when {
                        shouldLike && !alreadyLiked -> {
                            txn.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis()))
                            txn.update(commentRef, "likesCount", FieldValue.increment(1))
                        }
                        !shouldLike && alreadyLiked -> {
                            txn.delete(likeRef)
                            txn.update(commentRef, "likesCount", FieldValue.increment(-1))
                        }
                    }
                    null
                }.await()
            }.onSuccess { onSuccess() }
                .onFailure { onFailure(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Add comment ──

    fun addComment(
        postId: String,
        text: String,
        parentId: String? = null,
        replyToUsername: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val userId = currentUserId
        if (userId.isBlank() || text.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val userDoc = firestore.collection("users").document(userId).get().await()
                val commentData = hashMapOf<String, Any>(
                    "userId" to userId,
                    "username" to (userDoc.getString("username") ?: "user"),
                    "text" to text.trim(),
                    "timestamp" to System.currentTimeMillis(),
                    "avatarUrl" to (userDoc.getString("avatarUrl") ?: ""),
                    "avatarBase64" to (userDoc.getString("avatarBase64") ?: ""),
                    "likesCount" to 0,
                    "isEdited" to false
                )
                if (!parentId.isNullOrBlank()) commentData["parentId"] = parentId
                if (replyToUsername.isNotBlank()) commentData["replyToUsername"] = replyToUsername

                val createdComment = firestore.collection("posts").document(postId)
                    .collection("comments").add(commentData).await()
                firestore.collection("posts").document(postId)
                    .update("commentsCount", FieldValue.increment(1)).await()
                PushSender.postComment(postId, createdComment.id)
            }.onSuccess { onSuccess() }
                .onFailure { onFailure(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Edit comment ──

    fun editComment(
        postId: String,
        commentId: String,
        newText: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                firestore.collection("posts").document(postId)
                    .collection("comments").document(commentId)
                    .update(
                        mapOf(
                            "text" to newText.trim(),
                            "isEdited" to true,
                            "editedAt" to System.currentTimeMillis()
                        )
                    ).await()
            }.onSuccess { onComplete(null) }
                .onFailure { onComplete(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Delete comment ──

    fun deleteComment(
        postId: String,
        commentId: String,
        onComplete: (Exception?) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                firestore.collection("posts").document(postId)
                    .collection("comments").document(commentId).delete().await()
                firestore.collection("posts").document(postId)
                    .update("commentsCount", FieldValue.increment(-1)).await()
            }.onSuccess { onComplete(null) }
                .onFailure { onComplete(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Edit post caption ──

    fun editPostCaption(postId: String, newCaption: String) {
        viewModelScope.launch {
            runCatching {
                val caption = newCaption.trim()
                firestore.collection("posts").document(postId)
                    .update(
                        mapOf(
                            "caption" to caption,
                            // El caption editado puede ganar o perder hashtags;
                            // si no se recalculan, el post quedaría en Explorar
                            // con tags que ya no están en el texto.
                            "hashtags" to Hashtags.extract(caption)
                        )
                    )
            }
        }
    }

    // ── Delete post ──

    fun deletePost(
        postId: String,
        storageKey: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                if (storageKey.isNotBlank()) deleteRemoteFile(storageKey)
                firestore.collection("posts").document(postId).delete().await()
            }.onSuccess { onSuccess() }
                .onFailure { onFailure(it as? Exception ?: Exception(it)) }
        }
    }

    // ── Follow ──

    fun toggleFollowUser(targetUserId: String, onResult: (FollowActionResult?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                followRepository.toggleFollow(targetUserId)
            }.onSuccess { action -> onResult(action) }
                .onFailure { onResult(null) }
        }
    }

    // ── Save/unsave post ──

    fun toggleSavePost(
        postId: String,
        currentUserId: String,
        shouldSave: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (currentUserId.isBlank() || postId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val savedRef = firestore.collection("users").document(currentUserId)
                    .collection("savedPosts").document(postId)
                if (shouldSave) {
                    savedRef.set(mapOf("postId" to postId, "savedAt" to System.currentTimeMillis())).await()
                } else {
                    savedRef.delete().await()
                }
            }.onSuccess {
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message)
            }
        }
    }

    /**
     * Fetches in ONE query the IDs of posts the user already liked
     * (collectionGroup "likes"), instead of 1 read per post (N+1).
     */
    suspend fun fetchLikedPostIds(currentUserId: String): Set<String>? {
        if (currentUserId.isBlank()) return emptySet()
        return try {
            firestore.collectionGroup("likes")
                .whereEqualTo("userId", currentUserId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val parent = doc.reference.parent.parent ?: return@mapNotNull null
                    if (parent.parent?.id == "posts") parent.id else null
                }
                .toSet()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Regenerates the signed URL for a file in B2 (expires after 7 days).
     * Returns null on failure so the caller keeps the saved URL.
     */
    suspend fun refreshSignedUrl(storageKey: String): String? = try {
        storage.signDownloadUrl(storageKey)
    } catch (_: Exception) {
        null
    }

    /** Best-effort deletion of a remote file (when deleting a post). */
    suspend fun deleteRemoteFile(storageKey: String): Boolean =
        runCatching { storage.deleteFile(storageKey) }.getOrDefault(false)

    /**
     * Saves visible posts to the Room cache (with cache timestamp).
     * Called when the feed receives data from Firestore.
     *
     * Preservation rule: if Room already has a re-signed URL (the feed
     * regenerated it when the previous one expired) and the Firestore document
     * still has the old URL, the Room URL is preserved.
     */
    suspend fun cachePosts(posts: List<PostData>) {
        if (posts.isEmpty()) return
        val now = System.currentTimeMillis()
        val existingById = runCatching { postDao.getPostsOnce() }
            .getOrDefault(emptyList())
            .associateBy { it.id }
        postDao.insertPosts(posts.map { post ->
            val existing = existingById[post.id]
            PostEntity(
                id = post.id,
                userId = post.userId,
                username = post.username,
                userProfilePicture = post.userProfilePicture,
                imageUrl = preferResignedUrl(post.imageUrl, existing?.imageUrl),
                imageBase64 = post.imageBase64,
                caption = post.caption,
                likesCount = post.likesCount,
                commentsCount = post.commentsCount,
                timestamp = post.timestamp,
                isLiked = post.isLiked,
                storageKey = post.storageKey,
                videoUrl = preferResignedUrl(post.videoUrl, existing?.videoUrl),
                thumbnailUrl = preferResignedUrl(post.thumbnailUrl, existing?.thumbnailUrl),
                isVideo = post.isVideo,
                musicTitle = post.musicTitle,
                musicArtist = post.musicArtist,
                musicAssetFile = post.musicAssetFile,
                musicUrl = preferResignedUrl(post.musicUrl, existing?.musicUrl),
                musicStorageKey = post.musicStorageKey,
                hashtags = Hashtags.joinForCache(post.hashtags),
                cachedAt = now
            )
        })
    }

    /**
     * If the cache has a URL different from the document's (the locally
     * re-signed one) and it is not empty, keep the cached one.
     */
    private fun preferResignedUrl(incoming: String, cached: String?): String {
        if (cached.isNullOrBlank()) return incoming
        if (incoming.isBlank()) return cached
        return if (cached != incoming) cached else incoming
    }

    /** Deletes a post from the Room cache (post deleted on the server). */
    suspend fun deleteCachedPost(postId: String) {
        postDao.deletePost(postId)
    }

    /** Persists the re-signed image URL for the next session. */
    suspend fun saveResignedImageUrl(postId: String, url: String) {
        postDao.updateImageUrl(postId, url)
    }

    /** Persists the re-signed music URL for the next session. */
    suspend fun saveResignedMusicUrl(postId: String, url: String) {
        postDao.updateMusicUrl(postId, url)
    }

    /** Persists the re-signed video URL for the next session. */
    suspend fun saveResignedVideoUrl(postId: String, url: String) {
        postDao.updateVideoUrl(postId, url)
    }

    /**
     * Converts cached Room entities to [PostData] for offline display
     * or while refreshing from Firestore.
     */
    fun cachedPostsToData(entities: List<PostEntity>): List<PostData> =
        entities.map { entity ->
            PostData(
                id = entity.id,
                userId = entity.userId,
                username = entity.username,
                userProfilePicture = entity.userProfilePicture,
                imageUrl = entity.imageUrl,
                imageBase64 = entity.imageBase64,
                storageKey = entity.storageKey,
                videoUrl = entity.videoUrl,
                thumbnailUrl = entity.thumbnailUrl,
                isVideo = entity.isVideo,
                caption = entity.caption,
                likesCount = entity.likesCount,
                commentsCount = entity.commentsCount,
                timestamp = entity.timestamp,
                isLiked = entity.isLiked,
                musicTitle = entity.musicTitle,
                musicArtist = entity.musicArtist,
                musicAssetFile = entity.musicAssetFile,
                musicUrl = entity.musicUrl,
                musicStorageKey = entity.musicStorageKey,
                hashtags = Hashtags.splitFromCache(entity.hashtags)
            )
        }

    /** Indicates whether the post cache is still valid (less than 7 days). */
    suspend fun isPostCacheFresh(): Boolean {
        val lastCached = postDao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < 7L * 24L * 60L * 60L * 1000L
    }

    /** Returns the cached posts in Room (single read). */
    suspend fun getCachedPosts(): List<PostEntity> = postDao.getPostsOnce()
}

package com.vivid.app.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.PostEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postDao: PostDao,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    val posts: StateFlow<List<PostEntity>> = postDao.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPostsFromFirestore()
    }

    private fun loadPostsFromFirestore() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("posts")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                val entities = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    PostEntity(
                        id = doc.id,
                        userId = data["userId"] as? String ?: "",
                        username = data["username"] as? String ?: "",
                        userProfilePicture = data["userProfilePicture"] as? String ?: "",
                        imageUrl = data["imageUrl"] as? String ?: "",
                        imageBase64 = data["imageBase64"] as? String ?: "",
                        caption = data["caption"] as? String ?: "",
                        likesCount = (data["likesCount"] as? Long)?.toInt() ?: 0,
                        commentsCount = (data["commentsCount"] as? Long)?.toInt() ?: 0,
                        timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
                        isLiked = false
                    )
                }
                postDao.insertPosts(entities)
            } catch (_: Exception) {
                // Offline mode: Room seguirá mostrando el caché actual.
            }
        }
    }

    fun likePost(postId: String) {
        viewModelScope.launch {
            val currentPost = postDao.getPostById(postId) ?: return@launch
            val newIsLiked = !currentPost.isLiked
            val newLikesCount = (currentPost.likesCount + if (newIsLiked) 1 else -1).coerceAtLeast(0)

            postDao.updateLike(postId, newLikesCount, newIsLiked)

            runCatching {
                firestore.collection("posts")
                    .document(postId)
                    .update("likesCount", FieldValue.increment(if (newIsLiked) 1L else -1L))
                    .await()
            }.onFailure {
                postDao.updateLike(postId, currentPost.likesCount, currentPost.isLiked)
            }
        }
    }
}

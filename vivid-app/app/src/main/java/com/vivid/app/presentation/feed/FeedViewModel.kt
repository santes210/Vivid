package com.vivid.app.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.PostEntity
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
                firestore.collection("posts")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val entities = snapshot.documents.mapNotNull { doc ->
                            val data = doc.data ?: return@mapNotNull null
                            PostEntity(
                                id = doc.id,
                                userId = data["userId"] as? String ?: "",
                                username = data["username"] as? String ?: "",
                                userProfilePicture = data["userProfilePicture"] as? String ?: "",
                                imageUrl = data["imageUrl"] as? String ?: "",
                                caption = data["caption"] as? String ?: "",
                                likesCount = (data["likesCount"] as? Long)?.toInt() ?: 0,
                                timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis()
                            )
                        }
                        viewModelScope.launch {
                            postDao.insertPosts(entities)
                        }
                    }
            } catch (e: Exception) {
                // Offline mode - Room will provide cached data
            }
        }
    }

    fun likePost(postId: String) {
        viewModelScope.launch {
            // In real app: update Firestore + local
        }
    }
}
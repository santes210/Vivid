package com.vivid.app.presentation.reels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    init {
        loadReels()
    }

    private fun loadReels() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("reels")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                _reels.value = snapshot.documents.mapNotNull { doc ->
                    val videoUrl = doc.getString("videoUrl").orEmpty()
                    if (videoUrl.isBlank()) return@mapNotNull null
                    Reel(
                        id = doc.id,
                        videoUrl = videoUrl,
                        username = doc.getString("username") ?: "usuario",
                        caption = doc.getString("caption").orEmpty(),
                        likes = doc.getLong("likes")?.toInt() ?: 0,
                        userAvatar = doc.getString("userAvatar").orEmpty()
                    )
                }
            } catch (_: Exception) {
                _reels.value = emptyList()
            }
        }
    }

    suspend fun uploadReel(videoUri: Uri, caption: String): Result<String> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No hay sesión iniciada"))
            val userSnapshot = firestore.collection("users").document(user.uid).get().await()
            val username = userSnapshot.getString("username")
                ?: user.displayName
                ?: user.email?.substringBefore("@")
                ?: "usuario"
            val userAvatar = userSnapshot.getString("avatarUrl").orEmpty()

            val timestamp = System.currentTimeMillis()
            val storageRef = storage.reference.child("reels/${user.uid}/$timestamp.mp4")
            storageRef.putFile(videoUri).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            val reelData = mapOf(
                "userId" to user.uid,
                "username" to username,
                "userAvatar" to userAvatar,
                "videoUrl" to downloadUrl,
                "caption" to caption,
                "likes" to 0,
                "timestamp" to timestamp
            )

            val docRef = firestore.collection("reels").add(reelData).await()
            loadReels()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

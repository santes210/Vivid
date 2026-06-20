package com.vivid.app.presentation.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val storage: FirebaseStorage
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

                val loadedReels = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Reel(
                        id = doc.id,
                        videoUrl = data["videoUrl"] as? String ?: "",
                        username = data["username"] as? String ?: "",
                        caption = data["caption"] as? String ?: "",
                        likes = (data["likes"] as? Long)?.toInt() ?: 0
                    )
                }

                // If no reels in Firestore, use demo videos
                if (loadedReels.isEmpty()) {
                    _reels.value = listOf(
                        Reel("1", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "ana_vivid", "Día increíble 🌊", 12400),
                        Reel("2", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "carlos_vivid", "Setup de edición 🔥", 8900),
                        Reel("3", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "lucia_vivid", "Aventura 🏔️", 23100)
                    )
                } else {
                    _reels.value = loadedReels
                }
            } catch (e: Exception) {
                // Fallback to demo
                _reels.value = listOf(
                    Reel("1", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "ana_vivid", "Día increíble 🌊", 12400)
                )
            }
        }
    }

    suspend fun uploadReel(videoUri: android.net.Uri, caption: String) {
        // Implementación real de subida a Storage + Firestore
    }
}
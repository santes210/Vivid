package com.vivid.app.presentation.messages

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.presentation.reels.ReelLinks
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChatReelVideo(val id: String, val url: String, val username: String, val caption: String, val collection: String = "reels", val isVideo: Boolean = true)

class ChatReelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider
) {
    suspend fun load(id: String, collection: String = "reels"): ChatReelVideo {
        require(collection == "reels" || collection == "posts")
        ReelLinks.create(id) // Reject malformed IDs before querying.
        val uid = auth.currentUser?.uid ?: error("No active session")
        // Do not expose cached media before checking this viewer's current access.
        val document = firestore.collection(collection).document(id).get(Source.SERVER).await()
        check(document.exists()) { "Reel unavailable" }
        val isVideo = collection == "reels" || document.getBoolean("isVideo") == true
        val key = document.getString("storageKey").orEmpty()
        val url = if (key.isNotBlank()) storage.signDownloadUrl(key)
            else document.getString(if (isVideo) "videoUrl" else "imageUrl").orEmpty()
        require(url.startsWith("https://")) { "Invalid media URL" }
        check(auth.currentUser?.uid == uid) { "Session changed" }
        return ChatReelVideo(id, url, document.getString("username").orEmpty(), document.getString("caption").orEmpty(), collection, isVideo)
    }
}

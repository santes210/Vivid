package com.vivid.app.presentation.messages

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.data.storage.StorageRequestException
import kotlinx.coroutines.CancellationException
import com.vivid.app.presentation.reels.ReelLinks
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ChatReelVideo(val id: String, val url: String, val username: String, val caption: String, val collection: String = "reels", val isVideo: Boolean = true, val imageBase64: String = "", val altText: String = "", val ownerId: String = "")

class ChatReelRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider
) {
    suspend fun load(id: String, collection: String = "reels"): ChatReelVideo {
        require(collection == "reels" || collection == "posts")
        ReelLinks.create(id) // Reject malformed IDs before querying.
        val uid = auth.currentUser?.uid ?: throw MediaOpenException(MediaOpenFailure.SESSION)
        // Do not expose cached media before checking this viewer's current access.
        val document = firestore.collection(collection).document(id).get(Source.SERVER).await()
        check(document.exists()) { "Reel unavailable" }
        val isVideo = collection == "reels" || document.getBoolean("isVideo") == true
        val key = document.getString("storageKey").orEmpty()
        val url = if (key.isNotBlank()) {
            try { storage.signDownloadUrl(key) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val reason = when ((error as? StorageRequestException)?.statusCode) {
                    401 -> MediaOpenFailure.SESSION
                    403 -> MediaOpenFailure.ACCESS
                    else -> MediaOpenFailure.SIGNING
                }
                throw MediaOpenException(reason)
            }
        } else document.getString(if (isVideo) "videoUrl" else "imageUrl").orEmpty()
        val imageBase64 = if (!isVideo && url.isBlank()) document.getString("imageBase64").orEmpty() else ""
        require(url.startsWith("https://") || (!isVideo && imageBase64.isNotBlank())) { "Invalid media URL" }
        check(auth.currentUser?.uid == uid) { "Session changed" }
        return ChatReelVideo(id, url, document.getString("username").orEmpty(), document.getString("caption").orEmpty(), collection, isVideo, imageBase64, document.getString("altText").orEmpty(), document.getString("userId").orEmpty())
    }
}

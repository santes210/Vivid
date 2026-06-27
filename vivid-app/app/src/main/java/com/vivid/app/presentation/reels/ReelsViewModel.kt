package com.vivid.app.presentation.reels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.data.storage.StorageProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel de Reels.
 *
 * CAMBIO vs versión original:
 *   - Antes: subía el video con FirebaseStorage.
 *   - Ahora: usa [StorageProvider] (que hoy es Backblaze B2 directo,
 *     mañana será Cloud Functions sin tocar nada más).
 *
 * El resto (carga de lista, likes, etc.) sigue igual.
 */
@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: StorageProvider,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        refreshReels()
    }

    /**
     * Recarga los reels desde Firestore.
     *
     * Antes solo se cargaban en init; al volver de Crear Reel el ViewModel
     * se conserva en el back stack y la lista quedaba vieja/vacía. Por eso
     * parecía que el video “no aparecía” aunque sí se hubiera subido.
     */
    fun refreshReels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val snapshot = firestore.collection("reels")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                _reels.value = snapshot.documents.mapNotNull { doc ->
                    val storageKey = doc.getString("storageKey").orEmpty()
                    val savedUrl = doc.getString("videoUrl").orEmpty()
                    val thumbStorageKey = doc.getString("thumbnailStorageKey").orEmpty()
                    val savedThumbUrl = doc.getString("thumbnailUrl").orEmpty()

                    // Regenerar URL firmada fresca desde el storageKey (las URLs
                    // firmadas expiran). Si falla, usar la URL guardada.
                    val videoUrl = if (storageKey.isNotBlank()) {
                        try { storage.signDownloadUrl(storageKey) }
                        catch (_: Exception) { savedUrl }
                    } else savedUrl

                    val thumbnailUrl = if (thumbStorageKey.isNotBlank()) {
                        try { storage.signDownloadUrl(thumbStorageKey) }
                        catch (_: Exception) { savedThumbUrl }
                    } else savedThumbUrl

                    if (videoUrl.isBlank()) return@mapNotNull null
                    Reel(
                        id = doc.id,
                        videoUrl = videoUrl,
                        thumbnailUrl = thumbnailUrl,
                        username = doc.getString("username") ?: "usuario",
                        caption = doc.getString("caption").orEmpty(),
                        likes = doc.getLong("likes")?.toInt() ?: 0,
                        userAvatar = doc.getString("userAvatar").orEmpty()
                    )
                }
            } catch (_: Exception) {
                _reels.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Sube un reel. El path debe venir ya comprimido (de [VideoCompressor]).
     *
     * @param compressedVideoFilePath ruta local del MP4 comprimido
     * @param caption texto del reel
     */
    suspend fun uploadReel(compressedVideoFilePath: String, caption: String): Result<String> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(IllegalStateException("No hay sesión"))

            val userSnapshot = firestore.collection("users").document(user.uid).get().await()
            val username = userSnapshot.getString("username")
                ?: user.displayName
                ?: user.email?.substringBefore('@')
                ?: "usuario"
            val userAvatar = userSnapshot.getString("avatarUrl").orEmpty()

            val timestamp = System.currentTimeMillis()
            val remoteKey = "reels/${user.uid}/$timestamp.mp4"

            // Sube al provider (B2 hoy, Cloud Functions mañana)
            val publicUrl = storage.uploadFile(compressedVideoFilePath, remoteKey)

            val reelData = mapOf(
                "userId" to user.uid,
                "username" to username,
                "userAvatar" to userAvatar,
                "videoUrl" to publicUrl,
                "storageKey" to remoteKey,
                "provider" to "backblaze",
                "caption" to caption,
                "likes" to 0,
                "timestamp" to timestamp
            )

            val docRef = firestore.collection("reels").add(reelData).await()
            refreshReels()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

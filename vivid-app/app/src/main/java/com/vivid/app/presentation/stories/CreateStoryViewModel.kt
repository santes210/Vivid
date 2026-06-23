package com.vivid.app.presentation.stories

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.data.storage.VideoCompressor
import com.vivid.app.util.VideoThumbnailer
import com.vivid.app.util.VideoWatermarker
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

sealed interface CreateStoryUiState {
    data object Idle : CreateStoryUiState
    data class Compressing(val percent: Int) : CreateStoryUiState
    data class Watermarking(val percent: Int) : CreateStoryUiState
    data class Uploading(val percent: Int) : CreateStoryUiState
    data object SavingMetadata : CreateStoryUiState
    data object Success : CreateStoryUiState
    data class Error(val message: String) : CreateStoryUiState
}

@UnstableApi
@HiltViewModel
class CreateStoryViewModel @Inject constructor(
    private val storage: StorageProvider,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow<CreateStoryUiState>(CreateStoryUiState.Idle)
    val state: StateFlow<CreateStoryUiState> = _state.asStateFlow()

    /**
     * Sube un story con video.
     *
     * A diferencia de Reels:
     *   - Stories son verticales cortos (15s máx, 540x960).
     *   - Siempre llevan watermark Vivid (para branding).
     *   - Se borran automáticamente a las 24h (campo `expiresAt`).
     */
    fun publishVideoStory(
        context: Context,
        videoUri: Uri,
        caption: String
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión")

                // 1. Comprimir (más agresivo que Reels: 540p)
                _state.value = CreateStoryUiState.Compressing(0)
                val compressed = VideoCompressor.compress(context, videoUri) { pct ->
                    _state.value = CreateStoryUiState.Compressing(pct)
                }

                // 2. Watermark
                _state.value = CreateStoryUiState.Watermarking(0)
                val wmFile = File(context.cacheDir, "story_wm_${System.currentTimeMillis()}.mp4")
                val watermarked = VideoWatermarker.applyWatermark(
                    context = context,
                    inputUri = Uri.fromFile(File(compressed)),
                    outputFile = wmFile
                )

                // 3. Generar thumbnail
                val thumbFile = File(context.cacheDir, "story_thumb_${System.currentTimeMillis()}.jpg")
                VideoThumbnailer.extract(context, Uri.fromFile(File(compressed)), thumbFile)

                // 4. Subir video a B2
                _state.value = CreateStoryUiState.Uploading(0)
                val ts = System.currentTimeMillis()
                val videoKey = "stories/${user.uid}/$ts.mp4"
                val videoUrl = storage.uploadFile(watermarked, videoKey) { pct ->
                    _state.value = CreateStoryUiState.Uploading(pct / 2) // 0..50%
                }

                // 5. Subir thumbnail a B2
                val thumbKey = "stories/${user.uid}/$ts.jpg"
                val thumbUrl = if (thumbFile.exists()) {
                    storage.uploadFile(thumbFile.absolutePath, thumbKey) { pct ->
                        _state.value = CreateStoryUiState.Uploading(50 + pct / 2) // 50..100%
                    }
                } else ""

                // 6. Metadata en Firestore
                _state.value = CreateStoryUiState.SavingMetadata
                writeStoryMetadata(
                    uid = user.uid,
                    videoUrl = videoUrl,
                    thumbnailUrl = thumbUrl,
                    caption = caption,
                    storageKey = videoKey
                )

                _state.value = CreateStoryUiState.Success
            } catch (e: Exception) {
                _state.value = CreateStoryUiState.Error(e.message ?: "Error subiendo story")
            }
        }
    }

    /**
     * Sube un story con foto (sin watermark ni compresión).
     */
    fun publishPhotoStory(context: Context, photoUri: Uri, caption: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión")
                _state.value = CreateStoryUiState.Uploading(0)

                val ts = System.currentTimeMillis()
                val photoKey = "stories/${user.uid}/$ts.jpg"

                // Copia el archivo a cache primero
                val tempFile = File(context.cacheDir, "story_photo_$ts.jpg")
                context.contentResolver.openInputStream(photoUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val photoUrl = storage.uploadFile(tempFile.absolutePath, photoKey) { pct ->
                    _state.value = CreateStoryUiState.Uploading(pct)
                }

                _state.value = CreateStoryUiState.SavingMetadata
                writeStoryMetadata(
                    uid = user.uid,
                    videoUrl = "",
                    thumbnailUrl = photoUrl,
                    caption = caption,
                    storageKey = photoKey
                )

                _state.value = CreateStoryUiState.Success
            } catch (e: Exception) {
                _state.value = CreateStoryUiState.Error(e.message ?: "Error subiendo story")
            }
        }
    }

    private suspend fun writeStoryMetadata(
        uid: String,
        videoUrl: String,
        thumbnailUrl: String,
        caption: String,
        storageKey: String
    ) {
        val expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000L

        val data = mapOf(
            "userId" to uid,
            "videoUrl" to videoUrl,
            "thumbnailUrl" to thumbnailUrl,
            "storageKey" to storageKey,
            "caption" to caption,
            "type" to if (videoUrl.isBlank()) "photo" else "video",
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to expiresAt,
            "viewersCount" to 0
        )

        firestore.collection("stories").add(data).await()
    }

    fun reset() {
        _state.value = CreateStoryUiState.Idle
    }
}

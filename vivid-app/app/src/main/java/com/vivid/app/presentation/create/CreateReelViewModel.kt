package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.data.storage.VideoCompressor
import com.vivid.app.util.VideoThumbnailer
import com.vivid.app.util.VideoTrimmer
import com.vivid.app.util.VideoWatermarker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

sealed interface CreateReelUiState {
    data object Idle : CreateReelUiState
    data class Compressing(val percent: Int) : CreateReelUiState
    data class Watermarking(val percent: Int) : CreateReelUiState
    data class Uploading(val percent: Int) : CreateReelUiState
    data object SavingMetadata : CreateReelUiState
    data object Success : CreateReelUiState
    data class Error(val message: String) : CreateReelUiState
}

@UnstableApi
@HiltViewModel
class CreateReelViewModel @Inject constructor(
    private val storage: StorageProvider,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow<CreateReelUiState>(CreateReelUiState.Idle)
    val state: StateFlow<CreateReelUiState> = _state.asStateFlow()

    /**
     * Sube un reel con:
     *   1. Compresión del video (ahorra batería + espacio B2)
     *   2. Marca de agua "Vivid ✦" (opcional pero recomendado)
     *   3. Miniatura automática (frame del video, JPEG)
     *   4. Subida a Backblaze B2 vía Cloud Functions (signed URLs)
     *   5. Metadata en Firestore
     */
    fun publishReel(
        context: Context,
        videoUri: Uri,
        caption: String,
        trimStartMs: Long = 0,
        trimEndMs: Long = -1,
        withWatermark: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión iniciada")

                // 0. Si el usuario recortó el video, generar primero el archivo recortado.
                val inputForCompression = if (trimEndMs > trimStartMs && trimStartMs >= 0) {
                    val trimmedFile = File(context.cacheDir, "reel_trim_${System.currentTimeMillis()}.mp4")
                    Uri.fromFile(
                        File(
                            VideoTrimmer.trim(
                                context = context,
                                inputUri = videoUri,
                                outputFile = trimmedFile,
                                startMs = trimStartMs,
                                endMs = trimEndMs
                            )
                        )
                    )
                } else {
                    videoUri
                }

                // 1. Comprimir (a 720x1280, 1.5 Mbps)
                _state.value = CreateReelUiState.Compressing(0)
                val compressedPath = VideoCompressor.compress(context, inputForCompression) { pct ->
                    _state.value = CreateReelUiState.Compressing(pct)
                }

                // 2. Watermark (si el usuario lo eligió)
                var finalVideoPath = compressedPath
                if (withWatermark) {
                    _state.value = CreateReelUiState.Watermarking(0)
                    val wmFile = File(context.cacheDir, "reel_wm_${System.currentTimeMillis()}.mp4")
                    finalVideoPath = VideoWatermarker.applyWatermark(
                        context = context,
                        inputUri = Uri.fromFile(File(compressedPath)),
                        outputFile = wmFile
                    )
                    _state.value = CreateReelUiState.Watermarking(100)
                }

                // 3. Miniatura (JPEG, 720px ancho)
                val thumbFile = File(context.cacheDir, "reel_thumb_${System.currentTimeMillis()}.jpg")
                VideoThumbnailer.extract(
                    context,
                    Uri.fromFile(File(finalVideoPath)),
                    thumbFile,
                    targetWidth = 720
                )

                // 4. Subir video a B2 (URL firmada para bucket privado)
                _state.value = CreateReelUiState.Uploading(0)
                val ts = System.currentTimeMillis()
                val remoteKey = "reels/${user.uid}/$ts.mp4"
                val publicUrl = storage.uploadFile(finalVideoPath, remoteKey) { pct ->
                    _state.value = CreateReelUiState.Uploading(pct / 2)
                }

                // Subir miniatura a B2 (URL firmada)
                val thumbKey = remoteKey.replace(".mp4", "_thumb.jpg")
                val thumbUrl = if (thumbFile.exists() && thumbFile.length() > 0) {
                    storage.uploadFile(thumbFile.absolutePath, thumbKey) { pct ->
                        _state.value = CreateReelUiState.Uploading(50 + pct / 2)
                    }
                } else ""

                // 5. Metadata
                _state.value = CreateReelUiState.SavingMetadata
                writeReelMetadata(user.uid, publicUrl, thumbUrl, caption, remoteKey, thumbKey)

                // No dejes que un fallo de contador (reglas de Firestore) marque como fallida
                // una publicación que ya subió video y metadata correctamente.
                try {
                    firestore.collection("users").document(user.uid).update(
                        "reelsCount", FieldValue.increment(1),
                        "updatedAt", System.currentTimeMillis()
                    ).await()
                } catch (counterError: Exception) {
                    Log.w(TAG, "No se pudo actualizar reelsCount; reel ya publicado", counterError)
                }

                _state.value = CreateReelUiState.Success
                Log.d(TAG, "Reel publicado OK")
            } catch (e: Exception) {
                Log.e(TAG, "Error publicando reel", e)
                _state.value = CreateReelUiState.Error(e.message ?: "Error subiendo reel")
            }
        }
    }

    private suspend fun writeReelMetadata(
        uid: String,
        videoUrl: String,
        thumbnailUrl: String,
        caption: String,
        storageKey: String,
        thumbnailStorageKey: String
    ) {
        val userDoc = firestore.collection("users").document(uid).get().await()
        val username = userDoc.getString("username")
            ?: auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore('@')
            ?: "usuario"
        val avatar = userDoc.getString("avatarUrl").orEmpty()

        val data = mapOf(
            "userId" to uid,
            "username" to username,
            "userAvatar" to avatar,
            "videoUrl" to videoUrl,
            "thumbnailUrl" to thumbnailUrl,
            "thumbnailStorageKey" to thumbnailStorageKey,
            "storageKey" to storageKey,
            "provider" to "backblaze-direct",
            "caption" to caption.trim(),
            "likes" to 0,
            "comments" to 0,
            "shares" to 0,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("reels").add(data).await()
    }

    fun reset() {
        _state.value = CreateReelUiState.Idle
    }

    companion object {
        private const val TAG = "CreateReelVM"
    }
}

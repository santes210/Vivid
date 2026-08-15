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
    data class Trimming(val percent: Int) : CreateStoryUiState
    data class Compressing(val percent: Int) : CreateStoryUiState
    data class MixingAudio(val percent: Int) : CreateStoryUiState
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

    fun publishVideoStory(
        context: Context,
        videoUri: Uri,
        caption: String,
        audioUri: Uri? = null
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión")

                var currentUri = videoUri
                val ts = System.currentTimeMillis()

                val durationMs = getVideoDurationMs(context, currentUri)
                if (durationMs > 15_000) {
                    _state.value = CreateStoryUiState.Trimming(0)
                    val trimmedFile = File(context.cacheDir, "story_trim_${ts}.mp4")
                    val trimmedPath = try {
                        com.vivid.app.util.VideoTrimmer.trim(
                            context = context,
                            inputUri = currentUri,
                            outputFile = trimmedFile,
                            startMs = 0,
                            endMs = 15_000
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("CreateStoryVM", "Trim falló, usando original: ${e.message}")
                        currentUri.toString()
                    }
                    if (File(trimmedPath).exists() && File(trimmedPath).length() > 0) {
                        currentUri = Uri.fromFile(File(trimmedPath))
                    }
                    _state.value = CreateStoryUiState.Trimming(100)
                }

                _state.value = CreateStoryUiState.Compressing(0)
                val compressed = VideoCompressor.compress(context, currentUri) { pct ->
                    _state.value = CreateStoryUiState.Compressing(pct)
                }
                currentUri = Uri.fromFile(File(compressed))

                if (audioUri != null) {
                    _state.value = CreateStoryUiState.MixingAudio(0)
                    val audioToMix = try {
                        val audioDur = com.vivid.app.util.AudioTrimmer.getDurationMs(context, audioUri)
                        if (audioDur > 15_000) {
                            android.util.Log.d("CreateStoryVM", "Audio dura ${audioDur}ms, recortando a 15s para ahorrar B2")
                            val trimmedAudioFile = File(context.cacheDir, "story_audio_trimmed_${ts}.m4a")
                            val trimmedPath = com.vivid.app.util.AudioTrimmer.trimAudio(
                                context = context,
                                inputUri = audioUri,
                                outputFile = trimmedAudioFile,
                                startMs = 0,
                                endMs = 15_000
                            )
                            val f = File(trimmedPath)
                            if (f.exists() && f.length() > 1024) Uri.fromFile(f) else throw IllegalStateException("Trim vacío")
                        } else audioUri
                    } catch (e: Exception) {
                        android.util.Log.e("CreateStoryVM", "Auto-trim audio falló, no se subirá canción completa: ${e.message}")
                        throw IllegalStateException("No se pudo recortar el audio a 15s para ahorrar espacio. Elige un recorte manual de 15s.")
                    }

                    val mixedFile = File(context.cacheDir, "story_audio_${ts}.mp4")
                    val mixedPath = try {
                        com.vivid.app.util.AudioMixer.replaceAudio(
                            context = context,
                            videoUri = currentUri,
                            musicUri = audioToMix,
                            outputFile = mixedFile
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("CreateStoryVM", "Audio mix falló: ${e.message}")
                        currentUri.toString()
                    }
                    if (File(mixedPath).exists() && File(mixedPath).length() > 0) {
                        currentUri = Uri.fromFile(File(mixedPath))
                    }
                    _state.value = CreateStoryUiState.MixingAudio(100)
                }

                _state.value = CreateStoryUiState.Watermarking(0)
                val wmFile = File(context.cacheDir, "story_wm_${ts}.mp4")
                val watermarked = VideoWatermarker.applyWatermark(
                    context = context,
                    inputUri = currentUri,
                    outputFile = wmFile
                )
                currentUri = Uri.fromFile(File(watermarked))

                val thumbFile = File(context.cacheDir, "story_thumb_${ts}.jpg")
                VideoThumbnailer.extract(context, currentUri, thumbFile)

                _state.value = CreateStoryUiState.Uploading(0)
                val videoKey = "stories/${user.uid}/$ts.mp4"
                val localVideoPath = currentUri.path ?: watermarked
                val finalVideoPath = if (File(localVideoPath).exists()) localVideoPath else watermarked
                val videoUrl = storage.uploadFile(finalVideoPath, videoKey) { pct ->
                    _state.value = CreateStoryUiState.Uploading(pct / 2)
                }

                val thumbKey = "stories/${user.uid}/$ts.jpg"
                val thumbUrl = if (thumbFile.exists() && thumbFile.length() > 0) {
                    try {
                        storage.uploadFile(thumbFile.absolutePath, thumbKey) { pct ->
                            _state.value = CreateStoryUiState.Uploading(50 + pct / 2)
                        }
                    } catch (_: Exception) { "" }
                } else ""

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
                android.util.Log.e("CreateStoryVM", "Error subiendo story video", e)
                _state.value = CreateStoryUiState.Error(e.message ?: "Error subiendo story")
            }
        }
    }

    private fun getVideoDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (_: Exception) { 0L }
    }

    fun publishPhotoStory(context: Context, photoUri: Uri, caption: String) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión")
                _state.value = CreateStoryUiState.Uploading(0)

                val result = uploadStoryWithCompression(context, photoUri, caption, storage)
                if (result.isSuccess) {
                    _state.value = CreateStoryUiState.Success
                } else {
                    _state.value = CreateStoryUiState.Error(result.exceptionOrNull()?.message ?: "Error subiendo story")
                }
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
        val now = System.currentTimeMillis()
        val expiresAt = now + 24 * 60 * 60 * 1000L
        val userDoc = firestore.collection("users").document(uid).get().await()
        val username = userDoc.getString("username")
            ?: auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore('@')
            ?: "usuario"
        val avatarUrl = userDoc.getString("avatarUrl").orEmpty()
        val avatarBase64 = userDoc.getString("avatarBase64").orEmpty()
        val isPrivate = userDoc.getBoolean("isPrivate") ?: false
        val isVideo = videoUrl.isNotBlank()
        val mediaUrl = if (isVideo) thumbnailUrl else ""

        val data = mapOf(
            "userId" to uid,
            "username" to username,
            "avatarUrl" to avatarUrl,
            "avatarBase64" to avatarBase64,
            "userAvatar" to avatarUrl,
            "videoUrl" to videoUrl,
            "thumbnailUrl" to thumbnailUrl,
            "mediaUrl" to mediaUrl,
            "mediaBase64" to "",
            "storageKey" to storageKey,
            "caption" to caption.trim(),
            "type" to if (isVideo) "video" else "photo",
            "isPrivate" to isPrivate,
            "createdAt" to now,
            "expiresAt" to expiresAt,
            "viewersCount" to 0
        )

        firestore.collection("stories").add(data).await()
    }

    fun cleanExpiredStories(currentUserId: String) {
        if (currentUserId.isBlank()) return
        viewModelScope.launch {
            try {
                val deleted = deleteExpiredStoriesForCurrentUser(
                    firestore = firestore,
                    currentUserId = currentUserId,
                    now = System.currentTimeMillis(),
                    storage = storage
                )
                if (deleted > 0) {
                    android.util.Log.d("CreateStoryVM", "Limpieza: $deleted stories borradas de Firestore + B2")
                }
            } catch (e: Exception) {
                android.util.Log.w("CreateStoryVM", "Limpieza falló: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = CreateStoryUiState.Idle
    }
}

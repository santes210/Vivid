package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.util.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

sealed interface CreatePostUiState {
    data object Idle : CreatePostUiState
    data class Compressing(val percent: Int) : CreatePostUiState
    data class Uploading(val percent: Int) : CreatePostUiState
    data class UploadingAudio(val percent: Int) : CreatePostUiState
    data object SavingMetadata : CreatePostUiState
    data object Success : CreatePostUiState
    data class Error(val message: String) : CreatePostUiState
}

/**
 * Publicación de posts (fotos) con respaldo automático + música opcional:
 *
 *  1. Comprime la foto a JPEG local (máx 1280px, ~550 KB).
 *  2. Si hay música del dispositivo, la copia a cache y la sube a B2 (audio/mp3).
 *  3. Intenta subir la foto a Backblaze B2 → guarda SOLO la URL firmada en
 *     Firestore (`imageUrl` + `storageKey`), sin base64.
 *  4. Guarda metadata con música opcional (título, artista, asset o URL firmada).
 */
@HiltViewModel
class CreatePostViewModel @Inject constructor(
    private val storage: StorageProvider,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _state = MutableStateFlow<CreatePostUiState>(CreatePostUiState.Idle)
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()


private fun extractHashtags(text: String): List<String> {
    val regex = Regex("#(\\w+)")
    return regex.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()
}
    fun publishPost(
        context: Context,
        imageUri: Uri,
        caption: String,
        musicTrack: com.vivid.app.presentation.create.MusicTrack? = null,
        musicUri: Uri? = null
    ) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser
                    ?: throw IllegalStateException("No hay sesión activa")

                // 1. Comprimir a archivo local (para B2 no hay límite de 1MB)
                _state.value = CreatePostUiState.Compressing(20)
                val compressedFile = File(context.cacheDir, "post_${System.currentTimeMillis()}.jpg")
                if (!ImageCompressor.compressToFile(imageUri, context, compressedFile)) {
                    throw IllegalStateException("No se pudo comprimir la imagen")
                }
                _state.value = CreatePostUiState.Compressing(100)

                // 1b. Si hay música del dispositivo, subirla a B2
                var musicUrl = ""
                var musicStorageKey = ""
                var musicTitle = musicTrack?.title ?: ""
                var musicArtist = musicTrack?.artist ?: ""
                var musicAssetFile = musicTrack?.assetFile ?: ""

                if (musicUri != null) {
                    try {
                        _state.value = CreatePostUiState.UploadingAudio(0)
                        // Copiar Uri a archivo temporal para upload (robusto para content:// y file://)
                        val audioTempFile = File(context.cacheDir, "post_audio_${System.currentTimeMillis()}_${musicTitle.ifBlank { "audio" }}.m4a")
                        try {
                            if (musicUri.scheme == "file") {
                                val srcFile = File(musicUri.path ?: "")
                                if (srcFile.exists()) srcFile.copyTo(audioTempFile, overwrite = true)
                                else {
                                    context.contentResolver.openInputStream(musicUri)?.use { input ->
                                        audioTempFile.outputStream().use { output -> input.copyTo(output) }
                                    }
                                }
                            } else {
                                context.contentResolver.openInputStream(musicUri)?.use { input ->
                                    audioTempFile.outputStream().use { output -> input.copyTo(output) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo copiar audio, intentando upload directo: ${e.message}")
                            // Si falla el copy, intentar usar el archivo original si es file://
                            if (musicUri.scheme == "file") {
                                val srcPath = musicUri.path
                                if (srcPath != null && File(srcPath).exists()) {
                                    audioTempFile.delete()
                                    File(srcPath).copyTo(audioTempFile, overwrite = true)
                                }
                            }
                        }
                        if (audioTempFile.exists() && audioTempFile.length() > 0) {
                            // AHORRO B2: si el audio dura >15s, recortar automáticamente a 15s antes de subir
                            // Así nunca se sube la canción completa, solo el recorte.
                            val finalAudioFile = try {
                                val dur = com.vivid.app.util.AudioTrimmer.getDurationMs(context, Uri.fromFile(audioTempFile))
                                if (dur > 15_000) {
                                    Log.d(TAG, "Audio dura ${dur}ms, recortando a 15s para ahorrar B2")
                                    val trimmedFile = File(context.cacheDir, "post_audio_trimmed_${System.currentTimeMillis()}.m4a")
                                    val trimmedPath = com.vivid.app.util.AudioTrimmer.trimAudio(
                                        context = context,
                                        inputUri = Uri.fromFile(audioTempFile),
                                        outputFile = trimmedFile,
                                        startMs = 0,
                                        endMs = 15_000
                                    )
                                    val f = File(trimmedPath)
                                    if (f.exists() && f.length() > 1024) f else throw IllegalStateException("Trim vacío")
                                } else audioTempFile
                            } catch (e: Exception) {
                                Log.e(TAG, "Auto-trim falló, no se subirá canción completa para ahorrar B2: ${e.message}")
                                throw IllegalStateException("No se pudo recortar el audio a 15s. Elige una canción más corta o recórtala manualmente.")
                            }

                            val ts = System.currentTimeMillis()
                            // FIX: usar .m4a para que el Content-Type sea audio/mp4 y ExoPlayer lo reproduzca bien
                            musicStorageKey = "posts/${user.uid}/${ts}_audio.m4a"
                            musicUrl = storage.uploadFile(finalAudioFile.absolutePath, musicStorageKey) { pct ->
                                _state.value = CreatePostUiState.UploadingAudio(pct)
                            }
                            // Si no vino track, usa nombre del archivo
                            if (musicTitle.isBlank()) {
                                musicTitle = finalAudioFile.nameWithoutExtension
                                musicArtist = "Tu dispositivo"
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Upload audio falló, continuará sin música: ${e.message}")
                        musicUrl = ""
                        musicStorageKey = ""
                    }
                }

                // 2. Intentar subir foto a Backblaze B2
                var uploaded = false
                var publicUrl = ""
                var storageKey = ""
                try {
                    val ts = System.currentTimeMillis()
                    storageKey = "posts/${user.uid}/$ts.jpg"
                    _state.value = CreatePostUiState.Uploading(0)
                    publicUrl = storage.uploadFile(compressedFile.absolutePath, storageKey) { pct ->
                        _state.value = CreatePostUiState.Uploading(pct)
                    }
                    uploaded = publicUrl.isNotBlank()
                } catch (e: Exception) {
                    // Fallback a base64 — no es fatal
                    Log.w(TAG, "Upload a B2 falló, usando base64: ${e.message}")
                    uploaded = false
                }

                // 3. Metadata
                _state.value = CreatePostUiState.SavingMetadata
                val postId = "post_${user.uid}_${System.currentTimeMillis()}"
                val userDoc = firestore.collection("users").document(user.uid).get().await()
                val username = userDoc.getString("username")
                    ?: user.displayName
                    ?: user.email?.substringBefore('@')
                    ?: "usuario"

                val hashtags = extractHashtags(caption)
                val baseMusicMap = mutableMapOf<String, Any>().apply {
                    if (musicTitle.isNotBlank()) put("musicTitle", musicTitle)
                    if (musicArtist.isNotBlank()) put("musicArtist", musicArtist)
                    if (musicAssetFile.isNotBlank()) put("musicAssetFile", musicAssetFile)
                    if (musicUrl.isNotBlank()) put("musicUrl", musicUrl)
                    if (musicStorageKey.isNotBlank()) put("musicStorageKey", musicStorageKey)
                }

                if (uploaded) {
                    // Guardar SOLO la URL (sin base64, sin límite 1MB)
                    val postData = mutableMapOf<String, Any>(
                        "userId" to user.uid,
                        "username" to username,
                        "imageUrl" to publicUrl,
                        "storageKey" to storageKey,
                        "provider" to "backblaze-direct",
                        "caption" to caption.trim(),
                        "likesCount" to 0L,
                        "commentsCount" to 0L,
                        "timestamp" to System.currentTimeMillis(),
                        "hashtags" to hashtags
                    )
                    postData.putAll(baseMusicMap)
                    try {
                        firestore.collection("posts").document(postId).set(postData).await()
                    } catch (e: Exception) {
                        // Si falla el guardado, no dejar huérfano el archivo en B2
                        Log.w(TAG, "Metadata falló, borrando de B2 y usando base64: ${e.message}")
                        runCatching { storage.deleteFile(storageKey) }
                        if (musicStorageKey.isNotBlank()) runCatching { storage.deleteFile(musicStorageKey) }
                        uploaded = false
                    }
                }

                if (!uploaded) {
                    // Fallback: base64 comprimido (comportamiento original)
                    val compressedBase64 = ImageCompressor.compressToBase64(imageUri, context)
                        ?: throw IllegalStateException("No se pudo comprimir la imagen")
                    val postData = mutableMapOf<String, Any>(
                        "userId" to user.uid,
                        "username" to username,
                        "imageBase64" to compressedBase64,
                        "caption" to caption.trim(),
                        "likesCount" to 0L,
                        "commentsCount" to 0L,
                        "timestamp" to System.currentTimeMillis(),
                        "hashtags" to hashtags
                    )
                    postData.putAll(baseMusicMap)
                    firestore.collection("posts").document(postId).set(postData).await()
                }

                // 4. Incrementar contador postsCount en perfil
                runCatching {
                    firestore.collection("users").document(user.uid)
                        .set(
                            mapOf(
                                "postsCount" to FieldValue.increment(1),
                                "updatedAt" to System.currentTimeMillis()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        ).await()
                }

                _state.value = CreatePostUiState.Success
                Log.d(TAG, "Post publicado OK (b2=$uploaded, music=${musicTitle.isNotBlank()})")
            } catch (e: Exception) {
                Log.e(TAG, "Error publicando post", e)
                _state.value = CreatePostUiState.Error(e.message ?: "Error al publicar")
            }
        }
    }

    fun reset() {
        _state.value = CreatePostUiState.Idle
    }

    companion object {
        private const val TAG = "CreatePostVM"
    }
}

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
    data object SavingMetadata : CreatePostUiState
    data object Success : CreatePostUiState
    data class Error(val message: String) : CreatePostUiState
}

/**
 * Publicación de posts (fotos) con respaldo automático:
 *
 *  1. Comprime la foto a JPEG local (máx 1280px, ~550 KB).
 *  2. Intenta subirla a Backblaze B2 → guarda SOLO la URL firmada en
 *     Firestore (`imageUrl` + `storageKey`), sin base64.
 *  3. Si la subida a B2 falla (red, credenciales, bucket), cae al modo
 *     anterior: base64 comprimido dentro del documento de Firestore.
 *  4. Si el guardado de metadata falla tras subir a B2, borra el archivo
 *     del bucket (best-effort) y cae a base64.
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
    fun publishPost(context: Context, imageUri: Uri, caption: String) {
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

                // 2. Intentar subir a Backblaze B2
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

                if (uploaded) {
                    // Guardar SOLO la URL (sin base64, sin límite 1MB)
                    val hashtags = extractHashtags(caption)
                    val postData = mapOf(
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
                    try {
                        firestore.collection("posts").document(postId).set(postData).await()
                    } catch (e: Exception) {
                        // Si falla el guardado, no dejar huérfano el archivo en B2
                        Log.w(TAG, "Metadata falló, borrando de B2 y usando base64: ${e.message}")
                        runCatching { storage.deleteFile(storageKey) }
                        uploaded = false
                    }
                }

                if (!uploaded) {
                    // Fallback: base64 comprimido (comportamiento original)
                    val compressedBase64 = ImageCompressor.compressToBase64(imageUri, context)
                        ?: throw IllegalStateException("No se pudo comprimir la imagen")
                    val hashtags = extractHashtags(caption)
                    val postData = mapOf(
                        "userId" to user.uid,
                        "username" to username,
                        "imageBase64" to compressedBase64,
                        "caption" to caption.trim(),
                        "likesCount" to 0L,
                        "commentsCount" to 0L,
                        "timestamp" to System.currentTimeMillis(),
                        "hashtags" to hashtags
                    )
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
                Log.d(TAG, "Post publicado OK (b2=$uploaded)")
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

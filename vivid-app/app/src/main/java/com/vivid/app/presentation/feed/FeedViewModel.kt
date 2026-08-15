package com.vivid.app.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val storage: StorageProvider,
    private val postDao: PostDao,
    private val firestore: FirebaseFirestore,
    private val followRepository: FollowRepository
) : ViewModel() {

    // Caché Room (feature futura de offline). La carga Firestore→Room se
    // hace bajo demanda; FeedScreen muestra sus propios datos en vivo.
    val posts: StateFlow<List<PostEntity>> = postDao.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun likePost(postId: String) {
        viewModelScope.launch {
            val currentPost = postDao.getPostById(postId) ?: return@launch
            val newIsLiked = !currentPost.isLiked
            val newLikesCount = (currentPost.likesCount + if (newIsLiked) 1 else -1).coerceAtLeast(0)

            postDao.updateLike(postId, newLikesCount, newIsLiked)

            runCatching {
                firestore.collection("posts")
                    .document(postId)
                    .update("likesCount", FieldValue.increment(if (newIsLiked) 1L else -1L))
                    .await()
            }.onFailure {
                postDao.updateLike(postId, currentPost.likesCount, currentPost.isLiked)
            }
        }
    }

    fun toggleFollowUser(targetUserId: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                followRepository.toggleFollow(targetUserId)
            }.onSuccess { action ->
                val msg = when (action) {
                    FollowActionResult.FOLLOWED -> "Ahora sigues a esta cuenta."
                    FollowActionResult.UNFOLLOWED -> "Dejaste de seguir esta cuenta."
                    FollowActionResult.REQUESTED -> "Solicitud de seguimiento enviada."
                    FollowActionResult.REQUEST_CANCELLED -> "Solicitud cancelada."
                }
                onResult(msg)
            }.onFailure { e ->
                onResult(e.message ?: "No se pudo actualizar el seguimiento.")
            }
        }
    }

    fun toggleSavePost(
        postId: String,
        currentUserId: String,
        shouldSave: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (currentUserId.isBlank() || postId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val savedRef = firestore.collection("users").document(currentUserId)
                    .collection("savedPosts").document(postId)
                if (shouldSave) {
                    savedRef.set(mapOf(
                        "postId" to postId,
                        "savedAt" to System.currentTimeMillis()
                    )).await()
                } else {
                    savedRef.delete().await()
                }
            }.onSuccess {
                onResult(true, if (shouldSave) "Publicación guardada" else "Publicación eliminada de guardados")
            }.onFailure { e ->
                onResult(false, e.message ?: "Error al actualizar guardados")
            }
        }
    }

    /**
     * Obtiene en UNA sola consulta los IDs de posts que el usuario ya dio
     * like (collectionGroup "likes"), en vez de 1 lectura por post (N+1).
     *
     * @return Set de IDs, o null si la consulta falla (p. ej. índice aún no
     *         desplegado) para que el feed caiga al modo anterior.
     */
    suspend fun fetchLikedPostIds(currentUserId: String): Set<String>? {
        if (currentUserId.isBlank()) return emptySet()
        return try {
            firestore.collectionGroup("likes")
                .whereEqualTo("userId", currentUserId)
                .get()
                .await()
                .documents
                // Un like vive en posts/{postId}/likes/{uid}, así que el ID del
                // documento es el UID, no el postId. El ID del post es el del
                // abuelo (parent = colección "likes", parent.parent = el post).
                //
                // Antes se devolvía `it.id` (el UID), así que el feed comparaba
                // postId contra un set de UIDs: nunca coincidía y el corazón
                // aparecía siempre apagado aunque el like sí se guardara.
                //
                // El filtro por path evita mezclar los likes de comentarios
                // (posts/{id}/comments/{id}/likes/{uid}), que caen en la misma
                // consulta de collectionGroup.
                .mapNotNull { doc ->
                    val parent = doc.reference.parent.parent ?: return@mapNotNull null
                    if (parent.parent?.id == "posts") parent.id else null
                }
                .toSet()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Regenera la URL firmada de un archivo en B2 (expira a los 7 días).
     * Devuelve null si falla para que el llamador conserve la URL guardada.
     */
    suspend fun refreshSignedUrl(storageKey: String): String? = try {
        storage.signDownloadUrl(storageKey)
    } catch (e: Exception) {
        null
    }

    /** Borra best-effort un archivo remoto (al eliminar un post). */
    suspend fun deleteRemoteFile(storageKey: String): Boolean =
        runCatching { storage.deleteFile(storageKey) }.getOrDefault(false)
}

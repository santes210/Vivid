package com.vivid.app.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    // NOTA: aquí vivía un likePost() que solo incrementaba likesCount sin
    // escribir el doc en posts/{id}/likes/{uid}. Nadie lo llamaba, pero era
    // una bomba de tiempo: sin el doc de like, el corazón nunca aparece
    // encendido y cada tap suma +1 infinito. El like real es
    // FeedScreen.togglePostLike(), que escribe doc + contador juntos.

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

    /**
     * Guarda los posts visibles en la caché Room (con timestamp de caché).
     * Se llama cuando el feed recibe datos de Firestore.
     *
     * Regla de preservación: si en Room ya vive una URL re-firmada (el feed
     * la regeneró cuando la anterior venció) y el documento de Firestore
     * todavía trae la URL vieja, se conserva la de Room. Así la URL firme
     * sobrevive a los re-cacheos y el disco caché de Coil/ExoPlayer sigue
     * pegando entre sesiones. Las URLs de post NO se editan después de
     * publicar, así que no hay riesgo de conservar una URL desactualizada.
     */
    suspend fun cachePosts(posts: List<PostData>) {
        if (posts.isEmpty()) return
        val now = System.currentTimeMillis()
        val existingById = runCatching { postDao.getPostsOnce() }
            .getOrDefault(emptyList())
            .associateBy { it.id }
        postDao.insertPosts(posts.map { post ->
            val existing = existingById[post.id]
            PostEntity(
                id = post.id,
                userId = post.userId,
                username = post.username,
                userProfilePicture = post.userProfilePicture,
                imageUrl = preferResignedUrl(post.imageUrl, existing?.imageUrl),
                imageBase64 = post.imageBase64,
                caption = post.caption,
                likesCount = post.likesCount,
                commentsCount = post.commentsCount,
                timestamp = post.timestamp,
                isLiked = post.isLiked,
                storageKey = post.storageKey,
                videoUrl = preferResignedUrl(post.videoUrl, existing?.videoUrl),
                thumbnailUrl = preferResignedUrl(post.thumbnailUrl, existing?.thumbnailUrl),
                isVideo = post.isVideo,
                musicTitle = post.musicTitle,
                musicArtist = post.musicArtist,
                musicAssetFile = post.musicAssetFile,
                musicUrl = preferResignedUrl(post.musicUrl, existing?.musicUrl),
                musicStorageKey = post.musicStorageKey,
                cachedAt = now
            )
        })
    }

    /**
     * Si el caché tiene una URL distinta a la del documento (la re-firmada
     * localmente) y no está vacía, se queda con la del caché.
     */
    private fun preferResignedUrl(incoming: String, cached: String?): String {
        if (cached.isNullOrBlank()) return incoming
        if (incoming.isBlank()) return cached
        return if (cached != incoming) cached else incoming
    }

    /** Borra un post del caché Room (post borrado en el servidor). */
    suspend fun deleteCachedPost(postId: String) {
        postDao.deletePost(postId)
    }

    /** Persiste la URL de imagen re-firmada para la próxima sesión. */
    suspend fun saveResignedImageUrl(postId: String, url: String) {
        postDao.updateImageUrl(postId, url)
    }

    /** Persiste la URL de música re-firmada para la próxima sesión. */
    suspend fun saveResignedMusicUrl(postId: String, url: String) {
        postDao.updateMusicUrl(postId, url)
    }

    /** Persiste la URL de video re-firmada para la próxima sesión. */
    suspend fun saveResignedVideoUrl(postId: String, url: String) {
        postDao.updateVideoUrl(postId, url)
    }

    /**
     * Convierte entidades Room cacheadas a [PostData] para mostrar offline
     * o mientras se refresca Firestore.
     */
    fun cachedPostsToData(entities: List<PostEntity>): List<PostData> =
        entities.map { entity ->
            PostData(
                id = entity.id,
                userId = entity.userId,
                username = entity.username,
                userProfilePicture = entity.userProfilePicture,
                imageUrl = entity.imageUrl,
                imageBase64 = entity.imageBase64,
                storageKey = entity.storageKey,
                videoUrl = entity.videoUrl,
                thumbnailUrl = entity.thumbnailUrl,
                isVideo = entity.isVideo,
                caption = entity.caption,
                likesCount = entity.likesCount,
                commentsCount = entity.commentsCount,
                timestamp = entity.timestamp,
                isLiked = entity.isLiked,
                musicTitle = entity.musicTitle,
                musicArtist = entity.musicArtist,
                musicAssetFile = entity.musicAssetFile,
                musicUrl = entity.musicUrl,
                musicStorageKey = entity.musicStorageKey
            )
        }

    /**
     * Indica si la caché de posts sigue vigente (menos de 7 días).
     */
    suspend fun isPostCacheFresh(): Boolean {
        val lastCached = postDao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < 7L * 24L * 60L * 60L * 1000L
    }

    /** Devuelve los posts cacheados en Room (una sola lectura). */
    suspend fun getCachedPosts(): List<PostEntity> = postDao.getPostsOnce()
}

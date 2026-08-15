package com.vivid.app.presentation.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.data.local.dao.ReelDao
import com.vivid.app.data.local.entity.ReelEntity
import com.vivid.app.data.storage.StorageProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * ViewModel de Reels con scroll infinito estilo TikTok.
 *
 * - Carga inicial 15 reels orderBy timestamp DESC
 * - Paginación real con startAfter(lastDoc)
 * - Al deslizar cerca del final, carga automáticamente más (loadMore)
 * - Regenera URLs firmadas de B2 si hay storageKey (TTL 7d)
 * - Método uploadReel mantiene compatibilidad
 */
@HiltViewModel
class ReelsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: StorageProvider,
    private val auth: FirebaseAuth,
    private val reelDao: ReelDao
) : ViewModel() {

    private val _reels = MutableStateFlow<List<Reel>>(emptyList())
    val reels: StateFlow<List<Reel>> = _reels

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private var lastDoc: DocumentSnapshot? = null
    private val pageSize = 15

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasMore.value = true
            lastDoc = null

            // ── Caché Room: mostrar reels cacheados (menos de 7 días) al instante ──
            runCatching {
                if (isReelCacheFresh()) {
                    val cached = reelDao.getReelsOnce()
                    if (cached.isNotEmpty()) {
                        _reels.value = cachedEntitiesToData(cached)
                        _isLoading.value = false
                    }
                }
            }

            try {
                val userId = auth.currentUser?.uid.orEmpty()
                val followingIds = if (userId.isBlank()) emptyList() else {
                    firestore.collection("users").document(userId).collection("following")
                        .get().await().documents.map { it.id }
                }
                val privateAuthorChunks = (followingIds + userId)
                    .filter { it.isNotBlank() }.distinct().chunked(30)

                val (publicSnapshot, privateDocuments) = coroutineScope {
                    val publicRequest = async {
                        firestore.collection("reels")
                            .whereEqualTo("isPrivate", false)
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .limit(pageSize.toLong())
                            .get().await()
                    }
                    val privateRequests = privateAuthorChunks.map { authors ->
                        async {
                            firestore.collection("reels")
                                .whereIn("userId", authors)
                                .whereEqualTo("isPrivate", true)
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .limit(pageSize.toLong())
                                .get().await().documents
                        }
                    }
                    publicRequest.await() to privateRequests.awaitAll().flatten()
                }

                lastDoc = publicSnapshot.documents.lastOrNull()
                val documents = (publicSnapshot.documents + privateDocuments)
                    .distinctBy { it.id }
                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                val fresh = mapDocs(documents)
                _reels.value = fresh
                _hasMore.value = publicSnapshot.size() >= pageSize

                // Persistir en caché Room
                if (fresh.isNotEmpty()) {
                    cacheReels(fresh)
                }
            } catch (e: Exception) {
                // Si falla la red pero hay caché, la dejamos visible
                if (_reels.value.isEmpty()) {
                    _reels.value = emptyList()
                    _hasMore.value = false
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Caché local de reels (Room, TTL 7 días) ──

    private suspend fun isReelCacheFresh(): Boolean {
        val lastCached = reelDao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < 7L * 24L * 60L * 60L * 1000L
    }

    private suspend fun cacheReels(reels: List<Reel>) {
        if (reels.isEmpty()) return
        val now = System.currentTimeMillis()
        reelDao.insertReels(reels.map { reel ->
            ReelEntity(
                id = reel.id,
                userId = reel.userId,
                username = reel.username,
                userAvatar = reel.userAvatar,
                videoUrl = reel.videoUrl,
                thumbnailUrl = reel.thumbnailUrl,
                caption = reel.caption,
                likes = reel.likes,
                commentsCount = reel.commentsCount,
                timestamp = reel.timestamp,
                isPrivate = reel.isPrivate,
                storageKey = reel.storageKey,
                cachedAt = now
            )
        })
    }

    private fun cachedEntitiesToData(entities: List<ReelEntity>): List<Reel> =
        entities.map { entity ->
            Reel(
                id = entity.id,
                userId = entity.userId,
                videoUrl = entity.videoUrl,
                thumbnailUrl = entity.thumbnailUrl,
                username = entity.username,
                caption = entity.caption,
                likes = entity.likes,
                commentsCount = entity.commentsCount,
                userAvatar = entity.userAvatar,
                storageKey = entity.storageKey
            )
        }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val currentLast = lastDoc ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val snapshot = firestore.collection("reels")
                    .whereEqualTo("isPrivate", false)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(currentLast)
                    .limit(pageSize.toLong())
                    .get()
                    .await()

                if (snapshot.documents.isEmpty()) {
                    _hasMore.value = false
                } else {
                    lastDoc = snapshot.documents.lastOrNull()
                    val mapped = mapDocs(snapshot.documents)
                    // Evitar duplicados por id
                    val existingIds = _reels.value.map { it.id }.toSet()
                    val newOnes = mapped.filter { it.id !in existingIds }
                    if (newOnes.isNotEmpty()) {
                        _reels.value = _reels.value + newOnes
                    }
                    _hasMore.value = snapshot.documents.size >= pageSize
                }
            } catch (e: Exception) {
                // No fatal, solo dejamos de intentar si es error de índice
                // _hasMore stays true to allow retry
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun refresh() {
        loadInitial()
    }

    private suspend fun mapDocs(docs: List<DocumentSnapshot>): List<Reel> = coroutineScope {
        docs.mapNotNull { doc ->
            async {
                try {
                    val storageKey = doc.getString("storageKey").orEmpty()
                    val savedUrl = doc.getString("videoUrl").orEmpty()
                    val thumbSaved = doc.getString("thumbnailUrl").orEmpty()
                    val thumbKey = if (storageKey.isNotBlank() && thumbSaved.isBlank()) null else null

                    // Regenerar URL firmada fresca desde storageKey (expira 7d)
                    // Si falla, usa la guardada
                    val videoUrl = if (storageKey.isNotBlank()) {
                        try { storage.signDownloadUrl(storageKey) } catch (_: Exception) { savedUrl }
                    } else savedUrl

                    // Thumbnail también puede estar en B2 con key separado, intentar re-firmar si es necesario
                    // Si thumbnailUrl ya es http y no es de B2, lo dejamos
                    val thumbnailUrl = doc.getString("thumbnailUrl").orEmpty()

                    if (videoUrl.isBlank()) return@async null

                    Reel(
                        id = doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        videoUrl = videoUrl,
                        thumbnailUrl = thumbnailUrl,
                        username = doc.getString("username") ?: "usuario",
                        caption = doc.getString("caption").orEmpty(),
                        likes = doc.getLong("likes")?.toInt() ?: 0,
                        commentsCount = doc.getLong("comments")?.toInt() ?: 0,
                        userAvatar = doc.getString("userAvatar").orEmpty(),
                        storageKey = storageKey,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isPrivate = doc.getBoolean("isPrivate") ?: false
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Sube un reel. El path debe venir ya comprimido.
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
            val isPrivate = userSnapshot.getBoolean("isPrivate") ?: false

            val timestamp = System.currentTimeMillis()
            val remoteKey = "reels/${user.uid}/$timestamp.mp4"

            val publicUrl = storage.uploadFile(compressedVideoFilePath, remoteKey)

            val reelData = mapOf(
                "userId" to user.uid,
                "username" to username,
                "userAvatar" to userAvatar,
                "isPrivate" to isPrivate,
                "videoUrl" to publicUrl,
                "storageKey" to remoteKey,
                "provider" to "backblaze",
                "caption" to caption,
                "likes" to 0,
                "comments" to 0,
                "timestamp" to timestamp
            )

            val docRef = firestore.collection("reels").add(reelData).await()
            try {
                firestore.collection("users").document(user.uid).update(
                    "reelsCount", com.google.firebase.firestore.FieldValue.increment(1),
                    "updatedAt", System.currentTimeMillis()
                ).await()
            } catch (_: Exception) {}
            // Recargar para que aparezca arriba
            loadInitial()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

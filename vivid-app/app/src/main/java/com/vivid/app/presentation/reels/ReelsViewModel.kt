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
import com.vivid.app.util.CrashReporter
import com.vivid.app.util.toUserFacingMessage
import com.vivid.app.util.withNetworkTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

private const val TAG = "ReelsViewModel"

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

    /**
     * Error de la carga inicial, mostrado por ReelsScreen con botón de
     * reintento (retry()). Queda en null cuando hay contenido, aunque sea
     * de caché: el banner de sin conexión ya comunica el resto.
     */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var lastDoc: DocumentSnapshot? = null
    private val pageSize = 15

    /** TTL de las URLs firmadas de B2 (7 días). */
    private val signedUrlTtlMs = 7L * 24L * 60L * 60L * 1000L

    /**
     * Una URL firmada cacheada solo se reutiliza si le quedan más de 24 h.
     * Si está por vencer, se pide una nueva para que el usuario no sufra el
     * corte de reproducción a mitad de sesión.
     */
    private val reuseThresholdMs = 24L * 60L * 60L * 1000L

    init {
        loadInitial()
    }

    fun loadInitial() {
        viewModelScope.launch {
            _isLoading.value = true
            _hasMore.value = true
            _errorMessage.value = null
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
                // Timeout global de la carga inicial: si la red cuelga, la
                // UI muestra el error con reintento en vez de un spinner eterno.
                val (publicSnapshot, privateDocuments) = withNetworkTimeout("reels.loadInitial") {
                    val followingIds = if (userId.isBlank()) emptyList() else {
                        firestore.collection("users").document(userId).collection("following")
                            .get().await().documents.map { it.id }
                    }
                    val privateAuthorChunks = (followingIds + userId)
                        .filter { it.isNotBlank() }.distinct().chunked(30)

                    coroutineScope {
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
                }

                lastDoc = publicSnapshot.documents.lastOrNull()
                val documents = (publicSnapshot.documents + privateDocuments)
                    .distinctBy { it.id }
                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                val fresh = mapDocs(documents)
                _reels.value = fresh
                _hasMore.value = publicSnapshot.size() >= pageSize

                // Persistir en caché Room + purgar reels que ya no existen
                // en el servidor (borrados o cuya privacidad cambió).
                if (fresh.isNotEmpty()) {
                    cacheReels(fresh)
                    purgeDeletedReels(fresh.map { it.id }.toSet())
                }
            } catch (e: Exception) {
                CrashReporter.recordNonFatal(TAG, e, "Carga inicial de reels falló")
                // Si falla la red pero hay caché (aunque esté vencida),
                // mostrarla: mejor contenido viejo que pantalla vacía.
                if (_reels.value.isEmpty()) {
                    val stale = runCatching { reelDao.getReelsOnce() }.getOrDefault(emptyList())
                    _reels.value = if (stale.isNotEmpty()) cachedEntitiesToData(stale) else emptyList()
                    _hasMore.value = false
                }
                // El error queda visible para la UI; si hay caché mostrada,
                // ReelsScreen prioriza el contenido y solo deja el banner
                // de sin conexión comunicando el resto.
                _errorMessage.value = e.toUserFacingMessage("No se pudieron cargar los reels.")
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
                videoUrlExpiresAt = reel.videoUrlExpiresAt,
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
                storageKey = entity.storageKey,
                videoUrlExpiresAt = entity.videoUrlExpiresAt
            )
        }

    /**
     * Purga del caché Room los reels que ya no existen en Firestore (borrados
     * por su autor o inaccesibles por cambio de privacidad). Evita "fantasmas"
     * que se quedaban en la DB para siempre.
     *
     * Usa una query `whereIn(documentId)` (1 lectura por cada 30 ids) SOLO
     * para los ids cacheados que no aparecieron en la carga fresca.
     */
    private suspend fun purgeDeletedReels(visibleIds: Set<String>) {
        runCatching {
            val cachedIds = reelDao.getReelsOnce().map { it.id }
            val missing = cachedIds.filter { it !in visibleIds }
            if (missing.isEmpty()) return@runCatching
            val stillExists = missing.chunked(30).flatMap { chunk ->
                runCatching {
                    firestore.collection("reels")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get()
                        .await()
                        .documents
                        .map { it.id }
                }.getOrDefault(emptyList())
            }.toSet()
            val gone = missing.filter { it !in stillExists }
            if (gone.isNotEmpty()) {
                reelDao.deleteReels(gone)
            }
        }
    }

    /**
     * Re-firma la URL del reel después de un error de reproducción
     * (típicamente URL vencida) y actualiza estado + caché Room.
     * El cambio de `videoUrl` hace que ReelsScreen recree el ExoPlayer.
     */
    suspend fun refreshReelUrl(reelId: String): Boolean {
        val reel = _reels.value.find { it.id == reelId } ?: return false
        if (reel.storageKey.isBlank()) return false
        return try {
            val freshUrl = storage.signDownloadUrl(reel.storageKey)
            check(freshUrl.isNotBlank()) { "URL firmada vacía" }
            val updated = reel.copy(
                videoUrl = freshUrl,
                videoUrlExpiresAt = System.currentTimeMillis() + signedUrlTtlMs
            )
            _reels.value = _reels.value.map { if (it.id == reelId) updated else it }
            cacheReels(listOf(updated))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || !_hasMore.value) return
        val currentLast = lastDoc ?: return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val snapshot = withNetworkTimeout("reels.loadMore") {
                    firestore.collection("reels")
                        .whereEqualTo("isPrivate", false)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .startAfter(currentLast)
                        .limit(pageSize.toLong())
                        .get()
                        .await()
                }

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
                        // Las páginas siguientes también entran al caché Room:
                        // al reabrir la app el scroll conserva lo ya visto.
                        cacheReels(newOnes)
                    }
                    _hasMore.value = snapshot.documents.size >= pageSize
                }
            } catch (e: Exception) {
                // No fatal: se registra para diagnóstico y _hasMore queda
                // true para reintentar en el siguiente scroll.
                CrashReporter.recordNonFatal(TAG, e, "Paginación de reels falló")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun refresh() {
        loadInitial()
    }

    private suspend fun mapDocs(docs: List<DocumentSnapshot>): List<Reel> = coroutineScope {
        // URLs firmadas cacheadas en Room por storageKey. Si una sigue vigente
        // (le quedan >24h), se reutiliza: NO se llama a B2 en cada carga y la
        // URL estable permite que el caché de video de ExoPlayer pegue entre
        // sesiones (el CacheKeyFactory ya ignora el query de autorización).
        val cachedByKey = runCatching { reelDao.getReelsOnce() }
            .getOrDefault(emptyList())
            .filter { it.storageKey.isNotBlank() }
            .associateBy { it.storageKey }

        docs.mapNotNull { doc ->
            async {
                try {
                    val storageKey = doc.getString("storageKey").orEmpty()
                    val savedUrl = doc.getString("videoUrl").orEmpty()
                    val now = System.currentTimeMillis()
                    val cached = cachedByKey[storageKey]
                    // `usable` se reutiliza como guarda de null: su tipo
                    // no-null permite acceder a las propiedades sin !!
                    val usable = cached?.takeIf {
                        it.videoUrl.isNotBlank() &&
                            it.videoUrlExpiresAt > now + reuseThresholdMs
                    }

                    val videoUrl: String
                    val expiresAt: Long
                    when {
                        storageKey.isBlank() -> {
                            videoUrl = savedUrl
                            expiresAt = cached?.videoUrlExpiresAt ?: 0L
                        }
                        usable != null -> {
                            // Reutilizar la URL firmada cacheada (estable → hit de caché)
                            videoUrl = usable.videoUrl
                            expiresAt = usable.videoUrlExpiresAt
                        }
                        else -> {
                            // URL guardada en Firestore vencida/por vencer o sin caché:
                            // pedir una firma fresca a B2. Si falla (sin red),
                            // degradar a la URL guardada.
                            val freshSigned = try {
                                storage.signDownloadUrl(storageKey)
                            } catch (_: Exception) {
                                null
                            }
                            if (freshSigned != null) {
                                videoUrl = freshSigned
                                expiresAt = now + signedUrlTtlMs
                            } else {
                                videoUrl = savedUrl
                                expiresAt = 0L
                            }
                        }
                    }

                    if (videoUrl.isBlank()) return@async null

                    Reel(
                        id = doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        videoUrl = videoUrl,
                        thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                        username = doc.getString("username") ?: "usuario",
                        caption = doc.getString("caption").orEmpty(),
                        likes = doc.getLong("likes")?.toInt() ?: 0,
                        commentsCount = doc.getLong("comments")?.toInt() ?: 0,
                        userAvatar = doc.getString("userAvatar").orEmpty(),
                        storageKey = storageKey,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        isPrivate = doc.getBoolean("isPrivate") ?: false,
                        videoUrlExpiresAt = expiresAt
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

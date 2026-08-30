package com.vivid.app.domain.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.data.local.dao.HashtagDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.HashtagEntity
import com.vivid.app.data.local.entity.toPostData
import com.vivid.app.domain.model.PostVisibility
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.util.Hashtags
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Hashtags reales de Vivid: descubrimiento + cache offline.
 *
 * Antes los chips de Explorar eran una lista fija ("vivid", "arte", ...) así
 * que un #tag nuevo publicado por un usuario JAMÁS aparecía. Ahora:
 *
 *  1. [refresh] escanea los posts públicos recientes (una sola query con el
 *     índice isPrivate+timestamp que ya existe) y agrega `hashtags` con
 *     conteo. Los posts "solo amigos" no cuentan para el descubrimiento.
 *  2. El agregado se guarda en Room (`hashtags`) y [observeCached] lo emite:
 *     los chips existen desde el primer frame y sobreviven sin conexión.
 *  3. [cachedPostsForTag] sirve el grid de un tag desde el cache de posts
 *     cuando Firestore no está disponible.
 *
 * Un tag sale del catálogo cuando hace [STALE_MS] que no aparece en posts
 * recientes (se poda en el mismo refresh), así la lista respira con el uso
 * real y no crece para siempre.
 */
@Singleton
class HashtagRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val hashtagDao: HashtagDao,
    private val postDao: PostDao
) {

    companion object {
        /** Cuántos posts públicos recientes se escanean por refresh. */
        const val SCAN_LIMIT = 250L

        /** Tags sin aparecer en 7 días salen del catálogo. */
        const val STALE_MS = 7L * 24 * 60 * 60 * 1000

        /** Tope de posts cacheados servidos en el fallback offline. */
        const val OFFLINE_LIMIT = 60
    }

    /** Chips de Explorar (Room): ordenados por popularidad. */
    fun observeCached(): Flow<List<HashtagEntity>> = hashtagDao.observeAll()

    /**
     * Re-agrega hashtags desde Firestore y persiste el resultado.
     * Lanza si no hay red; el cache anterior queda intacto (quien llama
     * decide ignorar el fallo en modo offline).
     */
    suspend fun refresh() {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("isPrivate", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(SCAN_LIMIT)
            .get()
            .await()

        val now = System.currentTimeMillis()
        val counts = LinkedHashMap<String, Int>()
        snapshot.documents.forEach { doc ->
            // "Solo amigos" es contenido de seguidores, no descubrimiento.
            if (doc.getString("visibility") == PostVisibility.FRIENDS.value) return@forEach
            (doc.get("hashtags") as? List<*>)?.forEach { raw ->
                val tag = Hashtags.normalize(raw.toString())
                if (tag.isNotBlank()) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }

        if (counts.isNotEmpty()) {
            hashtagDao.upsertAll(counts.map { (tag, count) -> HashtagEntity(tag, count, now) })
        }
        hashtagDao.pruneOlderThan(now - STALE_MS)
    }

    /**
     * Grid offline de un tag: posts ya cacheados (por el feed o por el propio
     * Explorar) que llevan exactamente este hashtag.
     */
    suspend fun cachedPostsForTag(tag: String): List<PostData> {
        val normalized = Hashtags.normalize(tag)
        if (normalized.isBlank()) return emptyList()
        return postDao.getPostsByHashtag(normalized, OFFLINE_LIMIT).map { it.toPostData() }
    }
}

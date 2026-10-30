package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.data.local.dao.HashtagDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.entity.toPostData
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.data.paging.mapExplorePost
import com.vivid.app.data.paging.mapSearchUser
import com.vivid.app.domain.model.PostVisibility
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.presentation.search.SearchTagResult
import com.vivid.app.presentation.search.SearchUser
import com.vivid.app.presentation.search.SmartSearch
import com.vivid.app.util.Hashtags
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buscador inteligente de Vivid: personas + hashtags + publicaciones.
 *
 * Una sola entrada para las tres secciones que antes vivían separadas.
 * Firestore no ofrece texto completo, así que la estrategia es combinar
 * fuentes y dejar que [SmartSearch] ordene por relevancia:
 *
 *  - **Personas**: prefijo sobre `usernameLower`/`displayNameLower` y, para
 *    tolerar tildes, sobre `usernameFold`/`displayNameFold` (campos nuevos;
 *    los documentos legacy siguen cubiertos por los `*Lower`).
 *  - **Hashtags**: el catálogo Room descubierto por [HashtagRepository]
 *    (offline), más los tags curados y el `#tag` escrito tal cual.
 *  - **Publicaciones**: `#tag` → query real array-contains (contrato de
 *    Explorar); caption → prefijo sobre `captionFold` (posts nuevos) + una
 *    pasada por el cache Room que cubre posts legados, mitad de palabra y
 *    modo offline.
 *
 * Los resultados se acotan ([SmartSearch] `*_LIMIT`): es un buscador de
 * "mejores resultados", no un listado infinito.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val hashtagDao: HashtagDao,
    private val postDao: PostDao,
    private val hashtagRepository: HashtagRepository
) {

    companion object {
        /** Docs leídos por cada consulta de prefijo de personas. */
        const val USER_QUERY_LIMIT = 10L

        /** Docs leídos por cada consulta de publicaciones. */
        const val POST_QUERY_LIMIT = 12L

        /** Ventana del cache Room barajada en el fallback de captions. */
        const val CACHE_SCAN_LIMIT = 200
    }

    /**
     * Mantiene fresco el catálogo de hashtags (alimento de la sección
     * "Temas"). Sin red no es un error: el cache anterior sigue sirviendo.
     */
    suspend fun refreshTagCatalog() {
        runCatching { hashtagRepository.refresh() }
    }

    /**
     * Personas por prefijo de @username o de nombre visible, con ranking.
     * Un `#tag` es una búsqueda de tag, no de personas.
     */
    suspend fun searchUsers(rawQuery: String, blockedUserIds: Set<String>): List<SearchUser> {
        if (SmartSearch.tagIntent(rawQuery) != null) return emptyList()
        val handle = SmartSearch.stripHandle(rawQuery).removePrefix("#").trim()
        if (!ExplorePaging.isValidUserQuery(handle.lowercase())) return emptyList()
        val lower = handle.lowercase()
        val folded = SmartSearch.fold(handle)
        val currentUserId = auth.currentUser?.uid.orEmpty()

        suspend fun prefixDocs(field: String, q: String): List<DocumentSnapshot> =
            firestore.collection("users")
                .orderBy(field)
                .startAt(q)
                .endAt(ExplorePaging.usernamePrefixEnd(q))
                .limit(USER_QUERY_LIMIT)
                .get()
                .await()
                .documents

        val docs = buildList {
            addAll(prefixDocs("usernameLower", lower))
            addAll(prefixDocs("displayNameLower", lower))
            // Los `*Fold` cubren búsquedas sin tildes sobre nombres con tildes
            // (p. ej. "maria" → "María"); los legacy no los tienen y ya
            // entraron por los `*Lower`.
            addAll(prefixDocs("usernameFold", folded))
            addAll(prefixDocs("displayNameFold", folded))
        }

        val users = docs.distinctBy { it.id }
            .mapNotNull { mapSearchUser(it, currentUserId, blockedUserIds) }
        return SmartSearch.rankUsers(rawQuery, users).take(SmartSearch.PEOPLE_LIMIT)
    }

    /**
     * Hashtags reales por subcadena + curados + el `#tag` escrito, con
     * ranking (coincidencia y popularidad). Fuente principal: el catálogo
     * Room de [HashtagRepository]; funciona offline.
     */
    suspend fun searchTags(rawQuery: String): List<SearchTagResult> {
        val typed = Hashtags.parseQuery(rawQuery)
        val needle = typed ?: Hashtags.normalize(rawQuery)
        if (needle.isEmpty()) return emptyList()

        val cached = hashtagDao.search(needle, SmartSearch.TAG_LIMIT * 2)
        val merged = LinkedHashMap<String, SearchTagResult>()
        typed?.let { merged[it] = SearchTagResult(it, 0) }
        cached.forEach { merged[it.tag] = SearchTagResult(it.tag, it.count) }
        ExplorePaging.TAGS.forEach { merged.putIfAbsent(it, SearchTagResult(it, 0)) }

        return SmartSearch.rankTags(needle, merged.values.toList()).take(SmartSearch.TAG_LIMIT)
    }

    /**
     * Publicaciones por hashtag o por texto del caption, con ranking.
     *
     * Privacidad: las tres fuentes respetan el mismo criterio que Explorar —
     * sin cuentas privadas (`isPrivate`) y sin "solo amigos" ([PostVisibility.FRIENDS]).
     */
    suspend fun searchPosts(rawQuery: String, blockedUserIds: Set<String>): List<PostData> {
        val typed = Hashtags.parseQuery(rawQuery)
        if (typed == null && !SmartSearch.isSearchable(rawQuery)) return emptyList()

        val merged = LinkedHashMap<String, PostData>()

        // 1. `#tag` → query real por array-contains (mismo contrato que Explorar).
        if (typed != null) {
            runCatching {
                firestore.collection("posts")
                    .whereArrayContains("hashtags", typed)
                    .whereEqualTo("isPrivate", false)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(POST_QUERY_LIMIT)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { mapExplorePost(it, blockedUserIds) }
            }.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.id, it) }
        }

        // 2. Caption: prefijo sobre `captionFold` (posts con el campo nuevo).
        val folded = SmartSearch.fold(SmartSearch.stripHandle(rawQuery).removePrefix("#").trim())
        if (folded.isNotEmpty()) {
            runCatching {
                firestore.collection("posts")
                    .whereEqualTo("isPrivate", false)
                    .orderBy("captionFold")
                    .startAt(folded)
                    .endAt(ExplorePaging.usernamePrefixEnd(folded))
                    .limit(POST_QUERY_LIMIT)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { mapExplorePost(it, blockedUserIds) }
            }.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.id, it) }
        }

        // 3. Cache Room: posts legados sin `captionFold`, mitad de palabra y
        //    offline. Sin "solo amigos" (columna `visibility`, migración 8→9).
        runCatching {
            postDao.getRecentPosts(CACHE_SCAN_LIMIT)
                .map { it.toPostData() }
                .filter { post ->
                    post.visibility != PostVisibility.FRIENDS.value &&
                        post.userId !in blockedUserIds &&
                        SmartSearch.postRank(rawQuery, post) != null
                }
        }.getOrDefault(emptyList()).forEach { merged.putIfAbsent(it.id, it) }

        return SmartSearch.rankPosts(rawQuery, merged.values.toList()).take(SmartSearch.POST_LIMIT)
    }
}

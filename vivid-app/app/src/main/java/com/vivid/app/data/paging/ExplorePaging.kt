package com.vivid.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.domain.model.PostVisibility
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.util.Hashtags
import kotlinx.coroutines.tasks.await

/**
 * Constantes y helpers de paginación de Explorar / Búsqueda.
 * Viven fuera de Compose para poder testearlos sin Android.
 */
object ExplorePaging {
    const val POST_PAGE_SIZE = 18
    const val USER_PAGE_SIZE = 20
    const val MIN_QUERY_LENGTH = 2
    const val PREFETCH_DISTANCE = 6

    /**
     * Temas curados de Explorar. La clave coincide con [Hashtags.normalize]
     * (sin tildes) para que `#música` y el chip "musica" peguen en Firestore.
     */
    val TAGS = listOf("vivid", "arte", "musica", "viaje", "comida", "tecnologia", "moda", "deporte")

    fun normalizeQuery(raw: String): String = raw.trim().lowercase()

    /**
     * Búsqueda de personas. Un `#tag` no es un username: si no se excluye,
     * Firestore busca usuarios que empiezan por `#arte` y no sale nadie.
     */
    fun isValidUserQuery(query: String): Boolean {
        if (query.startsWith("#")) return false
        return query.length >= MIN_QUERY_LENGTH
    }

    /** Cota superior de un prefijo de username (rango Firestore startAt/endAt). */
    fun usernamePrefixEnd(query: String): String = query + "\uf8ff"

    /**
     * Cada 5 ítems el grid pinta uno a doble columna. El patrón llena filas
     * de 3 celdas: [2+1] [1+1+1] [2+1] … sin huecos.
     */
    fun isFeaturedIndex(index: Int): Boolean = index >= 0 && index % 5 == 0

    /**
     * Chips visibles: los curados, y si el usuario buscó un tag suelto
     * (`#atardecer`) se antepone para que se vea seleccionado.
     */
    fun visibleTags(selected: String): List<String> {
        val tag = Hashtags.normalize(selected)
        if (tag.isEmpty() || tag in TAGS) return TAGS
        return listOf(tag) + TAGS
    }
}

/**
 * PagingSource cursor-based sobre `posts` filtrados por hashtag.
 *
 * Clave = último [DocumentSnapshot] de la página. Firestore no ofrece offset,
 * así que Paging 3 + startAfter es la paginación real (no "cargar todo").
 */
class ExplorePostsPagingSource(
    private val firestore: FirebaseFirestore,
    private val tag: String,
    private val blockedUserIds: Set<String>,
    private val pageSize: Int = ExplorePaging.POST_PAGE_SIZE,
    /** Cada página cargada se reporta aquí para alimentar el cache Room. */
    private val onPostsLoaded: (suspend (List<PostData>) -> Unit)? = null
) : PagingSource<DocumentSnapshot, PostData>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, PostData>): DocumentSnapshot? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey ?: page.nextKey
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, PostData> {
        return try {
            var query: Query = firestore.collection("posts")
                .whereArrayContains("hashtags", tag)
                .whereEqualTo("isPrivate", false)
                .orderBy("timestamp", Query.Direction.DESCENDING)
            val cursor = params.key
            if (cursor != null) query = query.startAfter(cursor)
            val snapshot = query.limit(pageSize.toLong()).get().await()
            val data = snapshot.documents.mapNotNull { doc ->
                mapExplorePost(doc, blockedUserIds)
            }
            val last = snapshot.documents.lastOrNull()
            if (data.isNotEmpty()) onPostsLoaded?.invoke(data)
            LoadResult.Page(
                data = data,
                prevKey = null,
                nextKey = if (snapshot.documents.size < pageSize) null else last
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

internal fun mapExplorePost(doc: DocumentSnapshot, blockedUserIds: Set<String>): PostData? {
    val id = doc.id
    val userId = doc.getString("userId") ?: ""
    if (userId.isBlank() || userId in blockedUserIds) return null
    // "Solo amigos" nunca entra en Explorar: es contenido para seguidores,
    // no descubrimiento público. La query no puede filtrarlo (los posts
    // legacy no tienen el campo) así que se descarta aquí, por página.
    val visibility = doc.getString("visibility") ?: PostVisibility.PUBLIC.value
    if (visibility == PostVisibility.FRIENDS.value) return null
    return PostData(
        id = id,
        userId = userId,
        username = doc.getString("username") ?: "",
        userProfilePicture = "",
        userProfilePictureBase64 = "",
        imageUrl = doc.getString("imageUrl") ?: "",
        videoUrl = doc.getString("videoUrl") ?: "",
        isVideo = doc.getBoolean("isVideo") ?: false,
        thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
        caption = doc.getString("caption") ?: "",
        likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
        commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
        timestamp = doc.getLong("timestamp") ?: 0L,
        isLiked = false,
        isSaved = false,
        storageKey = doc.getString("storageKey") ?: "",
        hashtags = (doc.get("hashtags") as? List<*>)?.mapNotNull { it as? String }
            ?.map { Hashtags.normalize(it) }
            ?.filter { it.isNotEmpty() }
            .orEmpty(),
        visibility = visibility
    )
}

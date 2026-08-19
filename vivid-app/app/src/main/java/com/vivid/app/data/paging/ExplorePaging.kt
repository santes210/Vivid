package com.vivid.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.presentation.feed.PostData
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

    val TAGS = listOf("vivid", "arte", "musica", "viaje", "comida", "tecnologia", "moda", "deporte")

    fun normalizeQuery(raw: String): String = raw.trim().lowercase()

    fun isValidUserQuery(query: String): Boolean = query.length >= MIN_QUERY_LENGTH

    /** Cota superior de un prefijo de username (rango Firestore startAt/endAt). */
    fun usernamePrefixEnd(query: String): String = query + "\uf8ff"
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
    private val pageSize: Int = ExplorePaging.POST_PAGE_SIZE
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
    return PostData(
        id = id,
        userId = userId,
        username = doc.getString("username") ?: "",
        userProfilePicture = "",
        userProfilePictureBase64 = "",
        imageUrl = doc.getString("imageUrl") ?: "",
        videoUrl = doc.getString("videoUrl") ?: "",
        isVideo = doc.getBoolean("isVideo") ?: false,
        caption = doc.getString("caption") ?: "",
        likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
        commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
        timestamp = doc.getLong("timestamp") ?: 0L,
        isLiked = false,
        isSaved = false,
        storageKey = doc.getString("storageKey") ?: ""
    )
}

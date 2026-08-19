package com.vivid.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.presentation.search.SearchUser
import kotlinx.coroutines.tasks.await

/**
 * Paginación real de usuarios por prefijo de username.
 * Primera página: startAt(query) … endAt(query+\uf8ff).
 * Siguientes: startAfter(último doc) manteniendo el techo del prefijo.
 */
class UserSearchPagingSource(
    private val firestore: FirebaseFirestore,
    private val rawQuery: String,
    private val currentUserId: String,
    private val blockedUserIds: Set<String>,
    private val pageSize: Int = ExplorePaging.USER_PAGE_SIZE
) : PagingSource<DocumentSnapshot, SearchUser>() {

    override fun getRefreshKey(state: PagingState<DocumentSnapshot, SearchUser>): DocumentSnapshot? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey ?: page.nextKey
    }

    override suspend fun load(params: LoadParams<DocumentSnapshot>): LoadResult<DocumentSnapshot, SearchUser> {
        val query = ExplorePaging.normalizeQuery(rawQuery)
        if (!ExplorePaging.isValidUserQuery(query)) {
            return LoadResult.Page(emptyList(), prevKey = null, nextKey = null)
        }
        return try {
            var firestoreQuery: Query = firestore.collection("users")
                .orderBy("usernameLower")
                .endAt(ExplorePaging.usernamePrefixEnd(query))
            val cursor = params.key
            firestoreQuery = if (cursor != null) {
                firestoreQuery.startAfter(cursor)
            } else {
                firestoreQuery.startAt(query)
            }
            val snapshot = firestoreQuery.limit(pageSize.toLong()).get().await()
            val data = snapshot.documents.mapNotNull { doc ->
                mapSearchUser(doc, currentUserId, blockedUserIds)
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

internal fun mapSearchUser(
    doc: DocumentSnapshot,
    currentUserId: String,
    blockedUserIds: Set<String>
): SearchUser? {
    val uid = doc.getString("uid") ?: doc.id
    if (uid.isBlank() || uid == currentUserId || uid in blockedUserIds) return null
    return SearchUser(
        uid = uid,
        username = doc.getString("username") ?: "usuario",
        displayName = doc.getString("displayName") ?: doc.getString("username") ?: "Usuario",
        avatarUrl = doc.getString("avatarUrl").orEmpty(),
        avatarBase64 = doc.getString("avatarBase64").orEmpty(),
        isFollowing = false
    )
}

package com.vivid.app.presentation.feed

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/** Global chronological page assembled from author-scoped queries, never from recommended posts.
 * Each source fetches one extra document. Only the global page boundary advances, so documents
 * fetched from a slower source but not shown are not skipped on the next request.
 */
internal suspend fun loadFollowingPage(
    viewer: String,
    after: DocumentSnapshot?,
    likedIds: Set<String>?,
    authors: Set<String>
): FeedPageResult {
    if (authors.isEmpty()) return FeedPageResult(emptyList(), null, false)
    val db = FirebaseFirestore.getInstance()
    val candidates = mutableListOf<DocumentSnapshot>()
    // Small groups stay within Firestore's rules document-access budget.
    for (group in followingAuthorGroups(authors)) {
        var query = db.collection("posts")
            .whereIn("userId", group)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(21)
        if (after != null) query = query.startAfter(after)
        candidates += query.get().await().documents
    }
    val ordered = candidates.distinctBy { it.id }.sortedWith(
        compareByDescending<DocumentSnapshot> { it.getLong("timestamp") ?: 0L }.thenByDescending { it.id }
    )
    val page = ordered.take(20)
    val posts = page.mapNotNull { mapPostDoc(it, viewer, likedIds, authors) }
    return FeedPageResult(posts, page.lastOrNull(), ordered.size > 20)
}

internal fun followingAuthorGroups(authors: Set<String>): List<List<String>> =
    authors.filter { it.isNotBlank() }.sorted().chunked(10)

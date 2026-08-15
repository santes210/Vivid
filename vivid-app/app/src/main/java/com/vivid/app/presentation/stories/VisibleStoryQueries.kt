package com.vivid.app.presentation.stories

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Consulta únicamente documentos que las reglas pueden autorizar: stories
 * públicas y stories privadas propias/de seguidores aceptados.
 */
suspend fun loadVisibleStoryDocuments(
    firestore: FirebaseFirestore,
    currentUserId: String,
    now: Long = System.currentTimeMillis(),
    limit: Long = 50
): List<DocumentSnapshot> = coroutineScope {
    if (currentUserId.isBlank()) return@coroutineScope emptyList()

    val followingIds = firestore.collection("users").document(currentUserId)
        .collection("following").get().await().documents.map { it.id }
    val privateAuthorChunks = (followingIds + currentUserId).distinct().chunked(30)

    val publicRequest = async {
        firestore.collection("stories")
            .whereEqualTo("isPrivate", false)
            .whereGreaterThan("expiresAt", now)
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .limit(limit)
            .get().await().documents
    }
    val privateRequests = privateAuthorChunks.map { authors ->
        async {
            firestore.collection("stories")
                .whereIn("userId", authors)
                .whereEqualTo("isPrivate", true)
                .whereGreaterThan("expiresAt", now)
                .orderBy("expiresAt", Query.Direction.ASCENDING)
                .limit(limit)
                .get().await().documents
        }
    }

    (publicRequest.await() + privateRequests.awaitAll().flatten())
        .distinctBy { it.id }
        .sortedBy { it.getLong("expiresAt") ?: Long.MAX_VALUE }
}

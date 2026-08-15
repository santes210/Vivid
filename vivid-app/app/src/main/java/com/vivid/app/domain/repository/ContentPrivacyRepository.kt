package com.vivid.app.domain.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

const val CONTENT_PRIVACY_VERSION = 1L

/**
 * Sincroniza la privacidad del perfil y la copia usada por las consultas.
 *
 * Al volver privada una cuenta, los lotes adicionales se ocultan primero y el
 * perfil cambia atómicamente junto al último lote. Al volverla pública, cambia
 * primero el perfil junto al primer lote y después se publica el resto. Así no
 * existe una ventana donde contenido privado quede expuesto.
 */
suspend fun setAccountContentPrivacy(
    firestore: FirebaseFirestore,
    userId: String,
    isPrivate: Boolean
) {
    if (userId.isBlank()) return

    val collections = listOf("posts", "reels", "stories")
    val documents = coroutineScope {
        collections.map { collection ->
            async {
                firestore.collection(collection)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .documents
            }
        }.awaitAll().flatten()
    }

    val profileRef = firestore.collection("users").document(userId)
    val firstChunk = documents.take(449) // + perfil = máximo 450 operaciones
    val remainingChunks = documents.drop(449).chunked(450)

    suspend fun updateChunk(chunk: List<com.google.firebase.firestore.DocumentSnapshot>) {
        if (chunk.isEmpty()) return
        val batch = firestore.batch()
        chunk.forEach { batch.update(it.reference, "isPrivate", isPrivate) }
        batch.commit().await()
    }

    // Hacer privado es restrictivo y puede aplicarse antes del cambio de perfil.
    if (isPrivate) remainingChunks.forEach { updateChunk(it) }

    val transitionBatch = firestore.batch()
    transitionBatch.set(
        profileRef,
        mapOf(
            "isPrivate" to isPrivate,
            "contentPrivacyVersion" to CONTENT_PRIVACY_VERSION,
            "updatedAt" to System.currentTimeMillis()
        ),
        SetOptions.merge()
    )
    firstChunk.forEach { transitionBatch.update(it.reference, "isPrivate", isPrivate) }
    transitionBatch.commit().await()

    // Hacer público solo puede aplicarse después de que el perfil ya sea público.
    if (!isPrivate) remainingChunks.forEach { updateChunk(it) }
}

/** Migra una sola vez el contenido creado por versiones anteriores de Vivid. */
suspend fun ensureCurrentUserContentPrivacy(
    firestore: FirebaseFirestore,
    userId: String
) {
    if (userId.isBlank()) return
    val user = firestore.collection("users").document(userId).get().await()
    if ((user.getLong("contentPrivacyVersion") ?: 0L) >= CONTENT_PRIVACY_VERSION) return
    setAccountContentPrivacy(
        firestore = firestore,
        userId = userId,
        isPrivate = user.getBoolean("isPrivate") ?: false
    )
}

package com.vivid.app.domain.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.vivid.app.domain.model.PostVisibility
import com.vivid.app.util.Hashtags
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

const val CONTENT_PRIVACY_VERSION = 2L

/**
 * Actualizaciones de privacidad por documento.
 *
 * v2: además de `isPrivate`, rellena los campos nuevos que las consultas
 * asumen —`visibility` ("public") y, en posts sin el campo, los `hashtags`
 * recalculados del caption— para que el contenido creado ANTES de estos
 * features no quede invisible en Explorar. Solo se escriben campos AUSENTES:
 * un post "friends" ya guardado nunca se resetea a público.
 */
internal fun contentPrivacyUpdates(collection: String, doc: DocumentSnapshot, isPrivate: Boolean): Map<String, Any> {
    val updates = LinkedHashMap<String, Any>()
    updates["isPrivate"] = isPrivate
    if (collection == "posts") {
        if (!doc.contains("visibility")) updates["visibility"] = PostVisibility.PUBLIC.value
        if (!doc.contains("hashtags")) {
            val caption = doc.getString("caption").orEmpty()
            val tags = Hashtags.extract(caption)
            if (tags.isNotEmpty()) updates["hashtags"] = tags
        }
    }
    return updates
}

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
    // (colección, documento): el backfill v2 solo aplica a posts.
    val documents = coroutineScope {
        collections.map { collection ->
            async {
                firestore.collection(collection)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                    .documents.map { collection to it }
            }
        }.awaitAll().flatten()
    }

    val profileRef = firestore.collection("users").document(userId)
    val firstChunk = documents.take(449) // + perfil = máximo 450 operaciones
    val remainingChunks = documents.drop(449).chunked(450)

    suspend fun updateChunk(chunk: List<Pair<String, DocumentSnapshot>>) {
        if (chunk.isEmpty()) return
        val batch = firestore.batch()
        chunk.forEach { (collection, doc) ->
            batch.update(doc.reference, contentPrivacyUpdates(collection, doc, isPrivate))
        }
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
    firstChunk.forEach { (collection, doc) ->
        transitionBatch.update(doc.reference, contentPrivacyUpdates(collection, doc, isPrivate))
    }
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

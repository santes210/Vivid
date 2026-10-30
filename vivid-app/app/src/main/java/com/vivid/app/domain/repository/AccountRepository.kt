package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.vivid.app.util.AccountPurge
import com.vivid.app.data.storage.StorageProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Resultado de [AccountRepository.deleteAccount].
 */
sealed interface DeleteAccountResult {
    /** Cuenta y datos eliminados por completo (Firestore + Storage + Auth). */
    data object Success : DeleteAccountResult

    /**
     * Firebase exige una reautenticación reciente antes de borrar el usuario
     * de Auth. Los datos remotos ya están purgados; basta repetir [AccountRepository.deleteAccount]
     * después del re-login (la purga es idempotente).
     */
    data class ReauthRequired(
        val useGoogle: Boolean,
        val usePassword: Boolean,
        val email: String
    ) : DeleteAccountResult

    /** Fallo recuperable: se puede reintentar. */
    data class Error(val message: String) : DeleteAccountResult
}

/**
 * Eliminación completa de cuenta: borra TODO dato del usuario en Firestore y
 * Storage (contenido, actividad, grafo de seguidores, mensajes de chat y el
 * propio perfil) y finalmente el usuario de Firebase Auth.
 *
 * Diseño de la purga:
 *  - Cada fase **consulta y luego borra**, por lo que es idempotente: si se
 *    reintenta tras un fallo parcial o una reautenticación, no duplica
 *    decrementos de contadores.
 *  - Los decrementos (likesCount, commentsCount, followersCount…) viajan en el
 *    mismo WriteBatch que el borrado: atómicos y de a 1, tal como exigen las
 *    reglas (`stepsByOne`).
 *  - Si un contador no se puede mover (p. ej. contenido privado ajeno), se
 *    borra igualmente el documento propio y se omite el contador: los datos
 *    del usuario no quedan residuales.
 *  - En los chats no se borra el documento del chat (las reglas lo prohíben):
 *    se borran MIS mensajes y se sanea el documento para que el otro
 *    participante vea "Usuario eliminado" (ver [AccountPurge.scrubChatFields]).
 *
 * Nota sobre residuos ajenos: los "me gusta" de OTROS usuarios sobre mi
 * contenido y sus copias en `savedPosts`/`viewers` ajenos pertenecen a esos
 * usuarios (las reglas solo dejan borrarlos a su autor) y quedan como
 * referencias huérfanas, igual que cuando cualquier usuario borra un post.
 */
class AccountRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider
) {

    /**
     * Borra todos los datos de [userId] y su cuenta de Auth.
     * Llama a [onProgress] al entrar en cada [AccountPurge.Stage].
     */
    suspend fun deleteAccount(
        userId: String,
        onProgress: (AccountPurge.Stage) -> Unit = {}
    ): DeleteAccountResult {
        return try {
            onProgress(AccountPurge.Stage.CONTENT)
            purgeOwnedContent(userId)

            onProgress(AccountPurge.Stage.ACTIVITY)
            purgeOwnActivity(userId)

            onProgress(AccountPurge.Stage.GRAPH)
            purgeFollowGraph(userId)

            onProgress(AccountPurge.Stage.CHATS)
            purgeChats(userId)

            onProgress(AccountPurge.Stage.PROFILE)
            purgeUserDoc(userId)

            onProgress(AccountPurge.Stage.AUTH)
            val current = auth.currentUser
            if (current != null && current.uid == userId) {
                current.delete().await()
            }

            onProgress(AccountPurge.Stage.DONE)
            DeleteAccountResult.Success
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            val providers = auth.currentUser?.providerData
                .orEmpty()
                .mapNotNull { it?.providerId }
                .filter { it != "firebase" }
            DeleteAccountResult.ReauthRequired(
                useGoogle = providers.contains("google.com"),
                usePassword = providers.contains("password") || providers.contains("email"),
                email = auth.currentUser?.email.orEmpty()
            )
        } catch (e: Exception) {
            DeleteAccountResult.Error(e.localizedMessage ?: "Error desconocido")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Contenido propio: posts, reels e historias (+ sus subcolecciones)
    // ─────────────────────────────────────────────────────────────

    private suspend fun purgeOwnedContent(userId: String) {
        purgeOwnedDocs("posts", userId, listOf("storageKey", "musicStorageKey"))
        purgeOwnedDocs("reels", userId, listOf("storageKey", "musicStorageKey"))
        purgeOwnedDocs("stories", userId, listOf("storageKey"))
    }

    private suspend fun purgeOwnedDocs(
        collection: String,
        userId: String,
        mediaKeys: List<String>
    ) {
        val docs = queryAll(firestore.collection(collection).whereEqualTo("userId", userId))
        for (doc in docs) {
            // Archivos en B2/Supabase (imágenes, vídeos y música embebida)
            for (key in mediaKeys) {
                doc.getString(key)?.takeIf { it.isNotBlank() }?.let { fileKey ->
                    runCatching { storage.deleteFile(fileKey) }
                }
            }

            // Subcolecciones: likes propios, árbol de comentarios completo y
            // viewers (el autor del contenido puede borrarlos según las reglas).
            deleteMatchingDocs(doc.reference.collection("likes").whereEqualTo("userId", userId))

            val comments = queryAll(doc.reference.collection("comments"))
            for (comment in comments) {
                deleteMatchingDocs(comment.reference.collection("likes").whereEqualTo("userId", userId))
            }
            deleteDocsChunked(comments.map { it.reference })

            deleteSubcollectionDocs(doc.reference.collection("viewers"))

            doc.reference.delete().await()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Actividad propia dispersa: mis "me gusta" y mis comentarios
    // ─────────────────────────────────────────────────────────────

    private suspend fun purgeOwnActivity(userId: String) {
        // 1) Mis "me gusta" en todo Vivid (posts, reels y comentarios).
        //    collectionGroup("likes") está permitido para lectura con `signedIn()`.
        val likes = queryAll(firestore.collectionGroup("likes").whereEqualTo("userId", userId))
        for (like in likes) {
            val counter = AccountPurge.likeCounterTarget(like.reference.path)
            deleteWithCounter(like.reference, counter)
        }

        // 2) Mis comentarios y respuestas en todo Vivid. La query necesita la
        //    regla de collectionGroup("comments") de solo-los-míos (ver
        //    firestore.rules: "localizar mis comentarios dispersos").
        val comments = queryAll(firestore.collectionGroup("comments").whereEqualTo("userId", userId))
        for (comment in comments) {
            deleteMatchingDocs(comment.reference.collection("likes").whereEqualTo("userId", userId))
            val counter = AccountPurge.commentCounterTarget(comment.reference.path)
            deleteWithCounter(comment.reference, counter)
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Grafo de seguidores: mis subcolecciones y los docs espejo ajenos
    // ─────────────────────────────────────────────────────────────

    private suspend fun purgeFollowGraph(userId: String) {
        data class Mirror(val mySub: String, val theirSub: String, val counterField: String?)

        val mirrors = listOf(
            Mirror("following", "followers", "followersCount"),
            Mirror("followers", "following", "followingCount"),
            Mirror("followRequests", "sentFollowRequests", null),
            Mirror("sentFollowRequests", "followRequests", null)
        )

        for (mirror in mirrors) {
            val mine = queryAll(
                firestore.collection("users").document(userId).collection(mirror.mySub)
            )
            for (doc in mine) {
                val otherId = doc.id // el docId es el uid del otro usuario
                runCatching {
                    val batch = firestore.batch()
                    batch.delete(
                        firestore.collection("users").document(otherId)
                            .collection(mirror.theirSub).document(userId)
                    )
                    if (mirror.counterField != null) {
                        // Mismo patrón que FollowRepository: merge con uid/updatedAt
                        // para cumplir onlyChanges(['followersCount','followingCount','updatedAt','uid']).
                        batch.set(
                            firestore.collection("users").document(otherId),
                            mapOf(
                                "uid" to otherId,
                                "updatedAt" to System.currentTimeMillis(),
                                mirror.counterField to FieldValue.increment(-1)
                            ),
                            SetOptions.merge()
                        )
                    }
                    batch.commit().await()
                }.onFailure {
                    // El doc del otro ya no existe (borró su cuenta): basta el espejo.
                    runCatching {
                        firestore.collection("users").document(otherId)
                            .collection(mirror.theirSub).document(userId).delete().await()
                    }
                }
            }
            deleteDocsChunked(mine.map { it.reference })
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Chats: mis mensajes (y su media) + saneo de mi identidad
    // ─────────────────────────────────────────────────────────────

    private suspend fun purgeChats(userId: String) {
        val chats = queryAll(firestore.collection("chats").whereArrayContains("participants", userId))
        for (chat in chats) {
            // Mis mensajes: solo el emisor puede borrarlos (reglas).
            val mine = queryAll(
                chat.reference.collection("messages").whereEqualTo("senderId", userId)
            )
            for (message in mine) {
                for (key in listOf("imageKey", "voiceKey")) {
                    message.getString(key)?.takeIf { it.isNotBlank() }?.let { fileKey ->
                        runCatching { storage.deleteFile(fileKey) }
                    }
                }
            }
            deleteDocsChunked(mine.map { it.reference })

            // El doc del chat no es borrable (reglas): retiro mi identidad y mi
            // estado, dejando el hilo visible para el otro participante.
            runCatching {
                val scrubbed = AccountPurge.scrubChatFields(chat.data.orEmpty(), userId)
                if (scrubbed.isNotEmpty()) chat.reference.update(scrubbed).await()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Perfil: subcolecciones propias y el documento de usuario
    // ─────────────────────────────────────────────────────────────

    private suspend fun purgeUserDoc(userId: String) {
        val userRef = firestore.collection("users").document(userId)
        deleteSubcollectionDocs(userRef.collection("savedPosts"))
        deleteSubcollectionDocs(userRef.collection("fcmTokens"))
        userRef.delete().await()
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers de borrado
    // ─────────────────────────────────────────────────────────────

    /** Pagina la query (300 en 300) hasta agotarla. */
    private suspend fun queryAll(query: Query): List<DocumentSnapshot> {
        val out = mutableListOf<DocumentSnapshot>()
        var last: DocumentSnapshot? = null
        while (true) {
            val base: Query = if (last == null) query else query.startAfter(last)
            val page = base.limit(PAGE_SIZE).get().await()
            if (page.isEmpty) break
            out += page.documents
            last = page.documents.last()
            if (page.documents.size < PAGE_SIZE) break
        }
        return out
    }

    /** Borra todos los docs de una subcolección, en lotes de [PAGE_SIZE]. */
    private suspend fun deleteSubcollectionDocs(ref: CollectionReference) {
        deleteDocsChunked(queryAll(ref).map { it.reference })
    }

    /** Borra los docs que coincidan con la query (ya filtrada), en lotes. */
    private suspend fun deleteMatchingDocs(query: Query) {
        deleteDocsChunked(queryAll(query).map { it.reference })
    }

    private suspend fun deleteDocsChunked(refs: List<DocumentReference>) {
        for (chunk in refs.chunked(PAGE_SIZE)) {
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    /**
     * Borra [ref] y decrementa el contador del padre en el MISMO batch (atómico,
     * de a 1, como exigen las reglas). Si el padre no admite el contador
     * (p. ej. contenido privado ajeno), borra igualmente el documento propio.
     */
    private suspend fun deleteWithCounter(ref: DocumentReference, counter: AccountPurge.CounterTarget?) {
        if (counter == null) {
            ref.delete().await()
            return
        }
        runCatching {
            val batch = firestore.batch()
            batch.delete(ref)
            batch.update(
                firestore.document(counter.documentPath),
                counter.field,
                FieldValue.increment(-1)
            )
            batch.commit().await()
        }.onFailure {
            // El padre no admite el contador (p. ej. contenido privado ajeno):
            // se borra igualmente el documento propio y se propaga el fallo
            // solo si ni siquiera eso es posible.
            ref.delete().await()
        }
    }

    private companion object {
        const val PAGE_SIZE = 300L
    }
}

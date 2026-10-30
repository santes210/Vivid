package com.vivid.app.util

/**
 * Lógica pura del borrado de cuenta (sin dependencias de Firebase ni Android),
 * para poder testearla sin emulador ni Robolectric.
 *
 * El flujo completo vive en [com.vivid.app.domain.repository.AccountRepository]:
 * esta clase solo clasifica rutas de Firestore y sanea documentos.
 */
object AccountPurge {

    /** Etapas del borrado, para mostrar progreso en la UI. */
    enum class Stage { CONTENT, ACTIVITY, GRAPH, CHATS, PROFILE, AUTH, DONE }

    /** Contador a decrementar (-1) en [documentPath], campo [field]. */
    data class CounterTarget(val documentPath: String, val field: String)

    /**
     * Dado el path de un doc `like` de `collectionGroup("likes")`, devuelve el
     * contador del documento padre que debe restar 1, o null si no aplica:
     *
     * - `posts/{postId}/likes/{uid}` → `posts/{postId}.likesCount`
     * - `reels/{reelId}/likes/{uid}` → `reels/{reelId}.likes`
     * - `{posts|reels}/{id}/comments/{cid}/likes/{uid}` → `.../comments/{cid}.likesCount`
     */
    fun likeCounterTarget(likePath: String): CounterTarget? {
        val p = likePath.trim('/').split('/')
        return when {
            p.size == 4 && p[0] == "posts" && p[2] == "likes" ->
                CounterTarget("posts/${p[1]}", "likesCount")
            p.size == 4 && p[0] == "reels" && p[2] == "likes" ->
                CounterTarget("reels/${p[1]}", "likes")
            p.size == 6 && p[2] == "comments" && p[4] == "likes" ->
                CounterTarget("${p[0]}/${p[1]}/comments/${p[3]}", "likesCount")
            else -> null
        }
    }

    /**
     * Contador del padre de un comentario propio:
     *
     * - `posts/{postId}/comments/{cid}` → `posts/{postId}.commentsCount`
     * - `reels/{reelId}/comments/{cid}` → `reels/{reelId}.comments`
     */
    fun commentCounterTarget(commentPath: String): CounterTarget? {
        val p = commentPath.trim('/').split('/')
        return when {
            p.size == 4 && p[0] == "posts" && p[2] == "comments" ->
                CounterTarget("posts/${p[1]}", "commentsCount")
            p.size == 4 && p[0] == "reels" && p[2] == "comments" ->
                CounterTarget("reels/${p[1]}", "comments")
            else -> null
        }
    }

    /**
     * Sanea el documento de un chat cuando [userId] elimina su cuenta:
     *
     * - su identidad (nombre/avatares) se sustituye por [deletedLabel];
     * - su estado de lectura y "escribiendo…" desaparece;
     * - si el último mensaje era suyo, la vista previa se limpia.
     *
     * Nunca toca `participants` (las reglas de Firestore lo prohíben): el chat
     * conserva sus dos participantes para que el otro usuario no pierda el hilo.
     */
    fun scrubChatFields(
        fields: Map<String, Any?>,
        userId: String,
        deletedLabel: String = "Usuario eliminado"
    ): Map<String, Any?> {
        val out = fields.toMutableMap()

        fun replaceKey(fieldName: String, replacement: Any?) {
            @Suppress("UNCHECKED_CAST")
            val map = (out[fieldName] as? Map<String, Any?>) ?: return
            if (!map.containsKey(userId)) return
            out[fieldName] = map.filterKeys { it != userId } + mapOf<String, Any?>(userId to replacement)
        }

        fun dropKey(fieldName: String) {
            @Suppress("UNCHECKED_CAST")
            val map = (out[fieldName] as? Map<String, Any?>) ?: return
            out[fieldName] = map.filterKeys { it != userId }
        }

        replaceKey("participantNames", deletedLabel)
        replaceKey("participantAvatars", "")
        replaceKey("participantAvatarBase64s", "")
        dropKey("unreadCounts")
        dropKey("typingStatus")
        dropKey("typingLastUpdate")

        if (out["lastMessageSenderId"] == userId) {
            out["lastMessage"] = ""
            out["lastMessageSenderId"] = ""
            out["lastMessageType"] = ""
        }
        return out
    }
}

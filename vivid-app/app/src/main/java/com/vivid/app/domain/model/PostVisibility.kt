package com.vivid.app.domain.model

/**
 * Audiencia de una publicación.
 *
 * - [PUBLIC]: todos (feed global, Explorar, perfil).
 * - [FRIENDS]: "Solo amigos" — únicamente el autor y las personas que lo
 *   siguen (`users/{autor}/followers/{uid}`). No aparece nunca en Explorar.
 *
 * Se persiste en Firestore como `visibility` ("public" | "friends"). Los
 * posts creados antes de este campo (y los migrados por
 * `ensureCurrentUserContentPrivacy` v2) se tratan como [PUBLIC].
 *
 * Nota de diseño: el valor vive en el documento tal cual (no booleano) para
 * que mañana puedan sumarse audiencias ("close_friends", "mutuals") sin
 * migrar datos.
 */
enum class PostVisibility(val value: String) {
    PUBLIC("public"),
    FRIENDS("friends");

    companion object {
        fun fromValue(raw: String?): PostVisibility =
            entries.firstOrNull { it.value == raw } ?: PUBLIC
    }
}

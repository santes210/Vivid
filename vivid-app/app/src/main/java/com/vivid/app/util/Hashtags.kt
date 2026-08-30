package com.vivid.app.util

/**
 * Utilidades de hashtags de Vivid.
 *
 * Una sola definición del formato (# + letras/números/guion bajo, incluyendo
 * acentos) para que EXTRAER en el caption (crear/editar), DESCUBRIR en
 * Explorar y CACHEAR en Room nunca se desincronicen.
 *
 * Antes había dos regex separadas (`#(\w+)` en CreatePostViewModel y nada en
 * la edición), `\w` no aceptaba acentes ("#día" quedaba en "d") y editar un
 * caption no recalculaba los hashtags del post.
 */
object Hashtags {

    /** Máximo de hashtags que se guardan por publicación. */
    const val MAX_PER_POST = 12

    /** Longitud máxima de un hashtag normalizado. */
    const val MAX_LENGTH = 30

    /** Hashtag = "#" + letras (con acentos), números o guion bajo. */
    val REGEX: Regex = Regex("#[\\p{L}\\p{N}_]{1,30}")

    /** Extrae los hashtags de un caption, en minúsculas, sin "#" y sin duplicar. */
    fun extract(caption: String): List<String> =
        REGEX.findAll(caption)
            .map { match -> match.value.removePrefix("#").lowercase().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_PER_POST)
            .toList()

    /** Normaliza un tag suelto (chips, deep links): sin "#", minúsculas. */
    fun normalize(raw: String): String =
        raw.trim().lowercase().removePrefix("#").take(MAX_LENGTH)

    /**
     * Serialización para Room: los hashtags de un post se guardan como un
     * string con COMAS DE AMBOS LADOS (",arte,musica,"), lo que permite
     * buscar por tag exacto con `LIKE '%,tag,%'` sin falsos positivos
     * (",arte," no matchea ",smart,").
     */
    fun joinForCache(tags: List<String>): String =
        if (tags.isEmpty()) ""
        else tags.joinToString(",", prefix = ",", postfix = ",") { it.trim().lowercase() }

    /** Inverso de [joinForCache]. */
    fun splitFromCache(joined: String): List<String> =
        joined.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

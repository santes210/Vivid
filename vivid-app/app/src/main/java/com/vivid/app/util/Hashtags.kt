package com.vivid.app.util

import java.text.Normalizer

/**
 * Hashtags de Vivid: extracción, normalización y rangos para pintar.
 *
 * Una sola definición para que EXTRAER en el caption (crear/editar),
 * DESCUBRIR en Explorar y CACHEAR en Room nunca se desincronicen.
 *
 * Contrato con Explorar:
 *  - Se guardan en Firestore como `posts.hashtags: List<String>` ya
 *    normalizados (minúsculas, sin tildes, sin `#`).
 *  - La query es `whereArrayContains("hashtags", tag)`, así `#Música`
 *    y el chip `musica` tienen que acabar en la misma clave.
 *  - El texto visible del caption se deja como lo escribió el usuario;
 *    [spans] apunta a esos rangos para pintar el enlace.
 */
object Hashtags {
    const val MAX_LENGTH = 30
    const val MAX_PER_POST = 12
    const val MAX_PER_CAPTION = MAX_PER_POST

    private val IN_TEXT = Regex("#([\\p{L}\\p{N}_]+)")

    /** Hashtag = "#" + letras (con acentos), números o guion bajo. */
    val REGEX: Regex = Regex("#[\\p{L}\\p{N}_]{1,30}")

    /**
     * Normaliza un tag o un fragmento (`#Música`, `  Arte `) a la clave
     * que se guarda y se busca: minúsculas, sin diacríticos, recortado.
     * Vacío si no queda ninguna letra.
     */
    fun normalize(raw: String): String {
        val stripped = raw.trim().removePrefix("#")
        if (stripped.isEmpty()) return ""
        val folded = foldDiacritics(stripped.lowercase())
        val cleaned = buildString(folded.length) {
            folded.forEach { ch ->
                if (ch.isLetterOrDigit() || ch == '_') append(ch)
            }
        }.take(MAX_LENGTH)
        if (cleaned.isEmpty() || cleaned.none { it.isLetter() }) return ""
        return cleaned
    }

    fun isValid(raw: String): Boolean = normalize(raw).isNotEmpty()

    /** Tags únicos, en orden de aparición, recortados a [MAX_PER_POST]. */
    fun extract(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        IN_TEXT.findAll(text).forEach { match ->
            val tag = normalize(match.groupValues[1])
            if (tag.isNotEmpty()) seen += tag
            if (seen.size >= MAX_PER_POST) return seen.toList()
        }
        return seen.toList()
    }

    data class Span(
        val tag: String,
        val start: Int,
        val endExclusive: Int
    )

    /**
     * Rangos de `#tag` dentro de [text] (el caption ya filtrado, no el
     * original). [Span.tag] va normalizado para navegar a Explorar.
     */
    fun spans(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        return IN_TEXT.findAll(text).mapNotNull { match ->
            val tag = normalize(match.groupValues[1])
            if (tag.isEmpty()) null
            else Span(tag, match.range.first, match.range.last + 1)
        }.toList()
    }

    /**
     * Si [raw] es una búsqueda de tag (`#arte`, `# Música`), devuelve la
     * clave. Si no lleva `#`, no se asume tag: `ana` puede ser un username.
     */
    fun parseQuery(raw: String): String? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("#")) return null
        return normalize(trimmed).takeIf { it.isNotEmpty() }
    }

    fun display(tag: String): String {
        val n = normalize(tag)
        return if (n.isEmpty()) "" else "#$n"
    }

    /** Añade ` #tag` al caption si aún no está. */
    fun appendToCaption(caption: String, tag: String): String {
        val n = normalize(tag)
        if (n.isEmpty()) return caption
        if (n in extract(caption)) return caption
        val prefix = caption.trimEnd()
        return if (prefix.isEmpty()) "#$n" else "$prefix #$n"
    }

    /**
     * Serialización para Room: los hashtags de un post se guardan como un
     * string con COMAS DE AMBOS LADOS (",arte,musica,"), lo que permite
     * buscar por tag exacto con `LIKE '%,tag,%'` sin falsos positivos
     * (",arte," no matchea ",smart,").
     */
    fun joinForCache(tags: List<String>): String {
        val cleaned = tags.map { normalize(it) }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return ""
        return cleaned.joinToString(",", prefix = ",", postfix = ",")
    }

    /** Inverso de [joinForCache]. */
    fun splitFromCache(joined: String): List<String> =
        joined.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun foldDiacritics(s: String): String {
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        return nfd.replace("\\p{Mn}+".toRegex(), "")
    }
}

package com.vivid.app.presentation.search

import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.util.Hashtags
import com.vivid.app.util.TextFold

/** Pestaña/segmento del buscador inteligente: qué secciones se muestran. */
enum class SearchTab { ALL, PEOPLE, TAGS, POSTS }

/** Hashtag con cuántos posts públicos recientes lo usan (0 = curado/legacy). */
data class SearchTagResult(val tag: String, val count: Int)

/**
 * Núcleo puro del buscador inteligente: intención, coincidencia y ranking.
 *
 * Sin Firestore, sin Compose, sin Android — se testea en JVM. Las reglas:
 *
 *  1. **Intención**: `#arte` busca tags, `@ana` busca personas (la arroba se
 *     ignora) y el texto libre busca en las tres secciones a la vez.
 *  2. **Tolerancia a tildes**: toda comparación pasa por [fold]
 *     ([TextFold.fold]), así `musica` encuentra `#música`.
 *  3. **Tolerancia a errores**: si una palabra del texto está a distancia de
 *     edición ≤ 1 (≤ 2 en palabras largas) de la aguja, cuenta como fuzzy.
 *  4. **Ranking**: exacto < prefijo < inicio de palabra < contiene < fuzzy.
 *     En personas el `@username` pesa más que el nombre; en publicaciones el
 *     hashtag pesa más que el caption.
 */
object SmartSearch {

    /** Topes de cada sección (el panel no pagina: muestra los mejores). */
    const val PEOPLE_LIMIT = 24
    const val TAG_LIMIT = 10
    const val POST_LIMIT = 12

    /** Previsualizaciones del segmento "Todo". */
    const val PEOPLE_PREVIEW = 3
    const val TAGS_PREVIEW = 6
    const val POSTS_PREVIEW = 6

    // Rangos de coincidencia (menor = mejor).
    const val RANK_EXACT = 0
    const val RANK_PREFIX = 1
    const val RANK_WORD = 2
    const val RANK_CONTAINS = 3
    const val RANK_FUZZY = 4

    /** Plegado minúsculas + sin tildes (ver [TextFold]). */
    fun fold(raw: String): String = TextFold.fold(raw)

    /** `@ana` → `ana`: la arroba es intención de persona, no parte del texto. */
    fun stripHandle(raw: String): String = raw.trim().removePrefix("@").trim()

    /** `#arte` → `arte`; sin `#` no se asume tag (ver [Hashtags.parseQuery]). */
    fun tagIntent(raw: String): String? = Hashtags.parseQuery(raw)

    /** Texto plano a buscar en captions/usernames: sin `#`, sin `@`, plegado. */
    fun plainText(raw: String): String =
        fold(stripHandle(raw.trim().removePrefix("#")))

    /** Hay suficiente texto para buscar (≥ 2 letras; un `#tag` con 1 basta). */
    fun isSearchable(raw: String): Boolean {
        val tag = tagIntent(raw)
        if (tag != null) return tag.isNotEmpty()
        return fold(stripHandle(raw)).length >= ExplorePaging.MIN_QUERY_LENGTH
    }

    /**
     * Rango de coincidencia de [needle] dentro de [haystack], ambos plegados
     * aquí dentro. `null` = no coincide. Ver constantes `RANK_*`.
     */
    fun matchRank(haystack: String, needle: String): Int? {
        val h = fold(haystack)
        val n = fold(needle)
        if (h.isEmpty() || n.isEmpty()) return null
        return when {
            h == n -> RANK_EXACT
            h.startsWith(n) -> RANK_PREFIX
            wordStart(h, n) -> RANK_WORD
            h.contains(n) -> RANK_CONTAINS
            fuzzyHit(h, n) -> RANK_FUZZY
            else -> null
        }
    }

    /** ¿La aguja empieza una palabra del texto? ("an" en "ana pérez"). */
    private fun wordStart(h: String, n: String): Boolean {
        var idx = h.indexOf(n)
        while (idx >= 0) {
            if (idx == 0) return true
            val prev = h[idx - 1]
            if (!prev.isLetterOrDigit() && prev != '_') return true
            idx = h.indexOf(n, idx + 1)
        }
        return false
    }

    /**
     * Tolerancia a errores tipográficos: alguna palabra del texto está a
     * distancia de edición ≤ 1 (≤ 2 si la aguja es larga). Solo para agujas
     * de 4+ letras; con menos, el ruido supera la señal.
     */
    private fun fuzzyHit(h: String, n: String): Boolean {
        if (n.length < 4 || n.any { it == ' ' }) return false
        val maxDist = if (n.length >= 8) 2 else 1
        return WORD_SPLIT.split(h)
            .any { word -> word.length >= 2 && boundedLevenshtein(word, n, maxDist) <= maxDist }
    }

    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}_]+")

    /** Levenshtein con corte temprano: devuelve `max + 1` si excede [max]. */
    fun boundedLevenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > max) return max + 1
            val swap = prev
            prev = curr
            curr = swap
        }
        return prev[b.length]
    }

    /**
     * Personas ordenadas por relevancia: el `@username` manda (+0), el nombre
     * acompaña (+1). Quedan fuera las que no coinciden con nada.
     */
    fun rankUsers(needle: String, users: List<SearchUser>): List<SearchUser> {
        val n = plainText(needle)
        if (n.isEmpty()) return users
        return users
            .mapNotNull { user ->
                val rank = listOfNotNull(
                    matchRank(user.username, n)?.plus(0),
                    matchRank(user.displayName, n)?.plus(1)
                ).minOrNull() ?: return@mapNotNull null
                RankedUser(user, rank)
            }
            .sortedWith(compareBy({ it.rank }, { it.user.username.length }, { it.user.username }))
            .map { it.user }
    }

    /**
     * Tags ordenados por relevancia y, dentro del mismo rango, por popularidad.
     * La coincidencia se evalúa contra la clave normalizada (`musica`).
     */
    fun rankTags(needle: String, tags: List<SearchTagResult>): List<SearchTagResult> {
        val n = Hashtags.normalize(needle)
        if (n.isEmpty()) return tags
        return tags
            .mapNotNull { result -> matchRank(result.tag, n)?.let { RankedTag(result, it) } }
            .sortedWith(compareBy({ it.rank }, { -it.result.count }, { it.result.tag }))
            .map { it.result }
    }

    /**
     * Rango de un post: el hashtag pesa más que el caption (el caption suma 2
     * para que un tag exacto siempre gane a un caption que "contiene").
     */
    fun postRank(needle: String, post: PostData): Int? {
        val text = plainText(needle)
        if (text.isEmpty()) return null
        val tagWord = Hashtags.normalize(needle)
        val ranks = mutableListOf<Int>()
        if (tagWord.isNotEmpty()) {
            post.hashtags.forEach { h -> matchRank(h, tagWord)?.let { ranks += it } }
        }
        matchRank(post.caption, text)?.let { ranks += it + 2 }
        return ranks.minOrNull()
    }

    /** Posts ordenados por relevancia y, empatados, por recencia. */
    fun rankPosts(needle: String, posts: List<PostData>): List<PostData> =
        posts.mapNotNull { post -> postRank(needle, post)?.let { RankedPost(post, it) } }
            .sortedWith(compareBy({ it.rank }, { -it.post.timestamp }))
            .map { it.post }

    private data class RankedUser(val user: SearchUser, val rank: Int)
    private data class RankedTag(val result: SearchTagResult, val rank: Int)
    private data class RankedPost(val post: PostData, val rank: Int)
}

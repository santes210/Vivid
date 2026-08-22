package com.vivid.app.presentation.search

import com.vivid.app.data.paging.ExplorePaging

/**
 * Historial y sugerencias de la SearchBar de Explorar.
 *
 * Pura: no toca SharedPreferences ni Compose, así se testea sin Android.
 * [SearchViewModel] solo persiste lo que sale de aquí.
 */
object SearchHistory {
    const val MAX_ITEMS = 8
    const val SUGGESTION_LIMIT = 6
    const val SEPARATOR = "\n"

    fun normalize(raw: String): String = ExplorePaging.normalizeQuery(raw)

    fun canRecord(raw: String): Boolean = ExplorePaging.isValidUserQuery(normalize(raw))

    /** Inserta [raw] al frente, sin duplicados, recortado a [MAX_ITEMS]. */
    fun record(existing: List<String>, raw: String): List<String> {
        val query = normalize(raw)
        if (!ExplorePaging.isValidUserQuery(query)) return existing
        return listOf(query) + existing.filter { it != query }.take(MAX_ITEMS - 1)
    }

    fun remove(existing: List<String>, raw: String): List<String> {
        val query = normalize(raw)
        return existing.filter { it != query }
    }

    fun encode(items: List<String>): String = items.joinToString(SEPARATOR)

    fun decode(raw: String?): List<String> =
        raw
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .take(MAX_ITEMS)

    /**
     * Sugerencias: recientes que coinciden + tags de Explorar.
     *
     * Con query vacía se devuelve el historial completo y todos los tags
     * (la SearchBar expandida es justo el sitio para verlos). Con texto,
     * se filtra y se recorta a [SUGGESTION_LIMIT].
     */
    fun suggestions(
        query: String,
        history: List<String>,
        tags: List<String> = ExplorePaging.TAGS,
        limit: Int = SUGGESTION_LIMIT
    ): List<SearchSuggestion> {
        val needle = normalize(query)
        val recents = history.map { SearchSuggestion.Recent(it) }
        val tagItems = tags.map { SearchSuggestion.Tag(it) }
        if (needle.isEmpty()) return recents + tagItems
        return (recents + tagItems)
            .filter { it.matches(needle) }
            .take(limit)
    }
}

sealed class SearchSuggestion {
    abstract fun matches(needle: String): Boolean

    data class Recent(val query: String) : SearchSuggestion() {
        override fun matches(needle: String): Boolean = query.contains(needle)
    }

    data class Tag(val tag: String) : SearchSuggestion() {
        override fun matches(needle: String): Boolean =
            tag.contains(needle) || "#$tag".contains(needle)
    }
}

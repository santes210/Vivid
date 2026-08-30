package com.vivid.app.presentation.search

import com.vivid.app.data.paging.ExplorePaging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `record ignores blank and single-letter queries`() {
        assertEquals(emptyList<String>(), SearchHistory.record(emptyList(), " "))
        assertEquals(emptyList<String>(), SearchHistory.record(emptyList(), "a"))
        assertFalse(SearchHistory.canRecord("x"))
    }

    @Test
    fun `record normalizes and dedupes to the front`() {
        val first = SearchHistory.record(emptyList(), "  Alice  ")
        assertEquals(listOf("alice"), first)
        val second = SearchHistory.record(first, "bob")
        assertEquals(listOf("bob", "alice"), second)
        val again = SearchHistory.record(second, "ALICE")
        assertEquals(listOf("alice", "bob"), again)
        assertTrue(SearchHistory.canRecord("ALICE"))
    }

    @Test
    fun `record caps at MAX_ITEMS`() {
        var history = emptyList<String>()
        repeat(SearchHistory.MAX_ITEMS + 3) { index ->
            history = SearchHistory.record(history, "user$index")
        }
        assertEquals(SearchHistory.MAX_ITEMS, history.size)
        assertEquals("user${SearchHistory.MAX_ITEMS + 2}", history.first())
        assertFalse("user0" in history)
    }

    @Test
    fun `remove drops the normalized query`() {
        val history = listOf("alice", "bob")
        assertEquals(listOf("bob"), SearchHistory.remove(history, "  ALICE "))
        assertEquals(history, SearchHistory.remove(history, "carol"))
        val withTag = listOf("#musica", "alice")
        assertEquals(listOf("alice"), SearchHistory.remove(withTag, "#Música"))
    }

    @Test
    fun `encode and decode survive the SharedPreferences round-trip`() {
        val items = listOf("alice", "bob", "carol")
        assertEquals(items, SearchHistory.decode(SearchHistory.encode(items)))
        assertEquals(emptyList<String>(), SearchHistory.decode(null))
        assertEquals(emptyList<String>(), SearchHistory.decode("  \n  "))
        assertEquals(listOf("alice"), SearchHistory.decode("alice\n\n"))
    }

    @Test
    fun `decode never returns more than MAX_ITEMS`() {
        val raw = (0 until 20).joinToString("\n") { "q$it" }
        assertEquals(SearchHistory.MAX_ITEMS, SearchHistory.decode(raw).size)
    }

    @Test
    fun `empty query lists all recents then every explore tag`() {
        val history = listOf("ana", "bruno")
        val suggestions = SearchHistory.suggestions("  ", history)
        assertEquals(
            listOf(
                SearchSuggestion.Recent("ana"),
                SearchSuggestion.Recent("bruno")
            ),
            suggestions.take(2)
        )
        val tags = suggestions.drop(2).map { (it as SearchSuggestion.Tag).tag }
        assertEquals(ExplorePaging.TAGS, tags)
    }

    @Test
    fun `typed query filters recents and tags`() {
        val history = listOf("artefacto", "bruno")
        val suggestions = SearchHistory.suggestions("art", history)
        assertTrue(suggestions.any { it is SearchSuggestion.Recent && it.query == "artefacto" })
        assertTrue(suggestions.any { it is SearchSuggestion.Tag && it.tag == "arte" })
        assertFalse(suggestions.any { it is SearchSuggestion.Recent && it.query == "bruno" })
        assertTrue(suggestions.size <= SearchHistory.SUGGESTION_LIMIT)
    }

    @Test
    fun `hash prefix still matches a tag`() {
        val suggestions = SearchHistory.suggestions("#viv", emptyList())
        assertTrue(suggestions.any { it is SearchSuggestion.Tag && it.tag == "vivid" })
    }

    @Test
    fun `record stores hashtag queries with a hash prefix`() {
        assertTrue(SearchHistory.canRecord("#Arte"))
        val history = SearchHistory.record(emptyList(), "  #Música  ")
        assertEquals(listOf("#musica"), history)
        val again = SearchHistory.record(history, "alice")
        assertEquals(listOf("alice", "#musica"), again)
    }

    @Test
    fun `typed custom hashtag appears as a tag suggestion`() {
        val suggestions = SearchHistory.suggestions("#atardecer", emptyList())
        assertTrue(suggestions.any { it is SearchSuggestion.Tag && it.tag == "atardecer" })
        assertTrue(suggestions.size <= SearchHistory.SUGGESTION_LIMIT)
    }
}

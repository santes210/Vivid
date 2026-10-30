package com.vivid.app.presentation.search

import com.vivid.app.presentation.feed.PostData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSearchTest {

    // ── Plegado y utilidades ────────────────────────────────────────────

    @Test
    fun `fold lowercases and strips diacritics`() {
        assertEquals("maria", SmartSearch.fold("María"))
        assertEquals("musica", SmartSearch.fold("MÚSICA"))
        assertEquals("nino", SmartSearch.fold("niño"))
        assertEquals("hola mundo", SmartSearch.fold("  Hola   mundo  "))
    }

    @Test
    fun `tagIntent only fires on hash queries`() {
        assertEquals("arte", SmartSearch.tagIntent("#arte"))
        assertEquals("musica", SmartSearch.tagIntent("#Música"))
        assertNull(SmartSearch.tagIntent("arte"))
        assertNull(SmartSearch.tagIntent("@ana"))
    }

    @Test
    fun `stripHandle removes the at sign`() {
        assertEquals("ana", SmartSearch.stripHandle("@ana"))
        assertEquals("ana", SmartSearch.stripHandle("ana"))
    }

    @Test
    fun `isSearchable requires two letters for text and one for tags`() {
        assertTrue(SmartSearch.isSearchable("ar"))
        assertFalse(SmartSearch.isSearchable("a"))
        assertFalse(SmartSearch.isSearchable("  "))
        assertTrue(SmartSearch.isSearchable("#a"))
        assertTrue(SmartSearch.isSearchable("@an"))
    }

    // ── Coincidencia y rangos ───────────────────────────────────────────

    @Test
    fun `matchRank orders exact prefix word contains and fuzzy`() {
        assertEquals(SmartSearch.RANK_EXACT, SmartSearch.matchRank("arte", "arte"))
        assertEquals(SmartSearch.RANK_PREFIX, SmartSearch.matchRank("artes", "arte"))
        assertEquals(SmartSearch.RANK_WORD, SmartSearch.matchRank("mundo arte", "arte"))
        assertEquals(SmartSearch.RANK_CONTAINS, SmartSearch.matchRank("cartel", "arte"))
        assertNull(SmartSearch.matchRank("musica", "arte"))
    }

    @Test
    fun `matchRank ignores accents on both sides`() {
        assertEquals(SmartSearch.RANK_EXACT, SmartSearch.matchRank("música", "musica"))
        assertEquals(SmartSearch.RANK_EXACT, SmartSearch.matchRank("musica", "música"))
        assertEquals(SmartSearch.RANK_PREFIX, SmartSearch.matchRank("música en vivo", "musi"))
    }

    @Test
    fun `matchRank tolerates small typos`() {
        assertEquals(SmartSearch.RANK_FUZZY, SmartSearch.matchRank("artes", "artee"))
        assertEquals(SmartSearch.RANK_FUZZY, SmartSearch.matchRank("viaje", "biaje"))
        // Sin fuzzy por debajo de 4 letras: el ruido gana.
        assertNull(SmartSearch.matchRank("ana", "ane"))
    }

    @Test
    fun `boundedLevenshtein cuts early above max`() {
        assertEquals(0, SmartSearch.boundedLevenshtein("hola", "hola", 2))
        assertEquals(1, SmartSearch.boundedLevenshtein("hola", "holaa", 2))
        assertEquals(2, SmartSearch.boundedLevenshtein("hola", "holaaa", 3))
        assertTrue(SmartSearch.boundedLevenshtein("hola", "adios", 2) > 2)
    }

    // ── Ranking de personas ────────────────────────────────────────────

    @Test
    fun `rankUsers prefers username over display name`() {
        val byUsername = user("u1", "mar", "Pedro")
        val byDisplay = user("u2", "pedro", "Marina")
        val ranked = SmartSearch.rankUsers("mar", listOf(byDisplay, byUsername))
        assertEquals(listOf("u1", "u2"), ranked.map { it.uid })
    }

    @Test
    fun `rankUsers drops non-matching users`() {
        val ranked = SmartSearch.rankUsers(
            "mar",
            listOf(user("u1", "maria", "María"), user("u2", "luis", "Luis"))
        )
        assertEquals(listOf("u1"), ranked.map { it.uid })
    }

    @Test
    fun `rankUsers matches folded display names`() {
        val ranked = SmartSearch.rankUsers("maria", listOf(user("u1", "mariag", "María González")))
        assertEquals(listOf("u1"), ranked.map { it.uid })
    }

    // ── Ranking de tags ────────────────────────────────────────────────

    @Test
    fun `rankTags sorts by match quality then popularity`() {
        val tags = listOf(
            SearchTagResult("artesania", 50),   // prefijo
            SearchTagResult("arte", 10),        // exacto
            SearchTagResult("cartel", 100)      // contiene
        )
        val ranked = SmartSearch.rankTags("arte", tags)
        assertEquals(listOf("arte", "artesania", "cartel"), ranked.map { it.tag })
    }

    @Test
    fun `rankTags finds tags without accents`() {
        val ranked = SmartSearch.rankTags("musica", listOf(SearchTagResult("musica", 3)))
        assertEquals(listOf("musica"), ranked.map { it.tag })
    }

    // ── Ranking de publicaciones ───────────────────────────────────────

    @Test
    fun `postRank prefers hashtag match over caption match`() {
        val byTag = post("p1", caption = "qué lindo día", hashtags = listOf("arte"))
        val byCaption = post("p2", caption = "arte moderno", hashtags = listOf("otra"))
        val ranked = SmartSearch.rankPosts("arte", listOf(byCaption, byTag))
        assertEquals(listOf("p1", "p2"), ranked.map { it.id })
    }

    @Test
    fun `postRank returns null when nothing matches`() {
        assertNull(SmartSearch.postRank("arte", post("p1", caption = "hola", hashtags = listOf("musica"))))
    }

    @Test
    fun `rankPosts ties break by recency`() {
        val older = post("p1", caption = "arte", timestamp = 100)
        val newer = post("p2", caption = "arte", timestamp = 200)
        val ranked = SmartSearch.rankPosts("arte", listOf(older, newer))
        assertEquals(listOf("p2", "p1"), ranked.map { it.id })
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun user(uid: String, username: String, displayName: String) = SearchUser(
        uid = uid, username = username, displayName = displayName
    )

    private fun post(
        id: String,
        caption: String,
        hashtags: List<String> = emptyList(),
        timestamp: Long = 0L
    ) = PostData(
        id = id, userId = "u", username = "user",
        userProfilePicture = "", caption = caption,
        timestamp = timestamp, hashtags = hashtags
    )
}

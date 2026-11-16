package com.vivid.app.presentation.feed

import org.junit.Assert.*
import org.junit.Test

class FeedContinuityTest {
    private fun post(id: String, timestamp: Long, caption: String = "") =
        PostData(id, "author", "author", "", caption = caption, timestamp = timestamp)

    @Test fun `live updates do not drop paginated rows`() {
        val result = mergeFeedPages(listOf(post("head", 20)), listOf(post("older", 10)))
        assertEquals(listOf("head", "older"), result.map { it.id })
    }

    @Test fun `overlapping pages prefer live data and keep stable ids`() {
        val result = mergeFeedPages(listOf(post("same", 20, "updated")),
            listOf(post("same", 20, "old"), post("older", 10)))
        assertEquals(2, result.size)
        assertEquals("updated", result.first().caption)
    }

    @Test fun `empty filtered page is not necessarily end of source`() {
        assertTrue(FeedPageResult(emptyList(), null, hasMore = true).hasMore)
        assertFalse(FeedPageResult(listOf(post("last", 1)), null, hasMore = false).hasMore)
    }

    @Test fun `new pages sort consistently regardless of arrival order`() {
        val result = mergeFeedPages(listOf(post("old", 1)), listOf(post("new", 3), post("middle", 2)))
        assertEquals(listOf("new", "middle", "old"), result.map { it.id })
    }
}

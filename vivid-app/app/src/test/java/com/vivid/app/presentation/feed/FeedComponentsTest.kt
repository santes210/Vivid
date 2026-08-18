package com.vivid.app.presentation.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for pure utility functions in the Feed components.
 */
class FeedComponentsTest {

    @Test
    fun `formatTimestamp returns empty for zero`() {
        assertEquals("", formatTimestamp(0L))
    }

    @Test
    fun `formatTimestamp returns empty for negative`() {
        assertEquals("", formatTimestamp(-1L))
    }

    @Test
    fun `formatTimestamp returns formatted date`() {
        // 2024-01-15 00:00:00 UTC = 1705276800000
        Locale.setDefault(Locale.US)
        val result = formatTimestamp(1705276800000L)
        // Should produce something like "15 Jan" or "Jan 15" depending on locale
        assert(result.isNotBlank()) { "Expected non-blank formatted date, got: '$result'" }
    }
}

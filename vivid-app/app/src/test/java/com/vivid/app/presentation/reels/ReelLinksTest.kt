package com.vivid.app.presentation.reels

import org.junit.Assert.*
import org.junit.Test

class ReelLinksTest {
    @Test fun `link contains only a document id and round trips`() {
        val link = ReelLinks.create("abc_123-xyz")
        assertEquals("vivid://reel/abc_123-xyz", link)
        assertEquals("abc_123-xyz", ReelLinks.parse(link))
        assertFalse(link.contains("token"))
    }

    @Test fun `chat recognizes the reference in a localized share message`() {
        assertEquals("123", ReelLinks.fromMessage("Mira este reel en Vivid:\nvivid://reel/123"))
        assertEquals("123", ReelLinks.fromMessage("Watch this reel in Vivid:\nvivid://reel/123"))
    }

    @Test fun `untrusted or malformed links are rejected`() {
        for (link in listOf("https://example.com/reel/123", "vivid://reel/", "vivid://reel/../users",
            "vivid://reel/123?token=secret", "vivid://reel/123/other", "vivid://post/123")) {
            assertNull(link, ReelLinks.parse(link))
        }
        assertNull(ReelLinks.parse("vivid://reel/" + "a".repeat(129)))
    }

    @Test fun `post links remain distinct from reel links`() {
        assertEquals("p1", ReelLinks.postFromMessage("Mira esta publicación:\nvivid://post/p1"))
        assertNull(ReelLinks.fromMessage("vivid://post/p1"))
        assertNull(ReelLinks.postFromMessage("vivid://reel/p1"))
        assertNull(ReelLinks.postFromMessage("vivid://post/../users"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot create a path traversal link`() { ReelLinks.create("../users") }
}

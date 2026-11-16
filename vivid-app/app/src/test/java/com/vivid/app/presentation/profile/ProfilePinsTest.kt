package com.vivid.app.presentation.profile

import org.junit.Assert.*
import org.junit.Test

class ProfilePinsTest {
    @Test fun `three total pins across posts and reels`() {
        val pins = listOf("posts/a", "posts/b", "reels/c")
        try { updatedProfilePins(pins, "reels/d"); fail("Expected limit") }
        catch (error: IllegalArgumentException) { assertEquals("pin_limit", error.message) }
    }

    @Test fun `unpinning at the limit frees a slot`() {
        val pins = updatedProfilePins(listOf("posts/a", "posts/b", "reels/c"), "posts/b")
        assertEquals(listOf("posts/a", "reels/c", "reels/d"), updatedProfilePins(pins, "reels/d"))
    }

    @Test fun `post and reel with identical ids are different pins`() {
        assertEquals(listOf("posts/a", "reels/a"), updatedProfilePins(listOf("posts/a"), "reels/a"))
    }

    @Test fun `pins lead the grid without changing timestamps or counts`() {
        val posts = listOf(ProfilePost("new", timestamp = 50), ProfilePost("old", timestamp = 10), ProfilePost("middle", timestamp = 30))
        val sorted = orderProfileContent(posts, listOf("posts/old"))
        assertEquals(listOf("old", "new", "middle"), sorted.map { it.id })
        assertEquals(10L, sorted.first().timestamp)
        assertEquals(posts.size, sorted.size)
    }

    @Test fun `deleted pins do not create phantom grid entries`() {
        val posts = listOf(ProfilePost("new", timestamp = 50))
        assertEquals(posts, orderProfileContent(posts, listOf("posts/deleted")))
    }
}

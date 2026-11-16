package com.vivid.app.presentation.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileContentCountTest {
    @Test fun `count reflects current documents including videos stored as posts`() {
        val posts = listOf(ProfilePost("image"), ProfilePost("video", isVideo = true))
        assertEquals(2, profileContentCount(posts, true, false, true))
        assertEquals(1, profileContentCount(posts.dropLast(1), true, false, true))
        assertEquals(0, profileContentCount(emptyList(), true, false, true))
    }

    @Test fun `unknown failed and private counts never pretend to be zero`() {
        assertNull(profileContentCount(emptyList(), false, false, true))
        assertNull(profileContentCount(listOf(ProfilePost("old")), true, true, true))
        assertNull(profileContentCount(listOf(ProfilePost("private")), true, false, false))
        assertNull(ProfileUiState().postsCount)
        assertNull(ProfileUiState().reelsCount)
    }

    @Test fun `identity uses collection and exact document id without reel prefixes`() {
        val post = ProfilePost("reel_original")
        val reel = ProfilePost("reel_original", collection = "reels", isVideo = true)
        assertEquals("reel_original", reel.id)
        assertEquals(2, profileContentCount(listOf(post, reel, post), true, false, true))
    }
}

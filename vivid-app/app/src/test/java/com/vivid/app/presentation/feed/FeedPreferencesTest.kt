package com.vivid.app.presentation.feed

import org.junit.Assert.*
import org.junit.Test

class FeedPreferencesTest {
    private fun post(id: String, author: String) = PostData(
        id = id, userId = author, username = author,
        userProfilePicture = "", caption = "", timestamp = 1L
    )

    @Test fun `not interested hides only the selected publication`() {
        val preferences = FeedPreferences(hiddenPosts = setOf("one"))
        assertFalse(preferences.allows(post("one", "author")))
        assertTrue(preferences.allows(post("two", "author")))
    }

    @Test fun `mute hides all posts by that author but not other accounts`() {
        val preferences = FeedPreferences(mutedAuthors = setOf("muted"))
        assertFalse(preferences.allows(post("one", "muted")))
        assertFalse(preferences.allows(post("two", "muted")))
        assertTrue(preferences.allows(post("three", "other")))
    }

    @Test fun `reset preferences allow previously hidden content`() {
        assertTrue(FeedPreferences().allows(post("one", "muted")))
    }
}

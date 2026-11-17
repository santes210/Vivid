package com.vivid.app.presentation.feed

import org.junit.Assert.*
import org.junit.Test

class FollowingFeedPageTest {
    @Test fun `empty following never produces an unscoped query`() {
        assertTrue(followingAuthorGroups(emptySet()).isEmpty())
        assertTrue(followingAuthorGroups(setOf("", " ")).isEmpty())
    }

    @Test fun `author groups are stable bounded and include everyone once`() {
        val authors = (1..35).map { "author-$it" }.toSet()
        val groups = followingAuthorGroups(authors)
        assertTrue(groups.all { it.size in 1..10 })
        assertEquals(authors.sorted(), groups.flatten())
        assertEquals(groups, followingAuthorGroups(authors.reversed().toSet()))
    }
}

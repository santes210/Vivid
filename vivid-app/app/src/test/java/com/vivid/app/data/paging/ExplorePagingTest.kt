package com.vivid.app.data.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePagingTest {

    @Test
    fun `normalizeQuery trims and lowercases`() {
        assertEquals("alice", ExplorePaging.normalizeQuery("  Alice  "))
        assertEquals("vivid", ExplorePaging.normalizeQuery("VIVID"))
    }

    @Test
    fun `isValidUserQuery requires two characters`() {
        assertFalse(ExplorePaging.isValidUserQuery(""))
        assertFalse(ExplorePaging.isValidUserQuery("a"))
        assertTrue(ExplorePaging.isValidUserQuery("al"))
        assertTrue(ExplorePaging.isValidUserQuery("alice"))
    }

    @Test
    fun `usernamePrefixEnd appends firestore range sentinel`() {
        assertEquals("al\uf8ff", ExplorePaging.usernamePrefixEnd("al"))
    }

    @Test
    fun `page sizes are small enough for first paint`() {
        assertTrue(ExplorePaging.POST_PAGE_SIZE in 6..24)
        assertTrue(ExplorePaging.USER_PAGE_SIZE in 10..30)
        assertTrue(ExplorePaging.PREFETCH_DISTANCE < ExplorePaging.POST_PAGE_SIZE)
    }

    @Test
    fun `default tags include vivid`() {
        assertTrue("vivid" in ExplorePaging.TAGS)
        assertEquals("vivid", ExplorePaging.TAGS.first())
    }
}

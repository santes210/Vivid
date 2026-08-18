package com.vivid.app.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ChatRepository] helper methods that do not require
 * a running Firestore backend.
 */
class ChatRepositoryTest {

    // ── buildChatId ──

    @Test
    fun `buildChatId returns sorted concatenation`() {
        val id = ChatRepository.buildChatId("userB", "userA")
        assertEquals("userA_userB", id)
    }

    @Test
    fun `buildChatId is commutative`() {
        val id1 = ChatRepository.buildChatId("alice", "bob")
        val id2 = ChatRepository.buildChatId("bob", "alice")
        assertEquals(id1, id2)
    }

    @Test
    fun `buildChatId with same user returns doubled id`() {
        val id = ChatRepository.buildChatId("sam", "sam")
        assertEquals("sam_sam", id)
    }

    @Test
    fun `buildChatId preserves case in sorting`() {
        // Uppercase letters sort before lowercase in natural String order.
        val id = ChatRepository.buildChatId("Zach", "adam")
        assertEquals("Zach_adam", id)
    }

    @Test
    fun `buildChatId handles empty strings`() {
        val id = ChatRepository.buildChatId("", "userA")
        assertEquals("_userA", id)
    }
}

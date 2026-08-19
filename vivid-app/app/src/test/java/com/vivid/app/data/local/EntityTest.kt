package com.vivid.app.data.local

import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.data.local.entity.PostEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Room entity data classes, verifying default values,
 * equality, and copy behaviour.
 */
class EntityTest {

    // ── PostEntity ──

    @Test
    fun `PostEntity equality based on data`() {
        val a = PostEntity(
            id = "p1", userId = "u1", username = "test",
            userProfilePicture = "", caption = "cap", timestamp = 100L
        )
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun `PostEntity copy with updated likesCount`() {
        val post = PostEntity(
            id = "p1", userId = "u1", username = "test",
            userProfilePicture = "", caption = "", timestamp = 100L,
            likesCount = 5
        )
        val updated = post.copy(likesCount = 6, isLiked = true)
        assertEquals(6, updated.likesCount)
        assertTrue(updated.isLiked)
        assertEquals(post.id, updated.id)
    }

    // ── MessageEntity ──

    @Test
    fun `MessageEntity type defaults to text`() {
        val msg = MessageEntity(
            id = "m1", chatId = "c1", senderId = "s1",
            text = "hello", timestamp = 100L
        )
        assertEquals("text", msg.type)
        assertEquals(0L, msg.lastEditedAt)
    }

    @Test
    fun `MessageEntity with voice type`() {
        val msg = MessageEntity(
            id = "m2", chatId = "c1", senderId = "s1",
            text = "", timestamp = 200L, type = "voice",
            voiceUrl = "https://voice.com/1.ogg",
            voiceKey = "vk1", voiceDurationMs = 5000L
        )
        assertEquals("voice", msg.type)
        assertEquals(5000L, msg.voiceDurationMs)
    }

    // ── ChatEntity ──

    @Test
    fun `ChatEntity unreadCount defaults to zero`() {
        val chat = ChatEntity(
            chatId = "c1", otherUserId = "u2",
            otherUserName = "bob", otherUserAvatar = "",
            lastMessage = "", lastMessageTimestamp = 100L
        )
        assertEquals(0, chat.unreadCount)
    }

    @Test
    fun `ChatEntity with non-zero unread`() {
        val chat = ChatEntity(
            chatId = "c1", otherUserId = "u2",
            otherUserName = "bob", otherUserAvatar = "",
            lastMessage = "hi", lastMessageTimestamp = 200L,
            unreadCount = 3
        )
        assertEquals(3, chat.unreadCount)
    }
}

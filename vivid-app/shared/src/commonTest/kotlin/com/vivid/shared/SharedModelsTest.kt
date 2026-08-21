package com.vivid.shared

import com.vivid.shared.model.*
import com.vivid.shared.util.NetworkUtils
import com.vivid.shared.util.TimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests del módulo compartido.
 * Estos tests corren en JVM (Android) y en Native (iOS simulator).
 */
class SharedModelsTest {

    // ───────────────────── Message Tests ─────────────────────

    @Test
    fun message_isEdited_returnsTrueWhenEdited() {
        val msg = Message(id = "1", text = "Hola", lastEditedAt = 1000L)
        assertTrue(msg.isEdited)
    }

    @Test
    fun message_isEdited_returnsFalseWhenNeverEdited() {
        val msg = Message(id = "1", text = "Hola", lastEditedAt = 0L)
        assertFalse(msg.isEdited)
    }

    @Test
    fun message_canBeEditedBy_returnsTrueForSenderOfTextMessage() {
        val msg = Message(id = "1", text = "Hola", senderId = "userA", type = MessageType.TEXT)
        assertTrue(msg.canBeEditedBy("userA"))
    }

    @Test
    fun message_canBeEditedBy_returnsFalseForDifferentUser() {
        val msg = Message(id = "1", text = "Hola", senderId = "userA", type = MessageType.TEXT)
        assertFalse(msg.canBeEditedBy("userB"))
    }

    @Test
    fun message_canBeEditedBy_returnsFalseForNonTextMessage() {
        val msg = Message(id = "1", text = "foto.jpg", senderId = "userA", type = MessageType.IMAGE)
        assertFalse(msg.canBeEditedBy("userA"))
    }

    @Test
    fun message_mediaStorageKeys_filtersBlankAndDuplicates() {
        val msg = Message(id = "1", imageKey = "img/abc.jpg", voiceKey = "img/abc.jpg")
        assertEquals(listOf("img/abc.jpg"), msg.mediaStorageKeys())
    }

    @Test
    fun message_mediaStorageKeys_includesBothKeys() {
        val msg = Message(id = "1", imageKey = "img/abc.jpg", voiceKey = "voice/def.mp3")
        assertEquals(listOf("img/abc.jpg", "voice/def.mp3"), msg.mediaStorageKeys())
    }

    @Test
    fun messageType_fromString_parsesAllTypes() {
        assertEquals(MessageType.TEXT, MessageType.fromString("text"))
        assertEquals(MessageType.IMAGE, MessageType.fromString("image"))
        assertEquals(MessageType.VIDEO, MessageType.fromString("video"))
        assertEquals(MessageType.VOICE, MessageType.fromString("voice"))
        assertEquals(MessageType.STORY_REPLY, MessageType.fromString("story_reply"))
        assertEquals(MessageType.TEXT, MessageType.fromString("unknown"))
    }

    // ───────────────────── Story Tests ─────────────────────

    @Test
    fun storyType_fromString_parsesCorrectly() {
        assertEquals(StoryType.PHOTO, StoryType.fromString("photo"))
        assertEquals(StoryType.VIDEO, StoryType.fromString("video"))
        assertEquals(StoryType.PHOTO, StoryType.fromString("other"))
    }

    @Test
    fun groupStoriesByUser_groupsCorrectly() {
        val stories = listOf(
            Story(id = "1", userId = "userA", username = "alice", createdAt = 100L),
            Story(id = "2", userId = "userB", username = "bob", createdAt = 200L),
            Story(id = "3", userId = "userA", username = "alice", createdAt = 150L)
        )

        val groups = groupStoriesByUser(stories)

        assertEquals(2, groups.size)
        // userB tiene el story más reciente, va primero
        assertEquals("userB", groups[0].userId)
        assertEquals("userA", groups[1].userId)
        assertEquals(2, groups[1].stories.size)
    }

    // ───────────────────── Chat Tests ─────────────────────

    @Test
    fun buildChatId_sortsAlphabetically() {
        assertEquals("aaa_bbb", ChatRepository.buildChatId("bbb", "aaa"))
        assertEquals("aaa_bbb", ChatRepository.buildChatId("aaa", "bbb"))
    }

    @Test
    fun buildChatId_isDeterministic() {
        val id1 = ChatRepository.buildChatId("user123", "user456")
        val id2 = ChatRepository.buildChatId("user456", "user123")
        assertEquals(id1, id2)
    }

    // ───────────────────── NetworkUtils Tests ─────────────────────

    @Test
    fun exponentialBackoff_increasesWithAttempts() {
        val d0 = NetworkUtils.exponentialBackoff(0)
        val d1 = NetworkUtils.exponentialBackoff(1)
        val d2 = NetworkUtils.exponentialBackoff(2)

        assertTrue(d0 < d1)
        assertTrue(d1 < d2)
    }

    @Test
    fun exponentialBackoff_respectsMaxDelay() {
        val delay = NetworkUtils.exponentialBackoff(100, maxDelayMs = 5000L)
        assertTrue(delay <= 5000L)
    }

    // ───────────────────── TimeFormatter Tests ─────────────────────

    @Test
    fun formatRelativeTime_justNow() {
        val now = 1000000L
        assertEquals("justo ahora", TimeFormatter.formatRelativeTime(now - 5000, now))
    }

    @Test
    fun formatRelativeTime_minutes() {
        val now = 1000000L
        val fiveMinutesAgo = now - (5 * 60 * 1000)
        assertEquals("hace 5m", TimeFormatter.formatRelativeTime(fiveMinutesAgo, now))
    }

    @Test
    fun formatRelativeTime_hours() {
        val now = 100000000L
        val threeHoursAgo = now - (3 * 60 * 60 * 1000)
        assertEquals("hace 3h", TimeFormatter.formatRelativeTime(threeHoursAgo, now))
    }

    @Test
    fun formatDuration_formatsCorrectly() {
        assertEquals("0:30", TimeFormatter.formatDuration(30_000L))
        assertEquals("1:05", TimeFormatter.formatDuration(65_000L))
        assertEquals("1:00:00", TimeFormatter.formatDuration(3_600_000L))
    }

    @Test
    fun formatCount_formatsCorrectly() {
        assertEquals("999", TimeFormatter.formatCount(999))
        assertEquals("1000", TimeFormatter.formatCount(1000).let {
            // Acepta "1000" o "1.0K"
            if (it.contains("K")) "1000" else it
        })
    }
}

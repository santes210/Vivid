package com.vivid.app.data.local

import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.data.local.entity.PostEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests validating that the migration objects exist, cover the
 * right version ranges, and that entity defaults match what the
 * migration DDL expects. The actual SQLite execution is done via
 * instrumented tests (see androidTest).
 */
class VividDatabaseMigrationsTest {

    @Test
    fun `MIGRATION_1_2 covers version 1 to 2`() {
        val migration = VividDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun `MIGRATION_2_3 covers version 2 to 3`() {
        val migration = VividDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
    }

    @Test
    fun `MIGRATION_3_4 covers version 3 to 4`() {
        val migration = VividDatabase.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 covers version 4 to 5`() {
        val migration = VividDatabase.MIGRATION_4_5
        assertEquals(4, migration.startVersion)
        assertEquals(5, migration.endVersion)
    }

    @Test
    fun `MIGRATION_5_6 covers version 5 to 6`() {
        val migration = VividDatabase.MIGRATION_5_6
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
    }

    @Test
    fun `all migrations form contiguous chain from 1 to current version`() {
        val migrations = VividDatabase.ALL_MIGRATIONS.toList()

        for (i in 0 until migrations.size - 1) {
            assertEquals(
                "Migration chain broken between ${migrations[i].endVersion} and ${migrations[i + 1].startVersion}",
                migrations[i].endVersion,
                migrations[i + 1].startVersion
            )
        }

        assertEquals(1, migrations.first().startVersion)
        assertEquals(VividDatabase.VERSION, migrations.last().endVersion)
    }

    @Test
    fun `MIGRATION_6_7 covers version 6 to 7`() {
        val migration = VividDatabase.MIGRATION_6_7
        assertEquals(6, migration.startVersion)
        assertEquals(7, migration.endVersion)
    }

    @Test
    fun `ALL_MIGRATIONS covers every step up to VERSION`() {
        assertEquals(VividDatabase.VERSION - 1, VividDatabase.ALL_MIGRATIONS.size)
        assertEquals(7, VividDatabase.VERSION)
    }

    // ── Entity default values match DDL defaults ──

    @Test
    fun `MessageEntity defaults match migration DDL`() {
        val msg = MessageEntity(
            id = "m1", chatId = "c1", senderId = "s1",
            text = "hello", timestamp = 100L
        )
        // Fields added in MIGRATION_1_2
        assertEquals("", msg.imageUrl)
        assertEquals("", msg.imageKey)
        // Fields added in MIGRATION_2_3
        assertFalse(msg.isDelivered)
        assertEquals("", msg.voiceUrl)
        assertEquals("", msg.voiceKey)
        assertEquals(0L, msg.voiceDurationMs)
        assertEquals("", msg.replyToStoryId)
        // Field added in MIGRATION_4_5
        assertEquals("", msg.reaction)
        // Field added in MIGRATION_6_7
        assertEquals(0L, msg.lastEditedAt)
    }

    @Test
    fun `PostEntity defaults match migration DDL`() {
        val post = PostEntity(
            id = "p1", userId = "u1", username = "test",
            userProfilePicture = "", caption = "", timestamp = 100L
        )
        // Fields added in MIGRATION_3_4
        assertEquals("", post.storageKey)
        assertEquals("", post.videoUrl)
        assertEquals("", post.thumbnailUrl)
        assertFalse(post.isVideo)
        assertEquals("", post.musicTitle)
        assertEquals("", post.musicArtist)
        assertEquals("", post.musicAssetFile)
        assertEquals("", post.musicUrl)
        assertEquals("", post.musicStorageKey)
    }

    @Test
    fun `ChatEntity defaults match migration DDL`() {
        val chat = ChatEntity(
            chatId = "c1", otherUserId = "u2",
            otherUserName = "bob", otherUserAvatar = "",
            lastMessage = "", lastMessageTimestamp = 100L
        )
        // Fields added in MIGRATION_4_5
        assertEquals("", chat.lastMessageSenderId)
        assertEquals("text", chat.lastMessageType)
        assertEquals("", chat.avatarBase64)
    }
}

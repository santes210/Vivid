package com.vivid.app.presentation.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {

    @Test
    fun `isEdited is false when lastEditedAt is zero`() {
        val msg = Message(id = "m1", text = "hola", senderId = "a")

        assertFalse(msg.isEdited)
        assertEquals(0L, msg.lastEditedAt)
    }

    @Test
    fun `isEdited is true after lastEditedAt is set`() {
        val msg = Message(
            id = "m1",
            text = "hola",
            senderId = "a",
            lastEditedAt = 10L
        )

        assertTrue(msg.isEdited)
    }

    @Test
    fun `receipts default to not delivered and not read`() {
        val msg = Message(id = "m1", text = "hola", senderId = "a")

        assertFalse(msg.isDelivered)
        assertFalse(msg.isRead)
    }

    @Test
    fun `canBeEditedBy only allows own text messages`() {
        val text = Message(
            id = "m1",
            text = "hola",
            senderId = "alice",
            type = "text"
        )

        assertTrue(text.canBeEditedBy("alice"))
        assertFalse(text.canBeEditedBy("bob"))
        assertFalse(text.canBeEditedBy(""))

        val image = text.copy(type = "image")
        assertFalse(image.canBeEditedBy("alice"))

        val blank = text.copy(text = "   ")
        assertFalse(blank.copy(text = "").canBeEditedBy("alice"))
    }

    @Test
    fun `delivered is independent from read`() {
        val delivered = Message(
            id = "m1",
            text = "hola",
            senderId = "a",
            isDelivered = true,
            isRead = false
        )

        assertTrue(delivered.isDelivered)
        assertFalse(delivered.isRead)

        val read = delivered.copy(isRead = true)

        assertTrue(read.isDelivered)
        assertTrue(read.isRead)
    }

    @Test
    fun `media storage keys are independent from the message type`() {
        val video = Message(
            id = "m1",
            senderId = "alice",
            type = "video",
            imageKey = "chat_images/alice_bob/video.mp4",
            voiceKey = "chat_voice/alice_bob/audio.m4a"
        )

        assertEquals(
            listOf(
                "chat_images/alice_bob/video.mp4",
                "chat_voice/alice_bob/audio.m4a"
            ),
            video.mediaStorageKeys()
        )
    }

    @Test
    fun `media storage keys omit blanks and duplicates`() {
        val message = Message(
            imageKey = "key",
            voiceKey = "key"
        )

        assertEquals(
            listOf("key"),
            message.mediaStorageKeys()
        )
    }
}

package com.vivid.app.presentation.messages

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class ChatDraftStoreTest {
    private val values = mutableMapOf<String, String?>()
    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val store: ChatDraftStore

    init {
        every { prefs.getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg(); editor
        }
        every { editor.remove(any()) } answers { values.remove(firstArg<String>()); editor }
        store = ChatDraftStore(prefs)
    }

    @Test fun `retry and recreation retain the same operation id`() {
        val id = store.save("alice", "alice_bob", "hola")
        val restored = ChatDraftStore(prefs)
        assertEquals("hola", restored.text("alice", "alice_bob"))
        assertEquals(id, restored.save("alice", "alice_bob", "hola"))
    }

    @Test fun `acknowledgement does not erase a newer draft`() {
        val old = store.save("alice", "chat", "primero")
        val newer = store.save("alice", "chat", "segundo")
        assertNotEquals(old, newer)
        store.clearIfAcknowledged("alice", "chat", old)
        assertEquals("segundo", store.text("alice", "chat"))
        store.clearIfAcknowledged("alice", "chat", newer)
        assertEquals("", store.text("alice", "chat"))
    }

    @Test fun `drafts are isolated by account and conversation`() {
        store.save("alice", "chat", "privado")
        assertEquals("", store.text("bob", "chat"))
        assertEquals("", store.text("alice", "another"))
        assertEquals("", store.text("", "chat"))
    }
}

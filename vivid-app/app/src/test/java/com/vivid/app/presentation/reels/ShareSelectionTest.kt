package com.vivid.app.presentation.reels

import org.junit.Assert.*
import org.junit.Test

class ShareSelectionTest {
    @Test fun `search ignores accents case and outer whitespace`() {
        assertTrue(ShareSelection.matches("José María", " JOSE "))
        assertTrue(ShareSelection.matches("José María", "maria"))
        assertTrue(ShareSelection.matches("Ana", ""))
        assertFalse(ShareSelection.matches("Ana", "Pepe"))
    }

    @Test fun `optional note preserves the reference and displays separately`() {
        val base = "Mira esta publicación en Vivid:\nvivid://reel/r1"
        val payload = ShareSelection.payload(base, "  ¡Mira esto!  ")
        assertEquals("r1", ReelLinks.fromMessage(payload))
        assertEquals("¡Mira esto!", ShareSelection.noteFromMessage(payload))
        assertEquals(base, ShareSelection.payload(base, "  "))
        assertNull(ShareSelection.noteFromMessage(base))
    }

    @Test fun `notes have a bounded length and support multiple paragraphs`() {
        val base = "Post:\nvivid://post/p1"
        assertEquals(500, ShareSelection.payload(base, "a".repeat(600)).substringBefore("\n\n").length)
        val note = "Primero\n\nSegundo"
        assertEquals(note, ShareSelection.noteFromMessage(ShareSelection.payload(base, note)))
        assertNull(ShareSelection.noteFromMessage("Plain\n\ntext"))
    }

    @Test fun `retry selects only failed chosen recipients`() {
        val statuses = mapOf("a" to ReelShareStatus.SENT, "b" to ReelShareStatus.FAILED,
            "c" to ReelShareStatus.SENDING, "d" to ReelShareStatus.FAILED)
        assertEquals(setOf("b"), ShareSelection.retryable(setOf("a", "b", "c", "e"), statuses))
    }
}

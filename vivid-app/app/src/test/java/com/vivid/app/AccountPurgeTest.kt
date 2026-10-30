package com.vivid.app

import com.vivid.app.util.AccountPurge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la lógica pura del borrado de cuenta: clasificación de rutas de
 * Firestore y saneo de los documentos de chat. Sin Android ni Firebase.
 */
class AccountPurgeTest {

    // ── likeCounterTarget ──

    @Test
    fun `like en post decrementa likesCount del post`() {
        val target = AccountPurge.likeCounterTarget("posts/abc/likes/uid1")
        assertEquals(AccountPurge.CounterTarget("posts/abc", "likesCount"), target)
    }

    @Test
    fun `like en reel decrementa likes del reel`() {
        val target = AccountPurge.likeCounterTarget("reels/r1/likes/uid1")
        assertEquals(AccountPurge.CounterTarget("reels/r1", "likes"), target)
    }

    @Test
    fun `like en comentario de post decrementa likesCount del comentario`() {
        val target = AccountPurge.likeCounterTarget("posts/abc/comments/c9/likes/uid1")
        assertEquals(AccountPurge.CounterTarget("posts/abc/comments/c9", "likesCount"), target)
    }

    @Test
    fun `like en comentario de reel decrementa likesCount del comentario`() {
        val target = AccountPurge.likeCounterTarget("reels/r1/comments/c9/likes/uid1")
        assertEquals(AccountPurge.CounterTarget("reels/r1/comments/c9", "likesCount"), target)
    }

    @Test
    fun `ruta de like desconocida no tiene contador`() {
        assertNull(AccountPurge.likeCounterTarget("cosas/raras/likes/uid1"))
        assertNull(AccountPurge.likeCounterTarget("posts/abc/likes"))
        assertNull(AccountPurge.likeCounterTarget(""))
    }

    // ── commentCounterTarget ──

    @Test
    fun `comentario de post decrementa commentsCount del post`() {
        val target = AccountPurge.commentCounterTarget("posts/abc/comments/c9")
        assertEquals(AccountPurge.CounterTarget("posts/abc", "commentsCount"), target)
    }

    @Test
    fun `comentario de reel decrementa comments del reel`() {
        val target = AccountPurge.commentCounterTarget("reels/r1/comments/c9")
        assertEquals(AccountPurge.CounterTarget("reels/r1", "comments"), target)
    }

    @Test
    fun `ruta de comentario desconocida no tiene contador`() {
        assertNull(AccountPurge.commentCounterTarget("posts/abc/comments"))
        assertNull(AccountPurge.commentCounterTarget("usuarios/abc/comments/c9"))
        assertNull(AccountPurge.commentCounterTarget("posts/abc/comments/c9/likes/x"))
    }

    // ── scrubChatFields ──

    private fun chatFields(): Map<String, Any?> = mapOf(
        "participants" to listOf("uid1", "uid2"),
        "participantNames" to mapOf("uid1" to "Ana", "uid2" to "Beto"),
        "participantAvatars" to mapOf("uid1" to "http://a", "uid2" to "http://b"),
        "participantAvatarBase64s" to mapOf("uid1" to "aaa", "uid2" to "bbb"),
        "unreadCounts" to mapOf("uid1" to 2, "uid2" to 0),
        "typingStatus" to mapOf("uid1" to true),
        "lastMessage" to "Hola",
        "lastMessageSenderId" to "uid1",
        "lastMessageType" to "text",
        "createdAt" to 123L,
        "updatedAt" to 456L
    )

    @Test
    fun `scrub sustituye la identidad del usuario por Usuario eliminado`() {
        val out = AccountPurge.scrubChatFields(chatFields(), "uid1")
        @Suppress("UNCHECKED_CAST")
        val names = out["participantNames"] as Map<String, Any?>
        assertEquals("Usuario eliminado", names["uid1"])
        assertEquals("Beto", names["uid2"])
        @Suppress("UNCHECKED_CAST")
        val avatars = out["participantAvatars"] as Map<String, Any?>
        assertEquals("", avatars["uid1"])
        assertEquals("http://b", avatars["uid2"])
    }

    @Test
    fun `scrub borra el estado de lectura y escritura del usuario`() {
        val out = AccountPurge.scrubChatFields(chatFields(), "uid1")
        @Suppress("UNCHECKED_CAST")
        val unread = out["unreadCounts"] as Map<String, Any?>
        assertFalse(unread.containsKey("uid1"))
        assertTrue(unread.containsKey("uid2"))
        @Suppress("UNCHECKED_CAST")
        val typing = out["typingStatus"] as Map<String, Any?>
        assertFalse(typing.containsKey("uid1"))
    }

    @Test
    fun `scrub limpia la vista previa si el ultimo mensaje era del usuario`() {
        val out = AccountPurge.scrubChatFields(chatFields(), "uid1")
        assertEquals("", out["lastMessage"])
        assertEquals("", out["lastMessageSenderId"])
        assertEquals("", out["lastMessageType"])
    }

    @Test
    fun `scrub conserva la vista previa si el ultimo mensaje es ajeno`() {
        val fields = chatFields() + mapOf("lastMessageSenderId" to "uid2", "lastMessage" to "Adiós")
        val out = AccountPurge.scrubChatFields(fields, "uid1")
        assertEquals("Adiós", out["lastMessage"])
        assertEquals("uid2", out["lastMessageSenderId"])
    }

    @Test
    fun `scrub nunca toca participants`() {
        val out = AccountPurge.scrubChatFields(chatFields(), "uid1")
        assertEquals(listOf("uid1", "uid2"), out["participants"])
    }

    @Test
    fun `scrub no cambia datos de otros usuarios ni campos sueltos`() {
        val out = AccountPurge.scrubChatFields(chatFields(), "uid1")
        assertEquals(123L, out["createdAt"])
        assertEquals(456L, out["updatedAt"])
    }
}

package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del parser de hashtags: lo que extrae CreatePostViewModel al
 * publicar, lo que recalcula editPostCaption y lo que cachea Room DEBE ser
 * exactamente lo mismo. Las tildes se pliegan (`#Música` → `musica`) para
 * que Explorar encuentre el post con `whereArrayContains`.
 */
class HashtagsTest {

    @Test
    fun `extrae tags en minusculas y sin duplicados`() {
        assertEquals(
            listOf("vivid", "arte"),
            Hashtags.extract("Hola #Vivid! mirando #arte y de nuevo #vivid")
        )
    }

    @Test
    fun `soporta acentos plegados y numeros`() {
        assertEquals(listOf("dia", "verano2026"), Hashtags.extract("playa #día del #Verano2026"))
    }

    @Test
    fun `ignora hash solitario y etiqueta corta`() {
        assertEquals(emptyList<String>(), Hashtags.extract("esto # no es un tag"))
        assertEquals(listOf("abc"), Hashtags.extract("# #abc"))
    }

    @Test
    fun `no atraviesa puntuacion`() {
        assertEquals(
            listOf("vivid", "otro"),
            Hashtags.extract("#vivid, luego #otro.tag")
        )
    }

    @Test
    fun `limita la cantidad de tags por post`() {
        val caption = (1..20).joinToString(" ") { "#tag$it" }
        assertEquals(Hashtags.MAX_PER_POST, Hashtags.extract(caption).size)
    }

    @Test
    fun `normalize lowercases strips hash and diacritics`() {
        assertEquals("musica", Hashtags.normalize("#Música"))
        assertEquals("musica", Hashtags.normalize("  MÚSICA  "))
        assertEquals("nino", Hashtags.normalize("#niño"))
        assertEquals("arte", Hashtags.normalize("Arte"))
        assertEquals("vivid", Hashtags.normalize("  #Vivid "))
        assertEquals("", Hashtags.normalize("#"))
        assertEquals("", Hashtags.normalize("   "))
        assertEquals("", Hashtags.normalize("#123"))
        assertEquals("", Hashtags.normalize("#___"))
    }

    @Test
    fun `normalize truncates to MAX_LENGTH`() {
        val long = "a".repeat(Hashtags.MAX_LENGTH + 8)
        assertEquals("a".repeat(Hashtags.MAX_LENGTH), Hashtags.normalize(long))
    }

    @Test
    fun `extract finds unique tags in order`() {
        val caption = "Atardecer en la costa #Viaje #viaje #Música y #arte."
        assertEquals(listOf("viaje", "musica", "arte"), Hashtags.extract(caption))
    }

    @Test
    fun `extract ignores tags without letters and caps the list`() {
        assertEquals(emptyList<String>(), Hashtags.extract("sin tags ni nada"))
        assertEquals(emptyList<String>(), Hashtags.extract("#42 #___"))
        val many = (1..20).joinToString(" ") { "#tag$it" }
        assertEquals(Hashtags.MAX_PER_CAPTION, Hashtags.extract(many).size)
        assertEquals("tag1", Hashtags.extract(many).first())
    }

    @Test
    fun `spans point at the original hash ranges`() {
        val text = "Hola #Arte y #música"
        val spans = Hashtags.spans(text)
        assertEquals(2, spans.size)
        assertEquals("arte", spans[0].tag)
        assertEquals("#Arte", text.substring(spans[0].start, spans[0].endExclusive))
        assertEquals("musica", spans[1].tag)
        assertEquals("#música", text.substring(spans[1].start, spans[1].endExclusive))
    }

    @Test
    fun `parseQuery only accepts hash-prefixed input`() {
        assertEquals("arte", Hashtags.parseQuery("  #Arte  "))
        assertEquals("musica", Hashtags.parseQuery("#Música"))
        assertNull(Hashtags.parseQuery("arte"))
        assertNull(Hashtags.parseQuery("ana"))
        assertNull(Hashtags.parseQuery("#"))
        assertNull(Hashtags.parseQuery(""))
    }

    @Test
    fun `display prefixes a normalized tag`() {
        assertEquals("#musica", Hashtags.display("Música"))
        assertEquals("", Hashtags.display("#"))
    }

    @Test
    fun `appendToCaption skips duplicates and respects spacing`() {
        assertEquals("#arte", Hashtags.appendToCaption("", "Arte"))
        assertEquals("hola #arte", Hashtags.appendToCaption("hola", "arte"))
        assertEquals("hola #arte", Hashtags.appendToCaption("hola #arte", "ARTE"))
        assertEquals("hola #arte #musica", Hashtags.appendToCaption("hola #arte", "música"))
    }

    @Test
    fun `isValid rejects empty and numeric-only`() {
        assertTrue(Hashtags.isValid("vivid"))
        assertFalse(Hashtags.isValid(""))
        assertFalse(Hashtags.isValid("#42"))
    }

    @Test
    fun `join y split de cache son inversas`() {
        val tags = listOf("vivid", "arte", "musica")
        assertEquals(tags, Hashtags.splitFromCache(Hashtags.joinForCache(tags)))
    }

    @Test
    fun `join vacio para lista vacia y split tolera basura`() {
        assertEquals("", Hashtags.joinForCache(emptyList()))
        assertEquals(
            listOf("a", "b"),
            Hashtags.splitFromCache(",,a, ,b,,")
        )
    }

    @Test
    fun `joinForCache folds diacritics`() {
        assertEquals(",musica,", Hashtags.joinForCache(listOf("#Música")))
    }
}

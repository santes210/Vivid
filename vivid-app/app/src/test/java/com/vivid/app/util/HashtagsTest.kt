package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del parser de hashtags: lo que extrae CreatePostViewModel al
 * publicar, lo que recalcula editPostCaption y lo que cachea Room DEBE ser
 * exactamente lo mismo.
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
    fun `soporta acentos y numeros`() {
        assertEquals(listOf("día", "verano2026"), Hashtags.extract("playa #día del #Verano2026"))
    }

    @Test
    fun `ignora hash solitario y etiqueta corta`() {
        assertEquals(emptyList<String>(), Hashtags.extract("esto # no es un tag"))
        assertEquals(listOf("abc"), Hashtags.extract("# #abc"))
    }

    @Test
    fun `no atraviesa puntuacion`() {
        // La coma corta "#vivid"; el punto corta "#otro" (".tag" no es hashtag).
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
    fun `normalize quita hash y espacios`() {
        assertEquals("vivid", Hashtags.normalize("  #Vivid "))
        assertEquals("", Hashtags.normalize("#"))
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
}

package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleManagerTest {

    @Test
    fun `normalizeLang maps empty and system to default`() {
        assertEquals(LocaleManager.DEFAULT_LANG, LocaleManager.normalizeLang(""))
        assertEquals(LocaleManager.DEFAULT_LANG, LocaleManager.normalizeLang(LocaleManager.SYSTEM_LANG))
    }

    @Test
    fun `normalizeLang keeps supported codes`() {
        assertEquals("es", LocaleManager.normalizeLang("es"))
        assertEquals("en", LocaleManager.normalizeLang("en"))
    }

    @Test
    fun `normalizeLang falls back on unknown codes`() {
        assertEquals(LocaleManager.DEFAULT_LANG, LocaleManager.normalizeLang("fr"))
        assertEquals(LocaleManager.DEFAULT_LANG, LocaleManager.normalizeLang("pt-BR"))
    }

    @Test
    fun `clampFontScale stays inside supported range`() {
        assertEquals(0.85f, LocaleManager.clampFontScale(0.1f), 0.001f)
        assertEquals(1.30f, LocaleManager.clampFontScale(3f), 0.001f)
        assertEquals(1.0f, LocaleManager.clampFontScale(1.0f), 0.001f)
    }

    @Test
    fun `supported langs are spanish and english`() {
        assertEquals(listOf("es", "en"), LocaleManager.SUPPORTED_LANGS)
    }

    @Test
    fun `font scales include small normal large xlarge`() {
        assertEquals(listOf(0.85f, 1.0f, 1.15f, 1.30f), LocaleManager.FONT_SCALES)
    }
}

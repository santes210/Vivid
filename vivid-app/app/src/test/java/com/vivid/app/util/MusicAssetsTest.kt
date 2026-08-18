package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicAssetsTest {

    @Test
    fun `legacy wav paths remap to compressed mp3`() {
        assertEquals("music/vivid_pop.mp3", MusicAssets.resolvePackedPath("music/vivid_pop.wav"))
        assertEquals("music/lofi_dreams.mp3", MusicAssets.resolvePackedPath("/music/lofi_dreams.WAV"))
    }

    @Test
    fun `already compressed paths stay unchanged`() {
        assertEquals("music/sunset_chill.mp3", MusicAssets.resolvePackedPath("music/sunset_chill.mp3"))
        assertEquals("music/custom.m4a", MusicAssets.resolvePackedPath("music/custom.m4a"))
    }

    @Test
    fun `blank path stays blank`() {
        assertEquals("", MusicAssets.resolvePackedPath("   "))
    }

    @Test
    fun `packed audio extensions are recognized`() {
        assertTrue(MusicAssets.isPackedAudio("track.mp3"))
        assertTrue(MusicAssets.isPackedAudio("track.ogg"))
        assertTrue(MusicAssets.isPackedAudio("track.m4a"))
        assertTrue(MusicAssets.isPackedAudio("legacy.wav"))
        assertFalse(MusicAssets.isPackedAudio("readme.md"))
        assertFalse(MusicAssets.isPackedAudio("cover.jpg"))
    }
}

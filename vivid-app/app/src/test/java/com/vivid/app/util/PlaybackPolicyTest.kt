package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {

    @Test
    fun `data saver prepares only after manual play and stops offscreen`() {
        val decision = PlaybackPolicy.decide(true, NetworkTransport.Wifi, false, true)
        assertFalse(PlaybackPolicy.shouldPrepareVideo(true, false, decision))
        assertFalse(PlaybackPolicy.shouldPlayVideo(true, false, false, decision))
        assertTrue(PlaybackPolicy.shouldPrepareVideo(true, true, decision))
        assertTrue(PlaybackPolicy.shouldPlayVideo(true, false, true, decision))
        assertFalse(PlaybackPolicy.shouldPlayVideo(true, true, true, decision))
        assertFalse(PlaybackPolicy.shouldPrepareVideo(false, true, decision))
        assertFalse(PlaybackPolicy.shouldPlayVideo(false, false, true, decision))
    }

    @Test
    fun `wifi may prepare neighbors but must never play them`() {
        val decision = PlaybackPolicy.decide(false, NetworkTransport.Wifi, false, true)
        assertTrue(PlaybackPolicy.shouldPrepareVideo(false, false, decision))
        assertFalse(PlaybackPolicy.shouldPlayVideo(false, false, false, decision))
    }

    @Test
    fun `wifi unmetered prefetches next page`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = false,
            transport = NetworkTransport.Wifi,
            isMetered = false,
            autoplaySetting = true
        )
        assertEquals(15, decision.pageSize)
        assertEquals(1, decision.beyondViewportPageCount)
        assertTrue(decision.prefetchNextMedia)
        assertTrue(decision.autoplayAllowed)
        assertFalse(decision.constrained)
    }

    @Test
    fun `cellular does not prefetch`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = false,
            transport = NetworkTransport.Cellular,
            isMetered = true,
            autoplaySetting = true
        )
        assertEquals(0, decision.beyondViewportPageCount)
        assertFalse(decision.prefetchNextMedia)
        assertTrue(decision.constrained)
        assertTrue(decision.autoplayAllowed)
    }

    @Test
    fun `data saver disables autoplay and prefetch even on wifi`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = true,
            transport = NetworkTransport.Wifi,
            isMetered = false,
            autoplaySetting = true
        )
        assertEquals(6, decision.pageSize)
        assertEquals(0, decision.beyondViewportPageCount)
        assertFalse(decision.prefetchNextMedia)
        assertFalse(decision.autoplayAllowed)
        assertTrue(decision.constrained)
    }

    @Test
    fun `offline never autoplays`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = false,
            transport = NetworkTransport.None,
            isMetered = true,
            autoplaySetting = true
        )
        assertFalse(decision.autoplayAllowed)
        assertFalse(decision.prefetchNextMedia)
        assertEquals(0, decision.beyondViewportPageCount)
    }

    @Test
    fun `ethernet behaves like wifi`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = false,
            transport = NetworkTransport.Ethernet,
            isMetered = false,
            autoplaySetting = false
        )
        assertTrue(decision.prefetchNextMedia)
        assertFalse(decision.autoplayAllowed)
        assertEquals(1, decision.beyondViewportPageCount)
    }

    @Test
    fun `metered wifi is treated as constrained`() {
        val decision = PlaybackPolicy.decide(
            dataSaver = false,
            transport = NetworkTransport.Wifi,
            isMetered = true,
            autoplaySetting = true
        )
        // Wi-Fi + unmetered is the only full-prefetch path; a metered hotspot
        // must not prefetch even if the transport says Wifi.
        assertFalse(decision.prefetchNextMedia)
        assertEquals(0, decision.beyondViewportPageCount)
        assertTrue(decision.constrained)
    }
}

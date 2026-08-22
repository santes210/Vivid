package com.vivid.app.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class VividSpaceTest {

    @Test
    fun `scale matches the documented 4-8-12-16-24-32-48 rhythm`() {
        assertEquals(4f, VividSpace.xxs.value)
        assertEquals(8f, VividSpace.xs.value)
        assertEquals(12f, VividSpace.s.value)
        assertEquals(16f, VividSpace.m.value)
        assertEquals(24f, VividSpace.l.value)
        assertEquals(32f, VividSpace.xl.value)
        assertEquals(48f, VividSpace.xxl.value)
    }
}

package com.vivid.app.presentation.messages

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoControlMathTest {
    @Test fun `ten second seeks stay inside the video`() {
        assertEquals(0L, VideoControlMath.seekTarget(3_000, 30_000, false))
        assertEquals(30_000L, VideoControlMath.seekTarget(25_000, 30_000, true))
        assertEquals(20_000L, VideoControlMath.seekTarget(10_000, 60_000, true))
        assertEquals(10_000L, VideoControlMath.seekTarget(20_000, 60_000, false))
    }

    @Test fun `unknown duration and invalid positions are safe`() {
        assertEquals(0L, VideoControlMath.seekTarget(3_000, -1, true))
        assertEquals(10_000L, VideoControlMath.seekTarget(-1, 60_000, true))
        assertEquals(Long.MAX_VALUE, VideoControlMath.seekTarget(Long.MAX_VALUE - 1, Long.MAX_VALUE, true))
    }

    @Test fun `labels handle minutes hours and invalid values`() {
        assertEquals("0:00", VideoControlMath.timeLabel(-1))
        assertEquals("1:05", VideoControlMath.timeLabel(65_000))
        assertEquals("1:01:02", VideoControlMath.timeLabel(3_662_000))
    }
}

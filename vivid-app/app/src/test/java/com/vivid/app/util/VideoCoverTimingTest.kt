package com.vivid.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoCoverTimingTest {
    @Test fun `selected source time follows the trimmed video`() {
        assertEquals(2000L, VideoCoverTiming.exportedTime(7000, 5000, 10000))
        assertEquals(0L, VideoCoverTiming.exportedTime(3000, 5000, 10000))
    }
    @Test fun `untrimmed exports keep the selected time`() {
        assertEquals(7000L, VideoCoverTiming.exportedTime(7000, 0, -1))
        assertEquals(7000L, VideoCoverTiming.exportedTime(7000, 5000, -1))
    }
    @Test fun `requested frame cannot be past the last millisecond`() {
        assertEquals(999L, VideoCoverTiming.frameTime(2000, 1000))
        assertEquals(0L, VideoCoverTiming.frameTime(-10, 1000))
    }
    @Test fun `automatic covers handle short and unknown videos`() {
        assertEquals(500L, VideoCoverTiming.frameTime(null, 1000))
        assertEquals(1000L, VideoCoverTiming.frameTime(null, 10000))
        assertEquals(0L, VideoCoverTiming.frameTime(null, 0))
    }
}

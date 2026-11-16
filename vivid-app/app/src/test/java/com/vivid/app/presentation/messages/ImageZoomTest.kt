package com.vivid.app.presentation.messages

import org.junit.Assert.*
import org.junit.Test

class ImageZoomTest {
    @Test fun `zoom stays between original size and four times`() {
        assertEquals(4f, ImageZoom().transform(20f, 0f, 0f, 100f, 200f).scale, 0.001f)
        assertEquals(ImageZoom(), ImageZoom(3f, 10f, 20f).transform(0.1f, 5f, 5f, 100f, 200f))
    }

    @Test fun `panning is bounded by the enlarged viewport`() {
        val result = ImageZoom().transform(2f, 1000f, -1000f, 100f, 200f)
        assertEquals(50f, result.x, 0.001f)
        assertEquals(-100f, result.y, 0.001f)
    }

    @Test fun `zoom preserves the touched focal point`() {
        val result = ImageZoom().transform(2f, 0f, 0f, 200f, 200f, 25f, -30f)
        assertEquals(-25f, result.x, 0.001f)
        assertEquals(30f, result.y, 0.001f)
    }

    @Test fun `original size cannot be panned off screen`() {
        assertEquals(ImageZoom(), ImageZoom().transform(1f, 200f, 300f, 100f, 200f))
    }

    @Test fun `invalid gesture input does not corrupt the viewport`() {
        val initial = ImageZoom(2f, 10f, 10f)
        assertEquals(initial, initial.transform(Float.NaN, 0f, 0f, 100f, 100f))
        assertEquals(initial, initial.transform(1f, Float.POSITIVE_INFINITY, 0f, 100f, 100f))
        assertEquals(initial, initial.transform(0f, 0f, 0f, 100f, 100f))
    }
}

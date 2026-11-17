package com.vivid.app.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteContentSafetyTest {
    @Test fun `cleanup only accepts own explicitly stored keys in this content namespace`() {
        val result = ownedMediaKeys(listOf("reels/me/video.mp4", "reels/me/thumb.jpg",
            "reels/other/video.mp4", "posts/me/photo.jpg", "reels/me/../other/file", "",
            "reels/me/video.mp4", "reels/me-too/file.mp4"), "reels", "me")
        assertEquals(listOf("reels/me/video.mp4", "reels/me/thumb.jpg"), result)
    }
}

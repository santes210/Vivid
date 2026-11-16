package com.vivid.app.presentation.feed

import com.vivid.app.data.local.entity.toCachedEntity
import com.vivid.app.data.local.entity.toPostData
import org.junit.Assert.*
import org.junit.Test

class PostAccessibilityCacheTest {
    @Test fun `cache preserves alternative text and restricted audience`() {
        val post = PostData("p", "u", "name", "", caption = "caption", timestamp = 1,
            altText = "A red flower", visibility = "friends")
        val restored = post.toCachedEntity().toPostData()
        assertEquals(post.altText, restored.altText)
        assertEquals(post.visibility, restored.visibility)
    }
}

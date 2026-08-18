package com.vivid.app.presentation.feed

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the Feed data models.
 */
class FeedModelsTest {

    // ── PostData defaults ──

    @Test
    fun `PostData defaults are correct`() {
        val post = PostData(
            id = "p1",
            userId = "u1",
            username = "alice",
            userProfilePicture = "",
            caption = "Hello",
            timestamp = 1000L
        )
        assertEquals("p1", post.id)
        assertEquals("u1", post.userId)
        assertEquals("alice", post.username)
        assertEquals("Hello", post.caption)
        assertEquals(0, post.likesCount)
        assertEquals(0, post.commentsCount)
        assertFalse(post.isLiked)
        assertFalse(post.isSaved)
        assertFalse(post.isVideo)
        assertEquals("", post.imageUrl)
        assertEquals("", post.videoUrl)
        assertEquals("", post.musicTitle)
        assertEquals("", post.storageKey)
    }

    @Test
    fun `PostData copy preserves values and allows overrides`() {
        val post = PostData(
            id = "p1", userId = "u1", username = "alice",
            userProfilePicture = "", caption = "cap", timestamp = 100L,
            likesCount = 5, isLiked = false
        )
        val liked = post.copy(isLiked = true, likesCount = 6)
        assertTrue(liked.isLiked)
        assertEquals(6, liked.likesCount)
        assertEquals(post.id, liked.id)
        assertEquals(post.caption, liked.caption)
    }

    // ── PostComment defaults ──

    @Test
    fun `PostComment defaults are correct`() {
        val comment = PostComment(
            id = "c1", userId = "u1", username = "bob",
            text = "Nice!", timestamp = 2000L
        )
        assertEquals(0, comment.likesCount)
        assertFalse(comment.isLiked)
        assertFalse(comment.isEdited)
        assertNull(comment.parentId)
        assertEquals("", comment.replyToUsername)
        assertEquals("", comment.avatarUrl)
    }

    @Test
    fun `PostComment with parentId is a reply`() {
        val reply = PostComment(
            id = "c2", userId = "u2", username = "eve",
            text = "Agreed!", timestamp = 3000L,
            parentId = "c1", replyToUsername = "bob"
        )
        assertEquals("c1", reply.parentId)
        assertEquals("bob", reply.replyToUsername)
    }

    // ── FEED_CACHE_WRITE_INTERVAL_MS ──

    @Test
    fun `cache write interval is 60 seconds`() {
        assertEquals(60_000L, FEED_CACHE_WRITE_INTERVAL_MS)
    }

    // ── PostData with music ──

    @Test
    fun `PostData with music fields`() {
        val post = PostData(
            id = "p2", userId = "u1", username = "alice",
            userProfilePicture = "", caption = "",
            timestamp = 100L,
            musicTitle = "Song", musicArtist = "Artist",
            musicAssetFile = "music/song.mp3",
            musicUrl = "https://example.com/song.mp3",
            musicStorageKey = "key123"
        )
        assertEquals("Song", post.musicTitle)
        assertEquals("Artist", post.musicArtist)
        assertEquals("music/song.mp3", post.musicAssetFile)
        assertEquals("https://example.com/song.mp3", post.musicUrl)
        assertEquals("key123", post.musicStorageKey)
    }
}

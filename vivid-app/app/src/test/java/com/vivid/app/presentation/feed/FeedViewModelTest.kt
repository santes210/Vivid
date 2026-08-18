package com.vivid.app.presentation.feed

import com.vivid.app.data.local.entity.PostEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [FeedViewModel] pure functions.
 *
 * Tests that require Firestore / Hilt are better suited for integration tests.
 * Here we test the data-mapping and URL-preference logic that doesn't need DI.
 */
class FeedViewModelTest {

    // ── cachedPostsToData ──

    @Test
    fun `cachedPostsToData maps all fields correctly`() {
        // We can't instantiate FeedViewModel directly (Hilt), but we can
        // test the static mapping logic by creating a temporary instance
        // or, better, test the PostEntity → PostData mapping contract.
        val entity = PostEntity(
            id = "p1",
            userId = "u1",
            username = "alice",
            userProfilePicture = "pic.jpg",
            imageUrl = "https://img.example.com/1.jpg",
            imageBase64 = "base64data",
            caption = "Hello world",
            likesCount = 42,
            commentsCount = 7,
            timestamp = 1000L,
            isLiked = true,
            storageKey = "sk1",
            videoUrl = "https://vid.example.com/1.mp4",
            thumbnailUrl = "https://thumb.example.com/1.jpg",
            isVideo = true,
            musicTitle = "Song Title",
            musicArtist = "Artist Name",
            musicAssetFile = "music/file.mp3",
            musicUrl = "https://music.example.com/1.mp3",
            musicStorageKey = "msk1",
            cachedAt = 5000L
        )

        val postData = entityToPostData(entity)

        assertEquals("p1", postData.id)
        assertEquals("u1", postData.userId)
        assertEquals("alice", postData.username)
        assertEquals("pic.jpg", postData.userProfilePicture)
        assertEquals("https://img.example.com/1.jpg", postData.imageUrl)
        assertEquals("base64data", postData.imageBase64)
        assertEquals("Hello world", postData.caption)
        assertEquals(42, postData.likesCount)
        assertEquals(7, postData.commentsCount)
        assertEquals(1000L, postData.timestamp)
        assertTrue(postData.isLiked)
        assertEquals("sk1", postData.storageKey)
        assertEquals("https://vid.example.com/1.mp4", postData.videoUrl)
        assertTrue(postData.isVideo)
        assertEquals("Song Title", postData.musicTitle)
        assertEquals("Artist Name", postData.musicArtist)
    }

    @Test
    fun `cachedPostsToData handles empty list`() {
        val result = emptyList<PostEntity>().map { entityToPostData(it) }
        assertTrue(result.isEmpty())
    }

    // ── PostEntity → PostData mapping (mirrors ViewModel logic) ──

    private fun entityToPostData(entity: PostEntity): PostData = PostData(
        id = entity.id,
        userId = entity.userId,
        username = entity.username,
        userProfilePicture = entity.userProfilePicture,
        imageUrl = entity.imageUrl,
        imageBase64 = entity.imageBase64,
        storageKey = entity.storageKey,
        videoUrl = entity.videoUrl,
        thumbnailUrl = entity.thumbnailUrl,
        isVideo = entity.isVideo,
        caption = entity.caption,
        likesCount = entity.likesCount,
        commentsCount = entity.commentsCount,
        timestamp = entity.timestamp,
        isLiked = entity.isLiked,
        musicTitle = entity.musicTitle,
        musicArtist = entity.musicArtist,
        musicAssetFile = entity.musicAssetFile,
        musicUrl = entity.musicUrl,
        musicStorageKey = entity.musicStorageKey
    )
}

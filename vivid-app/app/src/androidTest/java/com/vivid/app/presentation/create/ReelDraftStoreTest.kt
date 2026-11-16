package com.vivid.app.presentation.create

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ReelDraftStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun draft(file: File) = ReelDraft(Uri.fromFile(file), "Caption", 2000, 8000, 3000, true,
        null, "music/test.mp3", "Track", "Artist", 0.8f, 0.2f)

    @Test fun draftSurvivesOriginalFileRemovalAndStoreRecreation() = runBlocking {
        val uid = "test-${UUID.randomUUID()}"
        val source = File.createTempFile("draft_test", ".mp4", context.cacheDir).apply { writeText("local media") }
        val store = ReelDraftStore(context, uid)
        try {
            store.save(draft(source))
            source.delete()
            val restored = checkNotNull(ReelDraftStore(context, uid).load())
            assertEquals("local media", File(checkNotNull(restored.video.path)).readText())
            assertEquals("Caption", restored.caption)
            assertEquals(2000L, restored.trimStart)
            assertEquals(3000L, restored.cover)
            assertEquals("music/test.mp3", restored.musicAsset)
            assertEquals(0.8f, restored.musicVolume, 0.001f)
        } finally { store.delete(); source.delete() }
    }

    @Test fun failedReplacementKeepsPreviousDraftAndAccountsAreIsolated() = runBlocking {
        val store = ReelDraftStore(context, "test-${UUID.randomUUID()}")
        val other = ReelDraftStore(context, "test-${UUID.randomUUID()}")
        val source = File.createTempFile("draft_test", ".mp4", context.cacheDir).apply { writeText("original") }
        try {
            store.save(draft(source))
            try {
                store.save(draft(File(context.cacheDir, "missing-${UUID.randomUUID()}")))
                fail("Expected unreadable media failure")
            } catch (_: java.io.FileNotFoundException) { }
            assertEquals("original", File(checkNotNull(store.load()!!.video.path)).readText())
            assertNull(other.load())
            store.delete()
            assertTrue(source.exists())
            assertNull(store.load())
        } finally { store.delete(); other.delete(); source.delete() }
    }
    @Test fun photoDraftPreservesAudienceAndAltTextWithoutReplacingReel() = runBlocking {
        val uid = "test-${UUID.randomUUID()}"
        val posts = ReelDraftStore(context, uid, forPost = true)
        val reels = ReelDraftStore(context, uid)
        val source = File.createTempFile("draft_photo", ".jpg", context.cacheDir).apply { writeText("photo") }
        try {
            reels.save(draft(source).copy(caption = "Reel"))
            posts.save(draft(source).copy(caption = "Photo", visibility = "friends", altText = "A red flower"))
            val restored = checkNotNull(ReelDraftStore(context, uid, forPost = true).load())
            assertEquals("friends", restored.visibility)
            assertEquals("A red flower", restored.altText)
            assertEquals("Photo", restored.caption)
            assertEquals("Reel", reels.load()!!.caption)
            posts.delete()
            assertNotNull(reels.load())
        } finally { posts.delete(); reels.delete(); source.delete() }
    }

}

package com.vivid.app.presentation.messages

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.vivid.app.data.storage.StorageProvider
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ChatReelRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val storage = mockk<StorageProvider>()
    private val collection = mockk<CollectionReference>()
    private val ref = mockk<DocumentReference>()
    private val doc = mockk<DocumentSnapshot>()
    private val repository = ChatReelRepository(firestore, auth, storage)

    init {
        every { auth.currentUser } returns user
        every { user.uid } returns "viewer"
        every { firestore.collection("reels") } returns collection
        every { collection.document("r1") } returns ref
        every { ref.get(Source.SERVER) } returns Tasks.forResult(doc)
        every { doc.exists() } returns true
        every { doc.getString("storageKey") } returns "reels/author/video.mp4"
        every { doc.getString("username") } returns "author"
        every { doc.getString("caption") } returns "Caption"
        coEvery { storage.signDownloadUrl(any(), any()) } returns "https://media.example/video.mp4"
    }

    @Test fun `authorizes on server before requesting media`() = runTest {
        val result = repository.load("r1")
        assertEquals("r1", result.id)
        assertEquals("https://media.example/video.mp4", result.url)
        coVerifyOrder {
            ref.get(Source.SERVER)
            storage.signDownloadUrl("reels/author/video.mp4", any())
        }
    }

    @Test fun `denied access never requests a media URL`() = runTest {
        every { ref.get(Source.SERVER) } returns Tasks.forException(SecurityException("denied"))
        try { repository.load("r1"); fail("Expected denied access") } catch (_: SecurityException) { }
        coVerify(exactly = 0) { storage.signDownloadUrl(any(), any()) }
    }

    @Test fun `deleted reel never requests a media URL`() = runTest {
        every { doc.exists() } returns false
        try { repository.load("r1"); fail("Expected unavailable reel") } catch (_: IllegalStateException) { }
        coVerify(exactly = 0) { storage.signDownloadUrl(any(), any()) }
    }

    @Test fun `local file URLs cannot be played from shared content`() = runTest {
        coEvery { storage.signDownloadUrl(any(), any()) } returns "file:///private/video.mp4"
        try { repository.load("r1"); fail("Expected invalid URL") } catch (_: IllegalArgumentException) { }
    }

    @Test fun `video post uses posts collection and retains content type`() = runTest {
        every { firestore.collection("posts") } returns collection
        every { doc.getBoolean("isVideo") } returns true
        val result = repository.load("r1", "posts")
        assertEquals("posts", result.collection)
        assertTrue(result.isVideo)
        verify(exactly = 0) { firestore.collection("reels") }
    }

    @Test fun `image post has no video controls`() = runTest {
        every { firestore.collection("posts") } returns collection
        every { doc.getBoolean("isVideo") } returns false
        assertFalse(repository.load("r1", "posts").isVideo)
    }

    @Test fun `arbitrary collections are rejected`() = runTest {
        try { repository.load("r1", "users"); fail("Expected invalid collection") } catch (_: IllegalArgumentException) { }
        verify(exactly = 0) { firestore.collection(any()) }
    }

    @Test fun `session change during loading does not expose video`() = runTest {
        every { auth.currentUser } returnsMany listOf(user, null)
        try { repository.load("r1"); fail("Expected session change") } catch (_: IllegalStateException) { }
    }
}

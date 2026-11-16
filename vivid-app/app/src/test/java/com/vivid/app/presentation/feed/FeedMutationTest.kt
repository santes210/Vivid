package com.vivid.app.presentation.feed

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.*
import com.vivid.app.data.local.dao.PostDao
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedMutationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    private val db = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val document = mockk<DocumentReference>()
    private fun model(): FeedViewModel {
        val dao = mockk<PostDao>()
        every { dao.getAllPosts() } returns flowOf(emptyList())
        every { db.collection("posts") } returns collection
        every { collection.document("p1") } returns document
        return FeedViewModel(mockk(), dao, db, mockk(), mockk(), mockk())
    }

    @Test fun `edit reports success only after server acknowledgement`() = runTest(dispatcher) {
        val vm = model()
        val pending = TaskCompletionSource<Void>()
        every { document.update(any<Map<String, Any>>()) } returns pending.task
        var completed = false
        vm.editPostCaption("p1", " caption ") { error -> assertNull(error); completed = true }
        runCurrent()
        assertFalse(completed)
        pending.setResult(null)
        advanceUntilIdle()
        assertTrue(completed)
        verify { document.update(match<Map<String, Any>> { it["caption"] == "caption" }) }
    }

    @Test fun `failed edit reaches caller instead of false success`() = runTest(dispatcher) {
        val vm = model()
        every { document.update(any<Map<String, Any>>()) } returns Tasks.forException(SecurityException("denied"))
        var failure: Exception? = null
        vm.editPostCaption("p1", "draft") { failure = it }
        advanceUntilIdle()
        assertTrue(failure is SecurityException)
    }

    @Test fun `an unacknowledged edit stops waiting without discarding the draft`() = runTest(dispatcher) {
        val vm = model()
        val pending = TaskCompletionSource<Void>()
        every { document.update(any<Map<String, Any>>()) } returns pending.task
        var failure: Exception? = null
        vm.editPostCaption("p1", "draft") { failure = it }
        advanceUntilIdle()
        assertTrue(failure is kotlinx.coroutines.TimeoutCancellationException)
    }

    @Test fun `saving without a session reports failure`() = runTest(dispatcher) {
        val vm = model()
        var result: Boolean? = null
        vm.toggleSavePost("p1", "", true) { success, _ -> result = success }
        assertEquals(false, result)
        verify(exactly = 0) { db.collection("users") }
    }
}

package com.vivid.app.presentation.stories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vivid.app.domain.repository.ChatRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoryInteractionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `retry uses same message id and story reference`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "viewer"
        val chats = mockk<ChatRepository>()
        val ids = mutableListOf<String>()
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } coAnswers {
            assertEquals("story1", arg<String>(3))
            ids += arg<String>(4)
            if (ids.size == 1) error("Acknowledgement lost")
        }
        val vm = StoryInteractionViewModel(chats, auth)
        val outcomes = mutableListOf<Boolean>()
        vm.reply("story1", "author", "Hello") { outcomes += it }
        advanceUntilIdle()
        vm.reply("story1", "author", "Hello") { outcomes += it }
        advanceUntilIdle()
        assertEquals(listOf(false, true), outcomes)
        assertEquals(ids[0], ids[1])
    }

    @Test fun `blank and self replies never send`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "viewer"
        val chats = mockk<ChatRepository>()
        val vm = StoryInteractionViewModel(chats, auth)
        vm.reply("story", "author", "  ") { assertFalse(it) }
        vm.reply("story", "viewer", "Hello") { assertFalse(it) }
        coVerify(exactly = 0) { chats.sendMessage(any(), any(), any(), any(), any()) }
    }
}

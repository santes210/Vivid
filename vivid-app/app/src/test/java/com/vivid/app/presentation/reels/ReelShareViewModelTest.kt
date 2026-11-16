package com.vivid.app.presentation.reels

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
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
class ReelShareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `batch retries keep id and note and never resend successful recipients`() = runTest(dispatcher) {
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "me"
        val db = mockk<FirebaseFirestore>()
        val chatCollection = mockk<CollectionReference>()
        val query = mockk<Query>()
        val snapshot = mockk<QuerySnapshot>()
        val registration = mockk<ListenerRegistration>(relaxed = true)
        every { db.collection("chats") } returns chatCollection
        every { chatCollection.whereArrayContains("participants", "me") } returns query
        every { query.orderBy("lastTimestamp", Query.Direction.DESCENDING) } returns query
        every { query.limit(30) } returns query
        fun recipient(uid: String): DocumentSnapshot = mockk<DocumentSnapshot>().also { doc ->
            every { doc.id } returns "me_$uid"
            every { doc.get("participants") } returns listOf("me", uid)
            every { doc.get("participantNames") } returns mapOf(uid to uid)
            every { doc.get("participantAvatars") } returns mapOf(uid to "https://example.com/$uid.jpg")
        }
        every { snapshot.documents } returns listOf(recipient("a"), recipient("b"))
        every { query.addSnapshotListener(any<EventListener<QuerySnapshot>>()) } answers {
            firstArg<EventListener<QuerySnapshot>>().onEvent(snapshot, null)
            registration
        }
        val users = mockk<CollectionReference>()
        val userRef = mockk<DocumentReference>()
        val userDoc = mockk<DocumentSnapshot>()
        every { db.collection("users") } returns users
        every { users.document(any()) } returns userRef
        every { userRef.get(Source.SERVER) } returns Tasks.forResult(userDoc)
        every { userDoc.exists() } returns true
        every { userDoc.get("blockedUsers") } returns emptyList<String>()
        every { userDoc.getBoolean("isPrivate") } returns false
        val chats = mockk<ChatRepository>()
        data class Attempt(val recipient: String, val text: String, val id: String)
        val attempts = mutableListOf<Attempt>()
        coEvery { chats.sendMessage(any(), any(), any(), any(), any()) } coAnswers {
            val attempt = Attempt(arg(2), arg(1), arg(4))
            attempts += attempt
            if (attempt.recipient == "b" && attempts.count { it.recipient == "b" } == 1) error("Lost acknowledgement")
        }
        val vm = ReelShareViewModel(chats, auth, db)
        vm.loadRecipients()
        assertEquals("https://example.com/a.jpg", vm.recipients.value.first().avatarUrl)
        val link = "vivid://reel/r1"
        val chosen = setOf("me_a", "me_b")
        vm.sendSelection(link, chosen, "Shared:\n$link", "Original note")
        advanceUntilIdle()
        assertEquals(ReelShareStatus.SENT, vm.statuses.value["$link:me_a"])
        assertEquals(ReelShareStatus.FAILED, vm.statuses.value["$link:me_b"])
        vm.sendSelection(link, chosen, "Shared:\n$link", "Changed note")
        advanceUntilIdle()
        assertEquals(1, attempts.count { it.recipient == "a" })
        val retried = attempts.filter { it.recipient == "b" }
        assertEquals(2, retried.size)
        assertEquals(retried[0].id, retried[1].id)
        assertEquals(retried[0].text, retried[1].text)
        assertTrue(retried[1].text.startsWith("Original note"))
        assertEquals(ReelShareStatus.SENT, vm.statuses.value["$link:me_b"])
        vm.stopRecipients()
    }
}

package com.vivid.app.presentation.profile

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileConnectionsViewModelTest {
    private val db = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { auth.currentUser } returns user
        every { user.uid } returns "me"
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `another account list is not queried`() = runTest {
        val vm = ProfileConnectionsViewModel(db, auth)
        vm.load("other", true)
        advanceUntilIdle()
        verify { db wasNot Called }
        assertTrue(vm.pages.value.isEmpty())
    }

    @Test fun `empty page is cached and exhausted until refresh`() = runTest {
        val users = mockk<CollectionReference>()
        val me = mockk<DocumentReference>()
        val edges = mockk<CollectionReference>()
        val ordered = mockk<Query>()
        val page = mockk<QuerySnapshot>()
        every { db.collection("users") } returns users
        every { users.document("me") } returns me
        every { me.collection("followers") } returns edges
        every { edges.orderBy(any<FieldPath>()) } returns ordered
        every { ordered.limit(30) } returns ordered
        every { ordered.get() } returns Tasks.forResult(page)
        every { page.documents } returns emptyList()
        every { page.size() } returns 0
        val vm = ProfileConnectionsViewModel(db, auth)
        vm.load("me", true)
        advanceUntilIdle()
        assertTrue(vm.pages.value.getValue("me/true").loaded)
        assertFalse(vm.pages.value.getValue("me/true").hasMore)
        vm.load("me", true)
        advanceUntilIdle()
        verify(exactly = 1) { ordered.get() }
        vm.load("me", true, refresh = true)
        advanceUntilIdle()
        verify(exactly = 2) { ordered.get() }
    }
}

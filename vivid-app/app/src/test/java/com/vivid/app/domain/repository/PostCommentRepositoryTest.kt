package com.vivid.app.domain.repository

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class PostCommentRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val posts = mockk<CollectionReference>()
    private val post = mockk<DocumentReference>()
    private val comments = mockk<CollectionReference>()
    private val comment = mockk<DocumentReference>()
    private val batch = mockk<WriteBatch>(relaxed = true)
    private val repository = PostCommentRepository(firestore)
    private val data: Map<String, Any> = mapOf("userId" to "alice", "text" to "hola")

    init {
        every { firestore.collection("posts") } returns posts
        every { posts.document("p1") } returns post
        every { post.collection("comments") } returns comments
        every { comments.document() } returns comment
        every { comment.id } returns "c1"
        every { firestore.batch() } returns batch
    }

    @Test
    fun `creation commits comment and counter together`() = runTest {
        every { batch.commit() } returns Tasks.forResult<Void>(null)

        assertEquals("c1", repository.add("p1", data))

        verifyOrder {
            batch.set(comment, data)
            batch.update(post, "commentsCount", any<FieldValue>())
            batch.commit()
        }
        verify(exactly = 1) { batch.commit() }
        verify(exactly = 0) { comment.set(any<Any>()) }
    }

    @Test
    fun `delete checks existence and does not decrement twice`() = runTest {
        val transaction = mockk<Transaction>(relaxed = true)
        val snapshot = mockk<DocumentSnapshot>()
        every { comments.document("c1") } returns comment
        every { transaction.get(comment) } returns snapshot
        every { snapshot.exists() } returnsMany listOf(true, false)
        every { firestore.runTransaction<Void>(any()) } answers {
            firstArg<Transaction.Function<Void>>().apply(transaction)
            Tasks.forResult<Void>(null)
        }

        repository.delete("p1", "c1")
        repository.delete("p1", "c1")

        verify(exactly = 2) { transaction.get(comment) }
        verify(exactly = 1) { transaction.delete(comment) }
        verify(exactly = 1) { transaction.update(post, "commentsCount", any<FieldValue>()) }
    }

    @Test
    fun `failed commit propagates instead of reporting success`() = runTest {
        val failure = IllegalStateException("Write rejected")
        every { batch.commit() } returns Tasks.forException(failure)

        try {
            repository.add("p1", data)
            fail("Expected commit failure")
        } catch (error: IllegalStateException) {
            assertSame(failure, error)
        }
    }
}

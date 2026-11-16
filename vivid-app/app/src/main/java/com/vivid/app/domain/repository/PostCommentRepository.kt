package com.vivid.app.domain.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Keeps comment writes and their parent counter in the same atomic operation. */
class PostCommentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun add(postId: String, data: Map<String, Any>): String {
        val post = firestore.collection("posts").document(postId)
        val comment = post.collection("comments").document()
        val batch = firestore.batch()
        batch.set(comment, data)
        batch.update(post, "commentsCount", FieldValue.increment(1))
        batch.commit().await()
        return comment.id
    }

    suspend fun delete(postId: String, commentId: String) {
        val post = firestore.collection("posts").document(postId)
        val comment = post.collection("comments").document(commentId)
        // A retried or concurrent deletion must not decrement the counter twice.
        firestore.runTransaction<Void> { transaction ->
            if (transaction.get(comment).exists()) {
                transaction.delete(comment)
                transaction.update(post, "commentsCount", FieldValue.increment(-1))
            }
            null
        }.await()
    }
}

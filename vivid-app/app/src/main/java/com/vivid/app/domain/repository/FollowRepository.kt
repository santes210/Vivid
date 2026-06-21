package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val currentUserId get() = auth.currentUser?.uid ?: ""

    suspend fun followUser(targetUserId: String) {
        if (currentUserId.isEmpty() || targetUserId == currentUserId) return

        val batch = firestore.batch()

        // 1. Add to current user's following subcollection
        val followingRef = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
        batch.set(followingRef, mapOf("timestamp" to System.currentTimeMillis()))

        // 2. Add to target user's followers subcollection
        val followerRef = firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
        batch.set(followerRef, mapOf("timestamp" to System.currentTimeMillis()))

        // 3. Increment current user followingCount
        val currentUserRef = firestore.collection("users").document(currentUserId)
        batch.update(currentUserRef, "followingCount", FieldValue.increment(1))

        // 4. Increment target user followersCount
        val targetUserRef = firestore.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followersCount", FieldValue.increment(1))

        batch.commit().await()
    }

    suspend fun unfollowUser(targetUserId: String) {
        if (currentUserId.isEmpty() || targetUserId == currentUserId) return

        val batch = firestore.batch()

        // 1. Delete from following subcollection
        val followingRef = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
        batch.delete(followingRef)

        // 2. Delete from followers subcollection
        val followerRef = firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
        batch.delete(followerRef)

        // 3. Decrement current user followingCount
        val currentUserRef = firestore.collection("users").document(currentUserId)
        batch.update(currentUserRef, "followingCount", FieldValue.increment(-1))

        // 4. Decrement target user followersCount
        val targetUserRef = firestore.collection("users").document(targetUserId)
        batch.update(targetUserRef, "followersCount", FieldValue.increment(-1))

        batch.commit().await()
    }

    suspend fun isFollowing(targetUserId: String): Boolean {
        if (currentUserId.isEmpty()) return false
        val doc = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .get()
            .await()
        return doc.exists()
    }

    suspend fun getFollowersCount(userId: String): Int {
        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("followers")
            .get()
            .await()
        return snapshot.size()
    }

    suspend fun getFollowingCount(userId: String): Int {
        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("following")
            .get()
            .await()
        return snapshot.size()
    }
}
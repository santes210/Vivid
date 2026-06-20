package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
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
        if (currentUserId.isEmpty()) return

        val batch = firestore.batch()

        // Add to current user's following
        val followingRef = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)

        batch.set(followingRef, mapOf("timestamp" to System.currentTimeMillis()))

        // Add to target user's followers
        val followerRef = firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)

        batch.set(followerRef, mapOf("timestamp" to System.currentTimeMillis()))

        batch.commit().await()
    }

    suspend fun unfollowUser(targetUserId: String) {
        if (currentUserId.isEmpty()) return

        val batch = firestore.batch()

        firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .delete()

        firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
            .delete()

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
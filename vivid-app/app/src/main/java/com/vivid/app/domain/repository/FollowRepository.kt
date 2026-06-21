package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

        ensureUserDocumentExists(currentUserId, isCurrentUser = true)
        ensureUserDocumentExists(targetUserId, isCurrentUser = false)

        val now = System.currentTimeMillis()
        val batch = firestore.batch()

        val followingRef = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
        batch.set(followingRef, mapOf("timestamp" to now))

        val followerRef = firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
        batch.set(followerRef, mapOf("timestamp" to now))

        val currentUserRef = firestore.collection("users").document(currentUserId)
        batch.set(
            currentUserRef,
            mapOf(
                "uid" to currentUserId,
                "updatedAt" to now,
                "followingCount" to FieldValue.increment(1)
            ),
            SetOptions.merge()
        )

        val targetUserRef = firestore.collection("users").document(targetUserId)
        batch.set(
            targetUserRef,
            mapOf(
                "uid" to targetUserId,
                "updatedAt" to now,
                "followersCount" to FieldValue.increment(1)
            ),
            SetOptions.merge()
        )

        batch.commit().await()
    }

    suspend fun unfollowUser(targetUserId: String) {
        if (currentUserId.isEmpty() || targetUserId == currentUserId) return

        ensureUserDocumentExists(currentUserId, isCurrentUser = true)
        ensureUserDocumentExists(targetUserId, isCurrentUser = false)

        val now = System.currentTimeMillis()
        val batch = firestore.batch()

        val followingRef = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
        batch.delete(followingRef)

        val followerRef = firestore.collection("users")
            .document(targetUserId)
            .collection("followers")
            .document(currentUserId)
        batch.delete(followerRef)

        val currentUserRef = firestore.collection("users").document(currentUserId)
        val currentUserSnapshot = currentUserRef.get().await()
        val currentFollowingCount = (currentUserSnapshot.getLong("followingCount") ?: 0L).coerceAtLeast(0L)

        val targetUserRef = firestore.collection("users").document(targetUserId)
        val targetUserSnapshot = targetUserRef.get().await()
        val targetFollowersCount = (targetUserSnapshot.getLong("followersCount") ?: 0L).coerceAtLeast(0L)

        batch.set(
            currentUserRef,
            mapOf(
                "uid" to currentUserId,
                "updatedAt" to now,
                "followingCount" to (currentFollowingCount - 1L).coerceAtLeast(0L)
            ),
            SetOptions.merge()
        )

        batch.set(
            targetUserRef,
            mapOf(
                "uid" to targetUserId,
                "updatedAt" to now,
                "followersCount" to (targetFollowersCount - 1L).coerceAtLeast(0L)
            ),
            SetOptions.merge()
        )

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

    private suspend fun ensureUserDocumentExists(userId: String, isCurrentUser: Boolean) {
        val ref = firestore.collection("users").document(userId)
        val snapshot = ref.get().await()
        if (snapshot.exists()) return

        val authUser = if (isCurrentUser) auth.currentUser else null
        val fallbackUsername = authUser?.displayName
            ?: authUser?.email?.substringBefore("@")
            ?: "usuario"

        ref.set(
            mapOf(
                "uid" to userId,
                "username" to fallbackUsername,
                "usernameLower" to fallbackUsername.lowercase(),
                "displayName" to fallbackUsername,
                "displayNameLower" to fallbackUsername.lowercase(),
                "email" to (authUser?.email ?: ""),
                "avatarUrl" to (authUser?.photoUrl?.toString() ?: ""),
                "bio" to "",
                "followersCount" to 0,
                "followingCount" to 0,
                "postsCount" to 0,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }
}

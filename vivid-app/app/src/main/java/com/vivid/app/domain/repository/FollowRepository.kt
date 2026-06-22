package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class SocialUserPreview(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val avatarBase64: String = ""
)

data class FollowRelationshipState(
    val isFollowing: Boolean = false,
    val hasPendingRequest: Boolean = false,
    val isTargetPrivate: Boolean = false,
    val isBlocked: Boolean = false
)

enum class FollowActionResult {
    FOLLOWED,
    UNFOLLOWED,
    REQUESTED,
    REQUEST_CANCELLED
}

@Singleton
class FollowRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val currentUserId get() = auth.currentUser?.uid ?: ""

    suspend fun getRelationshipState(targetUserId: String): FollowRelationshipState {
        if (currentUserId.isBlank() || targetUserId.isBlank() || targetUserId == currentUserId) {
            return FollowRelationshipState()
        }

        val currentUserSnapshot = firestore.collection("users").document(currentUserId).get().await()
        val targetSnapshot = firestore.collection("users").document(targetUserId).get().await()

        val isFollowing = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .document(targetUserId)
            .get()
            .await()
            .exists()

        val hasPendingRequest = firestore.collection("users")
            .document(targetUserId)
            .collection("followRequests")
            .document(currentUserId)
            .get()
            .await()
            .exists()

        val blockedUsers = (currentUserSnapshot.get("blockedUsers") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()

        return FollowRelationshipState(
            isFollowing = isFollowing,
            hasPendingRequest = hasPendingRequest,
            isTargetPrivate = targetSnapshot.getBoolean("isPrivate") ?: false,
            isBlocked = blockedUsers.contains(targetUserId)
        )
    }

    suspend fun toggleFollow(targetUserId: String): FollowActionResult {
        val relationshipState = getRelationshipState(targetUserId)
        return when {
            relationshipState.isFollowing -> {
                unfollowUser(targetUserId)
                FollowActionResult.UNFOLLOWED
            }
            relationshipState.hasPendingRequest -> {
                cancelFollowRequest(targetUserId)
                FollowActionResult.REQUEST_CANCELLED
            }
            relationshipState.isTargetPrivate -> {
                sendFollowRequest(targetUserId)
                FollowActionResult.REQUESTED
            }
            else -> {
                followUser(targetUserId)
                FollowActionResult.FOLLOWED
            }
        }
    }

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

        batch.delete(
            firestore.collection("users")
                .document(targetUserId)
                .collection("followRequests")
                .document(currentUserId)
        )
        batch.delete(
            firestore.collection("users")
                .document(currentUserId)
                .collection("sentFollowRequests")
                .document(targetUserId)
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

    suspend fun sendFollowRequest(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || targetUserId == currentUserId) return
        ensureUserDocumentExists(currentUserId, isCurrentUser = true)
        ensureUserDocumentExists(targetUserId, isCurrentUser = false)

        val now = System.currentTimeMillis()
        val currentSnapshot = firestore.collection("users").document(currentUserId).get().await()
        val username = currentSnapshot.getString("username")
            ?: auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "usuario"
        val displayName = currentSnapshot.getString("displayName") ?: username
        val avatarUrl = currentSnapshot.getString("avatarUrl").orEmpty()
        val avatarBase64 = currentSnapshot.getString("avatarBase64").orEmpty()

        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(targetUserId)
                .collection("followRequests").document(currentUserId),
            mapOf(
                "requesterId" to currentUserId,
                "username" to username,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl,
                "avatarBase64" to avatarBase64,
                "timestamp" to now
            )
        )
        batch.set(
            firestore.collection("users").document(currentUserId)
                .collection("sentFollowRequests").document(targetUserId),
            mapOf(
                "targetUserId" to targetUserId,
                "timestamp" to now
            )
        )
        batch.commit().await()
    }

    suspend fun cancelFollowRequest(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("users").document(targetUserId)
                .collection("followRequests").document(currentUserId)
        )
        batch.delete(
            firestore.collection("users").document(currentUserId)
                .collection("sentFollowRequests").document(targetUserId)
        )
        batch.commit().await()
    }

    suspend fun acceptFollowRequest(requesterId: String) {
        if (currentUserId.isBlank() || requesterId.isBlank() || requesterId == currentUserId) return
        followUsersPair(requesterId = requesterId, targetUserId = currentUserId)
    }

    suspend fun rejectFollowRequest(requesterId: String) {
        if (currentUserId.isBlank() || requesterId.isBlank()) return
        val batch = firestore.batch()
        batch.delete(
            firestore.collection("users").document(currentUserId)
                .collection("followRequests").document(requesterId)
        )
        batch.delete(
            firestore.collection("users").document(requesterId)
                .collection("sentFollowRequests").document(currentUserId)
        )
        batch.commit().await()
    }

    suspend fun getIncomingFollowRequests(): List<SocialUserPreview> {
        if (currentUserId.isBlank()) return emptyList()
        val snapshot = firestore.collection("users")
            .document(currentUserId)
            .collection("followRequests")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val requesterId = doc.getString("requesterId") ?: doc.id
            SocialUserPreview(
                uid = requesterId,
                username = doc.getString("username") ?: "usuario",
                displayName = doc.getString("displayName") ?: doc.getString("username") ?: "Usuario",
                avatarUrl = doc.getString("avatarUrl").orEmpty(),
                avatarBase64 = doc.getString("avatarBase64").orEmpty()
            )
        }
    }

    suspend fun getIncomingFollowRequestsCount(): Int {
        if (currentUserId.isBlank()) return 0
        return firestore.collection("users")
            .document(currentUserId)
            .collection("followRequests")
            .get()
            .await()
            .size()
    }

    suspend fun getFollowingUsers(): List<SocialUserPreview> {
        if (currentUserId.isBlank()) return emptyList()
        val followingDocs = firestore.collection("users")
            .document(currentUserId)
            .collection("following")
            .get()
            .await()
            .documents
            .map { it.id }

        return loadUserPreviews(followingDocs)
    }

    suspend fun getCloseFriends(): List<SocialUserPreview> {
        if (currentUserId.isBlank()) return emptyList()
        val snapshot = firestore.collection("users").document(currentUserId).get().await()
        val ids = (snapshot.get("closeFriends") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        return loadUserPreviews(ids)
    }

    suspend fun addCloseFriend(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        firestore.collection("users").document(currentUserId)
            .set(mapOf("closeFriends" to FieldValue.arrayUnion(targetUserId)), SetOptions.merge())
            .await()
    }

    suspend fun removeCloseFriend(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        firestore.collection("users").document(currentUserId)
            .set(mapOf("closeFriends" to FieldValue.arrayRemove(targetUserId)), SetOptions.merge())
            .await()
    }

    suspend fun getBlockedUsers(): List<SocialUserPreview> {
        if (currentUserId.isBlank()) return emptyList()
        val snapshot = firestore.collection("users").document(currentUserId).get().await()
        val ids = (snapshot.get("blockedUsers") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        return loadUserPreviews(ids)
    }

    suspend fun blockUser(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank() || targetUserId == currentUserId) return
        ensureUserDocumentExists(currentUserId, isCurrentUser = true)
        ensureUserDocumentExists(targetUserId, isCurrentUser = false)

        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(currentUserId),
            mapOf(
                "blockedUsers" to FieldValue.arrayUnion(targetUserId),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )

        batch.delete(firestore.collection("users").document(currentUserId).collection("following").document(targetUserId))
        batch.delete(firestore.collection("users").document(currentUserId).collection("followers").document(targetUserId))
        batch.delete(firestore.collection("users").document(targetUserId).collection("following").document(currentUserId))
        batch.delete(firestore.collection("users").document(targetUserId).collection("followers").document(currentUserId))
        batch.delete(firestore.collection("users").document(targetUserId).collection("followRequests").document(currentUserId))
        batch.delete(firestore.collection("users").document(currentUserId).collection("followRequests").document(targetUserId))
        batch.delete(firestore.collection("users").document(currentUserId).collection("sentFollowRequests").document(targetUserId))
        batch.delete(firestore.collection("users").document(targetUserId).collection("sentFollowRequests").document(currentUserId))
        batch.commit().await()
    }

    suspend fun unblockUser(targetUserId: String) {
        if (currentUserId.isBlank() || targetUserId.isBlank()) return
        firestore.collection("users").document(currentUserId)
            .set(mapOf("blockedUsers" to FieldValue.arrayRemove(targetUserId)), SetOptions.merge())
            .await()
    }

    suspend fun isFollowing(targetUserId: String): Boolean {
        return getRelationshipState(targetUserId).isFollowing
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

    private suspend fun followUsersPair(requesterId: String, targetUserId: String) {
        ensureUserDocumentExists(requesterId, isCurrentUser = requesterId == currentUserId)
        ensureUserDocumentExists(targetUserId, isCurrentUser = targetUserId == currentUserId)

        val now = System.currentTimeMillis()
        val batch = firestore.batch()
        batch.set(
            firestore.collection("users").document(requesterId).collection("following").document(targetUserId),
            mapOf("timestamp" to now)
        )
        batch.set(
            firestore.collection("users").document(targetUserId).collection("followers").document(requesterId),
            mapOf("timestamp" to now)
        )
        batch.set(
            firestore.collection("users").document(requesterId),
            mapOf("followingCount" to FieldValue.increment(1), "updatedAt" to now),
            SetOptions.merge()
        )
        batch.set(
            firestore.collection("users").document(targetUserId),
            mapOf("followersCount" to FieldValue.increment(1), "updatedAt" to now),
            SetOptions.merge()
        )
        batch.delete(
            firestore.collection("users").document(targetUserId)
                .collection("followRequests").document(requesterId)
        )
        batch.delete(
            firestore.collection("users").document(requesterId)
                .collection("sentFollowRequests").document(targetUserId)
        )
        batch.commit().await()
    }

    private suspend fun loadUserPreviews(ids: List<String>): List<SocialUserPreview> {
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { userId ->
            val snapshot = firestore.collection("users").document(userId).get().await()
            if (!snapshot.exists()) return@mapNotNull null
            SocialUserPreview(
                uid = userId,
                username = snapshot.getString("username") ?: "usuario",
                displayName = snapshot.getString("displayName") ?: snapshot.getString("username") ?: "Usuario",
                avatarUrl = snapshot.getString("avatarUrl").orEmpty(),
                avatarBase64 = snapshot.getString("avatarBase64").orEmpty()
            )
        }
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
                "closeFriends" to emptyList<String>(),
                "blockedUsers" to emptyList<String>(),
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }
}

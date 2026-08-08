package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.storage.StorageProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccountRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider
) {
    suspend fun closeAccountAndPurgeData(targetUserId: String): Boolean {
        if (targetUserId.isBlank()) return false
        return try {
            val postsSnapshot = firestore.collection("posts")
                .whereEqualTo("userId", targetUserId)
                .get()
                .await()
            for (doc in postsSnapshot.documents) {
                val storageKey = doc.getString("storageKey")
                if (!storageKey.isNullOrBlank()) {
                    try { storage.deleteFile(storageKey) } catch (_: Exception) {}
                }
                doc.reference.delete().await()
            }
            firestore.collection("users").document(targetUserId).delete().await()
            true
        } catch (e: Exception) {
            false
        }
    }
}

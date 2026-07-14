package com.vivid.app.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object UserPresenceHelper {
    private const val TAG = "UserPresenceHelper"

    fun setOnline() {
        updatePresence(true)
    }

    fun setOffline() {
        updatePresence(false)
    }

    private fun updatePresence(isOnline: Boolean) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(
                mapOf(
                    "isOnline" to isOnline,
                    "lastActiveAt" to now
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e ->
                Log.w(TAG, "No se pudo actualizar presencia a $isOnline", e)
            }
    }
}

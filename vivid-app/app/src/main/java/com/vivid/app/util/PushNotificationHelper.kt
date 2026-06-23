package com.vivid.app.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Helper para registrar el token FCM del dispositivo en Firestore.
 *
 * La Cloud Function `onReelLike`, `onReelComment`, `onFollow`
 * busca tokens en /users/{uid}/fcmTokens para enviar pushes.
 *
 * Llamar a `registerTokenForCurrentUser()` al iniciar sesion.
 */
object PushNotificationHelper {

    private const val TAG = "PushHelper"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /**
     * Suscribe el dispositivo actual a push notifications.
     * Llamar tras login.
     */
    fun registerTokenForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM token obtained: ${token.take(20)}...")

                db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .document(token)
                    .set(mapOf(
                        "createdAt" to System.currentTimeMillis(),
                        "platform" to "android",
                        "appVersion" to "2.0.0"
                    ))
                Log.d(TAG, "Token registrado para usuario $uid")
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando token", e)
            }
        }
    }

    /**
     * Elimina el token al cerrar sesion.
     */
    fun unregisterToken() {
        val uid = auth.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                db.collection("users").document(uid)
                    .collection("fcmTokens").document(token).delete()
                Log.d(TAG, "Token eliminado para usuario $uid")
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando token", e)
            }
        }
    }
}

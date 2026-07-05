package com.vivid.app.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Helper para registrar el token FCM del dispositivo en Firestore.
 *
 * La Cloud Function `onReelLike`, `onReelComment`, `onFollow`, `onMessageCreated`
 * busca tokens en /users/{uid}/fcmTokens para enviar pushes.
 *
 * Llamar a `registerTokenForCurrentUser()` al iniciar sesión.
 */
object PushNotificationHelper {

    private const val TAG = "PushHelper"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Suscribe el dispositivo actual a push notifications.
     * Llamar tras login o cuando el token FCM se renueva (onNewToken).
     */
    fun registerTokenForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return

        // Limpiar tokens viejos de este dispositivo (por si acaso)
        scope.launch {
            try {
                val oldTokens = db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .whereEqualTo("platform", "android")
                    .get().await()

                // Borrar tokens antiguos excepto el actual
                val currentToken = FirebaseMessaging.getInstance().token.await()
                for (doc in oldTokens.documents) {
                    if (doc.id != currentToken) {
                        doc.reference.delete()
                        Log.d(TAG, "Token antiguo eliminado: ${doc.id.take(20)}...")
                    }
                }

                // Registrar el token actual
                db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .document(currentToken)
                    .set(mapOf(
                        "createdAt" to System.currentTimeMillis(),
                        "platform" to "android",
                        "appVersion" to "2.0.0"
                    ))
                    .await()

                Log.d(TAG, "Token FCM registrado para $uid: ${currentToken.take(20)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando token FCM", e)
            }
        }
    }

    /**
     * Elimina el token al cerrar sesión.
     */
    fun unregisterToken() {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .document(token)
                    .delete()
                    .await()
                Log.d(TAG, "Token eliminado para usuario $uid")
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando token", e)
            }
        }
    }
}

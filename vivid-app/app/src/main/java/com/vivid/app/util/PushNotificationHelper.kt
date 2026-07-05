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
 * Flujo:
 *   1. Al iniciar sesión → registerTokenForCurrentUser()
 *   2. Guarda el token en /users/{uid}/fcmTokens/{token}
 *   3. Las Cloud Functions (onReelLike, onMessageCreated, etc.)
 *      leen estos tokens para enviar pushes FCM.
 *
 * ⚠️  Para que las notificaciones funcionen, las Cloud Functions DEBEN
 *     estar desplegadas en Firebase. Sin ellas, nadie envía los pushes.
 */
object PushNotificationHelper {

    private const val TAG = "PushHelper"
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Suscribe el dispositivo actual a push notifications.
     * Se llama desde MainActivity.addAuthStateListener (cada login)
     * y desde VividMessagingService.onNewToken (cuando FCM renueva el token).
     */
    fun registerTokenForCurrentUser() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "registerTokenForCurrentUser: no hay usuario autenticado, abortando")
            return
        }

        scope.launch {
            try {
                // 1. Obtener el token FCM actual
                val token = FirebaseMessaging.getInstance().token.await()
                Log.i(TAG, "✅ Token FCM obtenido: ${token.take(25)}...")

                // 2. Limpiar tokens viejos de este dispositivo (evita duplicados)
                val oldTokens = db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .whereEqualTo("platform", "android")
                    .get().await()

                for (doc in oldTokens.documents) {
                    if (doc.id != token) {
                        doc.reference.delete().await()
                        Log.d(TAG, "🗑️ Token antiguo eliminado: ${doc.id.take(20)}...")
                    }
                }

                // 3. Guardar el token actual en Firestore
                db.collection("users").document(uid)
                    .collection("fcmTokens")
                    .document(token)
                    .set(mapOf(
                        "createdAt" to System.currentTimeMillis(),
                        "platform" to "android",
                        "appVersion" to "2.0.0"
                    ))
                    .await()

                Log.i(TAG, "🎉 Token FCM registrado exitosamente para uid=$uid")
                Log.i(TAG, "   Ruta: /users/$uid/fcmTokens/${token.take(20)}...")

            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR registrando token FCM: ${e.javaClass.simpleName}", e)
                Log.e(TAG, "   ¿Están las Firestore Rules desplegadas con fcmTokens?")
                Log.e(TAG, "   ¿Tiene el usuario permisos de red?")
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
                Log.i(TAG, "Token eliminado para usuario $uid")
            } catch (e: Exception) {
                Log.e(TAG, "Error eliminando token", e)
            }
        }
    }
}

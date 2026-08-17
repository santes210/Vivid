package com.vivid.app.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Helper para registrar el token FCM del dispositivo en Firestore.
 *
 * Flujo:
 *   1. Al iniciar sesión → registerTokenForCurrentUser()
 *   2. Guarda el token en /users/{uid}/fcmTokens/{token}
 *   3. El Cloudflare Worker valida la acción y lee estos tokens para enviar FCM.
 *
 * Para que las notificaciones funcionen, PUSH_WORKER_URL debe estar configurada
 * en el build y el Worker debe tener su service account como secret.
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
     *
     * FIX: cuando FCM renueva el token (típico tras reinstalar la app), el
     * proceso arranca en frío y FirebaseAuth restaura la sesión de forma
     * ASÍNCRONA: currentUser puede ser null al principio. Antes esto hacía que
     * el token NUEVO nunca se registrara y las notificaciones dejaran de
     * llegar (el Worker solo encuentra tokens viejos/inválidos). Ahora se
     * espera a la sesión (máx 3s) antes de registrarlo.
     */
    fun registerTokenForCurrentUser() {
        scope.launch {
            val uid = auth.currentUser?.uid
                ?: awaitCurrentUser(timeoutMs = 3_000)?.uid
                ?: run {
                    Log.w(TAG, "registerTokenForCurrentUser: sin sesión tras espera, abortando")
                    return@launch
                }

            try {
                // 1. Obtener el token FCM actual
                val token = FirebaseMessaging.getInstance().token.await()
                Log.i(TAG, "✅ Token FCM obtenido: ${token.take(25)}...")

                // Se conservan los tokens de otros dispositivos. El Worker elimina
                // únicamente los que FCM marque como inválidos o no registrados.

                // 2. Guardar el token actual en Firestore
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

    /**
     * FirebaseAuth restaura el usuario de forma asíncrona al arrancar el proceso.
     * Espera (con tope de tiempo) hasta que currentUser esté disponible.
     */
    private suspend fun awaitCurrentUser(timeoutMs: Long): FirebaseUser? {
        val auth = FirebaseAuth.getInstance()
        auth.currentUser?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                // Objeto anónimo (no SAM lambda): dentro podemos usar `this`
                // para auto-remover el listener sin referenciar la variable
                // antes de inicializarla (compila siempre).
                val listener = object : FirebaseAuth.AuthStateListener {
                    override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                        firebaseAuth.currentUser?.let { user ->
                            firebaseAuth.removeAuthStateListener(this)
                            cont.resume(user) {}
                        }
                    }
                }
                auth.addAuthStateListener(listener)
                cont.invokeOnCancellation { auth.removeAuthStateListener(listener) }
            }
        }
    }
}

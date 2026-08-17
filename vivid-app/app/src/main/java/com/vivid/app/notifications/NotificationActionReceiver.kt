package com.vivid.app.notifications

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var chatRepository: ChatRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ── TOPE DURO anti-ANR ────────────────────────────────────────
                // La ventana del sistema para un BroadcastReceiver es ~10s. Aquí
                // todo el trabajo (espera de sesión + Firestore) queda acotado a
                // 8s, así pendingResult.finish() SIEMPRE se llama a tiempo y la
                // app nunca muestra "Vivid isn't responding" (ANR).
                withTimeoutOrNull(WORK_TIMEOUT_MS) {
                    // ── FIX: esperar la sesión ────────────────────────────────
                    // Al tocar una acción de notificación, Android puede arrancar
                    // el proceso desde cero. FirebaseAuth restaura el usuario de
                    // forma ASÍNCRONA, así que currentUser puede ser null al
                    // principio y sendMessage()/markMessagesAsRead() regresaban
                    // sin hacer nada.
                    val user = awaitCurrentUser(timeoutMs = AUTH_WAIT_MS)
                    if (user == null) {
                        Log.w(TAG, "Sin usuario autenticado; se ignora la acción ${intent.action}")
                        return@withTimeoutOrNull
                    }

                    when (intent.action) {
                        ACTION_MARK_READ -> handleMarkAsRead(context, intent)
                        ACTION_REPLY -> handleReply(context, intent)
                        else -> Log.w(TAG, "Acción desconocida: ${intent.action}")
                    }
                }
            } catch (t: Throwable) {
                // Throwable (no solo Exception): aunque algo raro ocurra, finish()
                // se llama en finally y no hay ANR.
                Log.w(TAG, "Error procesando acción ${intent.action}", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Marca el chat como leído y retira la notificación. */
    private suspend fun handleMarkAsRead(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        if (chatId.isBlank()) {
            Log.w(TAG, "MarkAsRead sin chatId")
            return
        }

        chatRepository.markChatAsRead(chatId)
        dismissNotification(context, chatId)
        Log.i(TAG, "Chat $chatId marcado como leído")
    }

    /** Envía la respuesta y deja el chat como leído. */
    private suspend fun handleReply(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID) ?: return
        if (chatId.isBlank() || receiverId.isBlank()) {
            Log.w(TAG, "Reply incompleta (chatId=$chatId, receiverId=$receiverId)")
            return
        }

        val results = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getString(KEY_REPLY_TEXT)?.trim() ?: return
        if (replyText.isBlank()) {
            Log.w(TAG, "Reply sin texto")
            return
        }

        chatRepository.sendMessage(chatId, replyText, receiverId)
        // Responder también marca el chat como leído (comportamiento estándar).
        chatRepository.markChatAsRead(chatId)
        dismissNotification(context, chatId)
        Log.i(TAG, "Respuesta enviada a chat $chatId")
    }

    private fun dismissNotification(context: Context, chatId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(chatId.hashCode())
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
                            // Overload con onCancellation: si el timeout ya canceló la
                            // continuación, no lanza excepción (carrera inofensiva).
                            cont.resume(user) {}
                        }
                    }
                }
                auth.addAuthStateListener(listener)
                cont.invokeOnCancellation { auth.removeAuthStateListener(listener) }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"

        /** Tope total del trabajo del receiver: < ventana de 10s del sistema. */
        private const val WORK_TIMEOUT_MS = 8_000L

        /** Espera máxima a que FirebaseAuth restaure la sesión (suele ser < 1s). */
        private const val AUTH_WAIT_MS = 3_000L

        const val ACTION_REPLY = "com.vivid.app.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.vivid.app.ACTION_MARK_READ"

        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_RECEIVER_ID = "receiverId"
        const val KEY_REPLY_TEXT = "reply_text"
    }
}

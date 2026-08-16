package com.vivid.app.notifications

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vivid.app.domain.repository.ChatRepository
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var chatRepository: ChatRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MARK_READ -> handleMarkAsRead(intent)
                    ACTION_REPLY -> handleReply(intent)
                }
            } catch (e: Exception) {
                // La acción falló silenciosamente — no mostrarle un error al usuario
                // porque él ya cerró la notificación y no espera una respuesta.
                android.util.Log.w("NotificationAction", "Error procesando acción", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Marca todos los mensajes del chat como leídos.
     * El chatId viene en los extras del Intent.
     */
    private suspend fun handleMarkAsRead(intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        if (chatId.isBlank()) return
        chatRepository.markMessagesAsRead(chatId)
    }

    /**
     * Responde al mensaje desde la notificación.
     * El texto viene del RemoteInput, y el receptor es el fromUserId
     * (el usuario que envió el mensaje original, que recibirá la respuesta).
     */
    private suspend fun handleReply(intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: return
        val receiverId = intent.getStringExtra(EXTRA_RECEIVER_ID) ?: return
        if (chatId.isBlank() || receiverId.isBlank()) return

        val results = RemoteInput.getResultsFromIntent(intent)
        val replyText = results?.getString(KEY_REPLY_TEXT)?.trim() ?: return
        if (replyText.isBlank()) return

        chatRepository.sendMessage(chatId, replyText, receiverId)
    }

    companion object {
        const val ACTION_REPLY = "com.vivid.app.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.vivid.app.ACTION_MARK_READ"

        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_RECEIVER_ID = "receiverId"
        const val KEY_REPLY_TEXT = "reply_text"
    }
}
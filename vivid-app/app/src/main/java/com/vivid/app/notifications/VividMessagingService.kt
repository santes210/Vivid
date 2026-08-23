package com.vivid.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vivid.app.MainActivity
import com.vivid.app.R

class VividMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "VividMessaging"
    }

    /**
     * Tinte de marca para [NotificationCompat.Builder.setColor]: el sistema
     * tiñe el icono pequeño con este color en la shade y en las acciones.
     *
     * No es una constante Kotlin de la paleta (VividBrandColors) porque las
     * notificaciones se construyen FUERA de la composición de Compose y
     * tienen que seguir el tema CLARO/OSCURO DEL SISTEMA (el de la shade),
     * no la preferencia interna de la app: por eso se resuelve desde el
     * recurso [R.color.notification_accent], que values-night sobreescribe
     * al rosa del esquema oscuro (coral #B71454 en light, rosa #FFAFC1 en
     * dark). El resultado coincide con brandPrimary en cada modo.
     */
    private fun brandAccentColor(): Int =
        ContextCompat.getColor(this, R.color.notification_accent)

    /**
     * Metadatos de **Bubbles** para que un mensaje DM pueda expandirse a
     * una burbuja y responder sin salir de la app que el usuario esté
     * usando. Al expandir la burbuja se abre el chat ([openChatPendingIntent]
     * es el mismo intent de la notificación); al deslizarla fuera se cierra
     * en silencio y la notificación queda en la shade, igual que en apps de
     * mensajería estándar.
     *
     * Solo se aplica en mensajes (task 38): las notificaciones de likes /
     * seguidores / reels no son conversaciones y burbujearlas sería ruido.
     * En API < 29 el sistema ignora los metadatos (el compat lo gestiona).
     */
    private fun bubbleMetadataForChat(openChatPendingIntent: PendingIntent): NotificationCompat.BubbleMetadata =
        NotificationCompat.BubbleMetadata.Builder(
            openChatPendingIntent,
            IconCompat.createWithResource(this, R.drawable.ic_notification_bell)
        ).build()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // El Worker envía mensajes data-only para que este servicio controle
        // igual el comportamiento en primer y segundo plano.
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Vivid"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "Nueva notificación"
        val type = remoteMessage.data["type"] ?: "general"

        try {
            val chatId = remoteMessage.data["chatId"]
            val reelId = remoteMessage.data["reelId"]
            val postId = remoteMessage.data["postId"]
            val fromUserId = remoteMessage.data["fromUserId"]

            when (type) {
                "message" -> {
                    markMessageDelivered(chatId, remoteMessage.data["messageId"])
                    showMessageNotification(title, body, chatId, fromUserId)
                }
                "reel_like",
                "reel_comment" -> showReelNotification(title, body, reelId)
                "post_like",
                "post_comment" -> showPostNotification(title, body, postId)
                "new_follower",
                "follow_request" -> showFollowerNotification(title, body, fromUserId)
                else -> showGeneralNotification(title, body)
            }
        } catch (e: Exception) {
            // NUNCA dejar que una notificación tire el proceso: si algo falla al
            // construir la notificación específica, se muestra una genérica. Si el
            // proceso muriera aquí, el mensaje FCM se perdería y el usuario vería
            // "Vivid se detuvo" + notificaciones que nunca llegan.
            Log.w(TAG, "Error construyendo notificación type=$type", e)
            runCatching { showGeneralNotification(title, body) }
        }
    }

    override fun onNewToken(token: String) {
        // Se llama cuando el token FCM cambia; delegamos el registro al PushNotificationHelper
        super.onNewToken(token)
        com.vivid.app.util.PushNotificationHelper.registerTokenForCurrentUser()
    }

    /**
     * Recibo de entrega: el dispositivo recibió el push. No marca leído.
     * Best-effort; si falta messageId o no hay sesión, no hace nada.
     */
    private fun markMessageDelivered(chatId: String?, messageId: String?) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (chatId.isNullOrBlank() || messageId.isNullOrBlank()) return
        runCatching {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("isDelivered", true)
        }
        Log.d(TAG, "delivery receipt queued chat=$chatId msg=$messageId uid=$uid")
    }

    private fun showMessageNotification(
        title: String,
        body: String,
        chatId: String?,
        fromUserId: String?
    ) {
        val safeChatId = chatId ?: ""
        val safeFromUserId = fromUserId ?: ""

        // Intent principal: abrir el chat al tocar la notificación
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openChat", true)
            putExtra("chatId", safeChatId)
        }
        val requestCode = (safeChatId.ifBlank { "msg" }).hashCode()
        val openChatPendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            // Tinte de marca: el sistema tiñe la campana de Vivid con el
            // primary de la app (coral en light, rosa en dark).
            .setColor(brandAccentColor())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openChatPendingIntent)
            // Bubbles: el DM puede expandirse a burbuja para chatear sin
            // salir de otra app (ver bubbleMetadataForChat).
            .setBubbleMetadata(bubbleMetadataForChat(openChatPendingIntent))

        // ── Acciones opcionales ──────────────────────────────────────────
        // Se construyen aisladas: si algo falla aquí, la notificación base
        // (abrir chat) se muestra igual. Así un error en las acciones NUNCA
        // puede impedir que la notificación llegue al usuario.
        if (safeChatId.isNotBlank() && safeFromUserId.isNotBlank()) {
            runCatching {
                // Acción: Marcar como leído (broadcast al receiver)
                val markReadIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_MARK_READ
                    putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, safeChatId)
                }
                val markReadPendingIntent = PendingIntent.getBroadcast(
                    this,
                    requestCode xor 0x1000, // requestCode diferente para evitar colisión
                    markReadIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(R.drawable.ic_notification_bell, "Marcar leído", markReadPendingIntent)

                // Acción: Responder (broadcast + RemoteInput al receiver)
                val replyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_REPLY
                    putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, safeChatId)
                    putExtra(NotificationActionReceiver.EXTRA_RECEIVER_ID, safeFromUserId)
                }
                // FIX: en API 31+ un broadcast con RemoteInput DEBE ser
                // FLAG_MUTABLE: el sistema necesita modificar el intent para
                // adjuntar el texto escrito por el usuario. Con
                // FLAG_IMMUTABLE la respuesta nunca llegaba al receiver y la
                // acción "Responder" parecía muerta. En < 31 el flag de
                // mutabilidad no existe y se ignora.
                val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val replyPendingIntent = PendingIntent.getBroadcast(
                    this,
                    requestCode xor 0x2000, // requestCode diferente
                    replyIntent,
                    replyFlags or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
                    .setLabel("Responder")
                    .build()
                val replyAction = NotificationCompat.Action.Builder(
                    R.drawable.ic_notification_bell,
                    "Responder",
                    replyPendingIntent
                )
                    .addRemoteInput(remoteInput)
                    .build()
                builder.addAction(replyAction)
            }.onFailure { e ->
                Log.w(TAG, "No se pudieron añadir acciones a la notificación de mensaje", e)
            }
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, builder.build())
    }

    private fun showReelNotification(title: String, body: String, reelId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openReel", true)
            putExtra("reelId", reelId ?: "")
        }

        val requestCode = (reelId ?: "reel").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "general_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(brandAccentColor())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
    }

    private fun showPostNotification(title: String, body: String, postId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openPost", true)
            putExtra("postId", postId ?: "")
        }
        val requestCode = (postId ?: "post").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, "general_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(brandAccentColor())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(requestCode, notification)
    }

    private fun showFollowerNotification(title: String, body: String, fromUserId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openProfile", true)
            putExtra("profileUserId", fromUserId ?: "")
        }

        val requestCode = (fromUserId ?: "follower").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "general_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(brandAccentColor())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
    }

    private fun showGeneralNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "general_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(brandAccentColor())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "messages_channel",
                    "Mensajes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notificaciones de mensajes directos"
                    // Bubbles: explícito por si el usuario tuvo una versión
                    // anterior con otro valor (es sticky en el channel).
                    setAllowBubbles(true)
                },
                NotificationChannel(
                    "general_channel",
                    "General",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Likes, comentarios, seguidores y otras notificaciones"
                }
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }
}

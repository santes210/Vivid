package com.vivid.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vivid.app.MainActivity
import com.vivid.app.R

class VividMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // El Worker envía mensajes data-only para que este servicio controle
        // igual el comportamiento en primer y segundo plano.
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Vivid"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: "Nueva notificación"
        val type = remoteMessage.data["type"] ?: "general"
        val chatId = remoteMessage.data["chatId"]
        val reelId = remoteMessage.data["reelId"]
        val postId = remoteMessage.data["postId"]
        val fromUserId = remoteMessage.data["fromUserId"]

        when (type) {
            "message" -> showMessageNotification(title, body, chatId, fromUserId)
            "reel_like",
            "reel_comment" -> showReelNotification(title, body, reelId)
            "post_like",
            "post_comment" -> showPostNotification(title, body, postId)
            "new_follower",
            "follow_request" -> showFollowerNotification(title, body, fromUserId)
            else -> showGeneralNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        // Se llama cuando el token FCM cambia; delegamos el registro al PushNotificationHelper
        super.onNewToken(token)
        com.vivid.app.util.PushNotificationHelper.registerTokenForCurrentUser()
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

        // --- Acción: Marcar como leído ---
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

        // --- Acción: Responder (con RemoteInput) ---
        val replyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_CHAT_ID, safeChatId)
            putExtra(NotificationActionReceiver.EXTRA_RECEIVER_ID, safeFromUserId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode xor 0x2000, // requestCode diferente
            replyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel("Responder")
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_notification_bell, // fallback; Android muestra el ícono de respuesta automáticamente
            "Responder",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        val notification = NotificationCompat.Builder(this, "messages_channel")
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openChatPendingIntent)
            .addAction(R.drawable.ic_notification_bell, "Marcar leído", markReadPendingIntent)
            .addAction(replyAction)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
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

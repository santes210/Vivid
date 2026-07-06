package com.vivid.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.MainActivity
import com.vivid.app.R
import com.vivid.app.util.LocalNotificationWatcher

/**
 * Foreground Service que mantiene vivo el watcher de notificaciones.
 * 
 * Con un Foreground Service, Android le da prioridad alta al proceso
 * y es mucho menos probable que lo mate. La notificación persistente
 * en la barra de estado es el "precio" por esta garantía.
 * 
 * Compatible con Shizuku para whitelisting adicional de batería.
 */
class NotificationForegroundService : Service() {

    companion object {
        private const val TAG = "NotifFgService"
        const val CHANNEL_ID = "vivid_foreground_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.vivid.app.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, NotificationForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "Foreground service iniciado")
        }

        fun stop(context: Context) {
            val intent = Intent(context, NotificationForegroundService::class.java)
            context.stopService(intent)
            Log.i(TAG, "Foreground service detenido")
        }

        fun isRunning(): Boolean = running
        private var running = false
    }

    private var running = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Iniciar el watcher de notificaciones locales
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            Log.i(TAG, "Iniciando LocalNotificationWatcher desde foreground service...")
            LocalNotificationWatcher.start(applicationContext)

            // Intentar whitelist de batería vía Shizuku (si está disponible)
            com.vivid.app.util.ShizukuBatteryHelper.tryWhitelist(applicationContext)
        } else {
            Log.w(TAG, "Sin usuario autenticado, no se inicia watcher")
        }

        return START_STICKY  // Reiniciar si Android lo mata
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        LocalNotificationWatcher.stop()
        Log.i(TAG, "Foreground service destruido")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vivid en segundo plano",
                NotificationManager.IMPORTANCE_LOW  // LOW = sin sonido, solo icono
            ).apply {
                description = "Mantiene Vivid activo para recibir notificaciones. Puedes ocultar esta notificación desde Ajustes > Apps > Vivid."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, NotificationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle("Vivid")
            .setContentText("Escuchando notificaciones...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending)
            .build()
    }
}

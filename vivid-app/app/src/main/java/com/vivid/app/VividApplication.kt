package com.vivid.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import com.vivid.app.util.SettingsManager
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        createNotificationChannels()
    }

    override fun newImageLoader(): ImageLoader = imageLoader

    /**
     * Crea los canales usados por [com.vivid.app.notifications.VividMessagingService]
     * y [com.vivid.app.util.LocalNotificationWatcher].
     *
     * Sin estos canales, en Android 8+ el sistema DESCARTA silenciosamente
     * cualquier notificación publicada en un canal inexistente.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val messagesChannel = NotificationChannel(
            VIVID_MESSAGES_CHANNEL,
            "Mensajes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones de mensajes directos"
        }

        val generalChannel = NotificationChannel(
            VIVID_GENERAL_CHANNEL,
            "Actividad de Vivid",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Me gusta, comentarios y nuevos seguidores"
        }

        manager.createNotificationChannels(listOf(messagesChannel, generalChannel))
    }

    companion object {
        const val VIVID_MESSAGES_CHANNEL = "messages_channel"
        const val VIVID_GENERAL_CHANNEL = "general_channel"
    }
}

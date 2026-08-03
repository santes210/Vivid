package com.vivid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.navigation.VividNavigation
import com.vivid.app.notifications.NotificationForegroundService
import com.vivid.app.theme.VividTheme
import com.vivid.app.util.DeepLinkBus
import com.vivid.app.util.PushNotificationHelper
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.UserPresenceHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readNotificationExtras(intent)

        // ── Auth listener: token FCM + Foreground Service para notificaciones ──
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            if (auth.currentUser != null) {
                // Token FCM (para cuando actives Blaze en el futuro)
                PushNotificationHelper.registerTokenForCurrentUser()

                // Iniciar Foreground Service que mantiene vivo el watcher de notificaciones
                // + intenta whitelist de batería vía Shizuku si está disponible
                NotificationForegroundService.start(applicationContext)
            } else {
                NotificationForegroundService.stop(applicationContext)
            }
        }

        // Permiso de notificaciones Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }

        setContent {
            val selectedTheme = SettingsManager.selectedThemeOption
            val dynamicColor = SettingsManager.dynamicColorEnabled
            val darkTheme = when (selectedTheme) {
                "Oscuro" -> true
                "Claro" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            VividTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VividApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (SettingsManager.activityStatusEnabled) {
            UserPresenceHelper.setOnline()
        }
    }

    override fun onStop() {
        if (SettingsManager.activityStatusEnabled) {
            UserPresenceHelper.setOffline()
        }
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readNotificationExtras(intent)
    }

    private fun readNotificationExtras(intent: android.content.Intent) {
        // Publica los deep links en el bus reactivo. Así también llegan
        // cuando la app ya estaba abierta (onNewIntent), no solo en frío.
        if (intent.getBooleanExtra("openChat", false)) {
            intent.getStringExtra("chatId")
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLinkBus.emitChat(it) }
        }
        if (intent.getBooleanExtra("openReel", false)) {
            intent.getStringExtra("reelId")
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLinkBus.emitReel(it) }
        }
        if (intent.getBooleanExtra("openProfile", false)) {
            intent.getStringExtra("profileUserId")
                ?.takeIf { it.isNotBlank() }
                ?.let { DeepLinkBus.emitProfile(it) }
        }
    }
}

@Composable
fun VividApp() {
    val navController = rememberNavController()
    // Deep links reactivos: cualquier evento nuevo re-dispara la navegación.
    val deepLinkChatId by DeepLinkBus.chatId.collectAsState()
    val deepLinkReelId by DeepLinkBus.reelId.collectAsState()
    val deepLinkProfileUserId by DeepLinkBus.profileUserId.collectAsState()
    VividNavigation(
        navController = navController,
        deepLinkChatId = deepLinkChatId,
        deepLinkReelId = deepLinkReelId,
        deepLinkProfileUserId = deepLinkProfileUserId
    )
}

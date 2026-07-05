package com.vivid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.navigation.VividNavigation
import com.vivid.app.theme.VividTheme
import com.vivid.app.util.LocalNotificationWatcher
import com.vivid.app.util.PushNotificationHelper
import com.vivid.app.util.SettingsManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingChatId: String? = null
    private var pendingReelId: String? = null
    private var pendingProfileUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readNotificationExtras(intent)

        // ── Auth listener: registra token FCM + inicia watcher de notificaciones locales ──
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                // Registrar token FCM para cuando actives Blaze en el futuro
                PushNotificationHelper.registerTokenForCurrentUser()

                // Iniciar watcher de notificaciones locales (sin Cloud Functions)
                LocalNotificationWatcher.start(applicationContext)
            } else {
                LocalNotificationWatcher.stop()
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

            val deepLinkChatId = remember { pendingChatId }
            val deepLinkReelId = remember { pendingReelId }
            val deepLinkProfileUserId = remember { pendingProfileUserId }

            VividTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VividApp(
                        deepLinkChatId = deepLinkChatId,
                        deepLinkReelId = deepLinkReelId,
                        deepLinkProfileUserId = deepLinkProfileUserId
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readNotificationExtras(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalNotificationWatcher.stop()
    }

    private fun readNotificationExtras(intent: android.content.Intent) {
        if (intent.getBooleanExtra("openChat", false)) {
            pendingChatId = intent.getStringExtra("chatId")
        }
        if (intent.getBooleanExtra("openReel", false)) {
            pendingReelId = intent.getStringExtra("reelId")
        }
        if (intent.getBooleanExtra("openProfile", false)) {
            pendingProfileUserId = intent.getStringExtra("profileUserId")
        }
    }
}

@Composable
fun VividApp(
    deepLinkChatId: String? = null,
    deepLinkReelId: String? = null,
    deepLinkProfileUserId: String? = null
) {
    val navController = rememberNavController()
    VividNavigation(
        navController = navController,
        deepLinkChatId = deepLinkChatId,
        deepLinkReelId = deepLinkReelId,
        deepLinkProfileUserId = deepLinkProfileUserId
    )
}

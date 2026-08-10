package com.vivid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.navigation.VividNavigation
import com.vivid.app.notifications.NotificationForegroundService
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividTheme
import com.vivid.app.util.PushNotificationHelper
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.UserPresenceHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingChatId: String? = null
    private var pendingReelId: String? = null
    private var pendingProfileUserId: String? = null
    private var pendingPostId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge-to-edge real: barras transparentes, contenido detrás cuando es seguro
        enableEdgeToEdge()

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
            val animationsEnabled = SettingsManager.smoothAnimationsEnabled
            val darkTheme = when (selectedTheme) {
                "Oscuro" -> true
                "Claro" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val deepLinkChatId = remember { pendingChatId }
            val deepLinkReelId = remember { pendingReelId }
            val deepLinkProfileUserId = remember { pendingProfileUserId }
            val deepLinkPostId = remember { pendingPostId }

            VividTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                CompositionLocalProvider(
                    LocalVividAnimationsEnabled provides animationsEnabled
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        VividApp(
                            deepLinkChatId = deepLinkChatId,
                            deepLinkReelId = deepLinkReelId,
                            deepLinkProfileUserId = deepLinkProfileUserId,
                            deepLinkPostId = deepLinkPostId
                        )
                    }
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
        // Deep links Material You 3: vivid://post/xxx
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "vivid" && data.host == "post") {
                val path = data.path ?: ""
                val postId = path.removePrefix("/")
                if (postId.isNotBlank()) pendingPostId = postId
            }
        }
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
    deepLinkProfileUserId: String? = null,
    deepLinkPostId: String? = null
) {
    val navController = rememberNavController()
    VividNavigation(
        navController = navController,
        deepLinkChatId = deepLinkChatId,
        deepLinkReelId = deepLinkReelId,
        deepLinkProfileUserId = deepLinkProfileUserId,
        deepLinkPostId = deepLinkPostId
    )
}

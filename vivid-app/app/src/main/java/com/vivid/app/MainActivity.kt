package com.vivid.app

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.ensureCurrentUserContentPrivacy
import com.vivid.app.navigation.VividNavigation
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividTheme
import com.vivid.app.util.LocaleManager
import com.vivid.app.util.PushNotificationHelper
import com.vivid.app.util.SettingsManager
import com.vivid.app.util.UserPresenceHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingChatId: String? by mutableStateOf(null)
    private var pendingReelId: String? by mutableStateOf(null)
    private var pendingProfileUserId: String? by mutableStateOf(null)
    private var pendingPostId: String? by mutableStateOf(null)

    // ── Auth listener: registra el token FCM de la sesión ──
    // Guardado como campo para poder removerlo en onDestroy(): un listener
    // registrado y nunca removido retiene la Activity y filtra eventos
    // después de que la pantalla murió.
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        if (auth.currentUser != null) {
            val userId = auth.currentUser?.uid.orEmpty()
            lifecycleScope.launch {
                runCatching {
                    ensureCurrentUserContentPrivacy(FirebaseFirestore.getInstance(), userId)
                }
            }

            PushNotificationHelper.registerTokenForCurrentUser()
        }
    }

    /**
     * Aplica el idioma y tamaño de fuente elegidos en Ajustes ANTES de que
     * se cree la vista. Sin esto, los recursos se sirven en el idioma del
     * sistema aunque el usuario haya elegido otro, y los strings de
     * stringResource() salen en el idioma equivocado.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyToActivity(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Edge-to-edge real: barras transparentes, contenido detrás cuando es seguro
        enableEdgeToEdge()

        readNotificationExtras(intent)

        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)

        // POST_NOTIFICATIONS se pide en PermissionsOnboardingScreen, con
        // contexto, no aquí al arrancar (Android 13+ rechaza el diálogo frío).

        setContent {
            val selectedTheme = SettingsManager.selectedThemeOption
            val dynamicColor = SettingsManager.dynamicColorEnabled
            // Respeta tanto la preferencia de Vivid como "Quitar animaciones" del sistema.
            // ValueAnimator también devuelve false cuando la escala de animación es 0.
            val animationsEnabled = SettingsManager.smoothAnimationsEnabled &&
                ValueAnimator.areAnimatorsEnabled()
            val darkTheme = when (selectedTheme) {
                "Oscuro" -> true
                "Claro" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val deepLinkChatId = pendingChatId
            val deepLinkReelId = pendingReelId
            val deepLinkProfileUserId = pendingProfileUserId
            val deepLinkPostId = pendingPostId

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

    override fun onDestroy() {
        // La Activity es de tarea larga (singleTask + deep links): remover el
        // listener evita fugas y callbacks sobre una vista muerta.
        runCatching { FirebaseAuth.getInstance().removeAuthStateListener(authStateListener) }
        super.onDestroy()
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
        if (intent.getBooleanExtra("openPost", false)) {
            pendingPostId = intent.getStringExtra("postId")
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

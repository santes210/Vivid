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

    companion object {
        /**
         * Extra que mandan los **atajos del launcher** (long-press) y el
         * **widget "Crear"**: qué parte de la app abrir al arrancar.
         * El valor literal vive también en res/xml/shortcuts.xml (no puede
         * referenciar una const Kotlin): mantenerlos en sincronía.
         */
        const val EXTRA_SHORTCUT_ACTION = "com.vivid.app.extra.SHORTCUT_ACTION"

        const val SHORTCUT_CREATE_POST = "create_post"
        const val SHORTCUT_CREATE_REEL = "create_reel"
        const val SHORTCUT_CREATE_STORY = "create_story"
        const val SHORTCUT_MESSAGES = "messages"
        const val SHORTCUT_SEARCH = "search"
    }

    /**
     * Un toque a un atajo/widget: la acción + un [seq] que crece en cada
     * lanzamiento. Sin la secuencia, repetir el MISMO atajo dos veces
     * (mismo string) no re-dispararía la navegación porque la clave del
     * LaunchedEffect no cambiaría.
     */
    data class ShortcutRequest(val action: String, val seq: Int)

    private var pendingChatId: String? by mutableStateOf(null)
    private var pendingReelId: String? by mutableStateOf(null)
    private var pendingProfileUserId: String? by mutableStateOf(null)
    private var pendingPostId: String? by mutableStateOf(null)
    private var pendingShortcutRequest: ShortcutRequest? by mutableStateOf(null)
    private var shortcutSeq = 0

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
            val seedPalette = com.vivid.app.theme.VividSeedPalette
                .fromId(SettingsManager.seedPaletteId)
            val amoled = SettingsManager.amoledBlackEnabled
            // Respeta tanto la preferencia de Vivid como "Quitar animaciones" del sistema.
            // ValueAnimator también devuelve false cuando la escala de animación es 0.
            val animationsEnabled = SettingsManager.smoothAnimationsEnabled &&
                ValueAnimator.areAnimatorsEnabled()
            val darkTheme = when (selectedTheme) {
                SettingsManager.THEME_DARK -> true
                SettingsManager.THEME_LIGHT -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            val deepLinkChatId = pendingChatId
            val deepLinkReelId = pendingReelId
            val deepLinkProfileUserId = pendingProfileUserId
            val deepLinkPostId = pendingPostId
            val deepLinkShortcut = pendingShortcutRequest

            VividTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor,
                seedPalette = seedPalette,
                amoled = amoled
            ) {
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
                            deepLinkPostId = deepLinkPostId,
                            deepLinkShortcut = deepLinkShortcut
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
        // Atajos del launcher (long-press) y widget "Crear": llegan como
        // Intent ACTION_VIEW con targetClass MainActivity + este extra.
        // Se lee aquí (onCreate y onNewIntent) para que funcione tanto el
        // arranque frío como el warm start con la app ya en segundo plano.
        // El seq crece en cada lanzamiento para que repetir el mismo atajo
        // re-dispare la navegación (ver ShortcutRequest).
        val shortcutAction = intent.getStringExtra(EXTRA_SHORTCUT_ACTION)
        if (shortcutAction != null) {
            shortcutSeq += 1
            pendingShortcutRequest = ShortcutRequest(shortcutAction, shortcutSeq)
        }
    }
}

@Composable
fun VividApp(
    deepLinkChatId: String? = null,
    deepLinkReelId: String? = null,
    deepLinkProfileUserId: String? = null,
    deepLinkPostId: String? = null,
    deepLinkShortcut: MainActivity.ShortcutRequest? = null
) {
    val navController = rememberNavController()
    VividNavigation(
        navController = navController,
        deepLinkChatId = deepLinkChatId,
        deepLinkReelId = deepLinkReelId,
        deepLinkProfileUserId = deepLinkProfileUserId,
        deepLinkPostId = deepLinkPostId,
        deepLinkShortcut = deepLinkShortcut
    )
}

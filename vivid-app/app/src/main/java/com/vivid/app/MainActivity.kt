package com.vivid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.navigation.VividNavigation
import com.vivid.app.theme.VividTheme
import com.vivid.app.util.SettingsManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permisos de notificación en Android 13+ de forma proactiva
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

            // Registrar el token FCM para notificaciones push cuando hay sesión activa
            val currentUser = FirebaseAuth.getInstance().currentUser
            LaunchedEffect(currentUser) {
                if (currentUser != null) {
                    com.vivid.app.util.PushNotificationHelper.registerTokenForCurrentUser()
                }
            }

            VividTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VividApp()
                }
            }
        }
    }
}

@Composable
fun VividApp() {
    val navController = rememberNavController()
    VividNavigation(navController = navController)
}
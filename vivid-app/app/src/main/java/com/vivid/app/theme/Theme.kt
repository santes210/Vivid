package com.vivid.app.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Theme principal Vivid — Material You 3 Expressive (M3 1.5.0-alpha23).
 *
 *   - dynamicColor: true → toma colores del wallpaper (Android 12+)
 *   - dynamicColor: false → usa la paleta de marca VividBrandColors
 *   - edge-to-edge: status/navigation bars transparentes (WindowCompat)
 *
 * Además aplica:
 *   - VividTypography (escala completa M3 + variantes Emphasized)
 *   - VividShapes (esquinas expresivas con propósito)
 *   - VividMotionScheme → MotionScheme.expressive() (físicas de muelle / rebote)
 *   - surfaceContainer* hierarchy (sin transparencias arbitrarias)
 *   - systemBars contrast automático (iconos claros/oscuros según tema)
 *
 * Perf: el colorScheme se cachea con `remember(darkTheme, dynamicColor)` para no
 * regenerar la paleta dinámica en cada recomposición (importante en gama baja).
 *
 * Versiones (upgrade coordinado — ver EXPRESSIVE_M3_UPGRADE.md):
 *   - material3: 1.5.0-alpha23 (Expressive APIs; no existen en 1.4.0 estable)
 *   - compose:   1.12.0-alpha03 (requisito de material3 1.5.0-alpha23)
 *   - kotlin:    2.1.20 (+ KSP 2.1.20-1.0.32)
 */
@Composable
fun VividTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> darkColorScheme(
                primary = VividBrandColorsDark.Primary,
                onPrimary = VividBrandColorsDark.OnPrimary,
                primaryContainer = VividBrandColorsDark.PrimaryContainer,
                onPrimaryContainer = VividBrandColorsDark.OnPrimaryContainer,
                secondary = VividBrandColorsDark.Secondary,
                onSecondary = VividBrandColorsDark.OnSecondary,
                secondaryContainer = VividBrandColorsDark.SecondaryContainer,
                onSecondaryContainer = VividBrandColorsDark.OnSecondaryContainer,
                tertiary = VividBrandColorsDark.Tertiary,
                onTertiary = VividBrandColorsDark.OnTertiary,
                tertiaryContainer = VividBrandColorsDark.TertiaryContainer,
                onTertiaryContainer = VividBrandColorsDark.OnTertiaryContainer,
                error = VividBrandColorsDark.Error,
                onError = VividBrandColorsDark.OnError,
                errorContainer = VividBrandColorsDark.ErrorContainer,
                onErrorContainer = VividBrandColorsDark.OnErrorContainer,
                background = VividBrandColorsDark.Background,
                onBackground = VividBrandColorsDark.OnBackground,
                surface = VividBrandColorsDark.Surface,
                onSurface = VividBrandColorsDark.OnSurface,
                surfaceVariant = VividBrandColorsDark.SurfaceVariant,
                onSurfaceVariant = VividBrandColorsDark.OnSurfaceVariant,
                surfaceTint = VividBrandColorsDark.SurfaceTint,
                outline = VividBrandColorsDark.Outline,
                outlineVariant = VividBrandColorsDark.OutlineVariant,
                surfaceContainer = VividBrandColorsDark.SurfaceContainer,
                surfaceContainerHigh = VividBrandColorsDark.SurfaceContainerHigh,
                surfaceContainerHighest = VividBrandColorsDark.SurfaceContainerHighest,
                surfaceContainerLow = VividBrandColorsDark.SurfaceContainerLow,
                surfaceContainerLowest = VividBrandColorsDark.SurfaceContainerLowest,
                inverseSurface = VividBrandColorsDark.InverseSurface,
                inverseOnSurface = VividBrandColorsDark.InverseOnSurface,
                inversePrimary = VividBrandColorsDark.InversePrimary,
                scrim = VividBrandColorsDark.Scrim
            )
            else -> lightColorScheme(
                primary = VividBrandColors.Primary,
                onPrimary = VividBrandColors.OnPrimary,
                primaryContainer = VividBrandColors.PrimaryContainer,
                onPrimaryContainer = VividBrandColors.OnPrimaryContainer,
                secondary = VividBrandColors.Secondary,
                onSecondary = VividBrandColors.OnSecondary,
                secondaryContainer = VividBrandColors.SecondaryContainer,
                onSecondaryContainer = VividBrandColors.OnSecondaryContainer,
                tertiary = VividBrandColors.Tertiary,
                onTertiary = VividBrandColors.OnTertiary,
                tertiaryContainer = VividBrandColors.TertiaryContainer,
                onTertiaryContainer = VividBrandColors.OnTertiaryContainer,
                error = VividBrandColors.Error,
                onError = VividBrandColors.OnError,
                errorContainer = VividBrandColors.ErrorContainer,
                onErrorContainer = VividBrandColors.OnErrorContainer,
                background = VividBrandColors.Background,
                onBackground = VividBrandColors.OnBackground,
                surface = VividBrandColors.Surface,
                onSurface = VividBrandColors.OnSurface,
                surfaceVariant = VividBrandColors.SurfaceVariant,
                onSurfaceVariant = VividBrandColors.OnSurfaceVariant,
                surfaceTint = VividBrandColors.SurfaceTint,
                outline = VividBrandColors.Outline,
                outlineVariant = VividBrandColors.OutlineVariant,
                surfaceContainer = VividBrandColors.SurfaceContainer,
                surfaceContainerHigh = VividBrandColors.SurfaceContainerHigh,
                surfaceContainerHighest = VividBrandColors.SurfaceContainerHighest,
                surfaceContainerLow = VividBrandColors.SurfaceContainerLow,
                surfaceContainerLowest = VividBrandColors.SurfaceContainerLowest,
                inverseSurface = VividBrandColors.InverseSurface,
                inverseOnSurface = VividBrandColors.InverseOnSurface,
                inversePrimary = VividBrandColors.InversePrimary,
                scrim = VividBrandColors.Scrim
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge real: barras 100% transparentes, contenido detrás cuando es seguro.
            // MainActivity.enableEdgeToEdge() ya hace setDecorFitsSystemWindows(false);
            // aquí solo aseguramos transparencia + contraste automático de iconos.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.isStatusBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = VividShapes,
        typography = VividTypography,
        // Material 3 Expressive: físicas de muelle (springs con rebote) en todos los
        // componentes que respetan MaterialTheme.motionScheme (bottom sheets,
        // expansiones, indicadores, morphing de formas, etc.).
        motionScheme = VividMotionScheme,
        content = content
    )
}

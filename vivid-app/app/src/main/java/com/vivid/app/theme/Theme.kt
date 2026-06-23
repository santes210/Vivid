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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Theme principal Vivid — Material You 3 completo.
 *
 *   - dynamicColor: true → toma colores del wallpaper (Android 12+)
 *   - dynamicColor: false → usa la paleta de marca VividBrandColors
 *   - edge-to-edge: status/navigation bars transparentes
 *
 * Además aplica:
 *   - VividTypography (escala completa M3)
 *   - VividShapes (esquinas expresivas)
 *   - systemBars contrast automático (iconos claros/oscuros según tema)
 */
@Composable
fun VividTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
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
            outline = VividBrandColorsDark.Outline,
            outlineVariant = VividBrandColorsDark.OutlineVariant,
            surfaceTint = VividBrandColorsDark.SurfaceTint
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
            outline = VividBrandColors.Outline,
            outlineVariant = VividBrandColors.OutlineVariant,
            surfaceTint = VividBrandColors.SurfaceTint
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: status bar y navigation bar transparentes
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            // Iconos claros si el fondo es oscuro
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VividTypography,
        shapes = VividShapes,
        content = content
    )
}

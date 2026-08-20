package com.vivid.app.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.vivid.app.util.LocaleManager

/**
 * Theme principal de Vivid — Material 3 Expressive real (material3 1.5.0-alpha).
 *
 * Qué instala:
 *   - **ColorScheme**: color dinámico del wallpaper en Android 12+ cuando el
 *     usuario lo deja activado; si no, la paleta de marca "Vivid Sunset"
 *     generada en [VividBrandColors] / [VividBrandColorsDark].
 *   - **MotionScheme.expressive()**: springs con rebote que los componentes de
 *     material3 aplican automáticamente. Ver [VividMotion] para las
 *     animaciones propias de la app.
 *   - **Shapes** ([VividShapes]) y **Typography** ([VividTypography]).
 *   - **[LocalVividAccents]**: los colores de producto (like, anillo de
 *     historias, verificado, en línea) armonizados con el esquema activo, para
 *     que convivan con el color dinámico sin dejar de ser reconocibles.
 *   - Edge-to-edge: contraste automático de los iconos de las barras del
 *     sistema. Las barras ya son transparentes por `enableEdgeToEdge()` en
 *     `MainActivity`; pintarlas aquí con `window.statusBarColor` no haría nada
 *     (la API es no-op desde API 35).
 *
 * Perf: el esquema se cachea con `remember(darkTheme, dynamicColor)` para no
 * regenerar la paleta dinámica en cada recomposición (nota en gama baja).
 */
@Composable
fun VividTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = remember(darkTheme, dynamicColor) {
        val supportsDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        when {
            supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
            supportsDynamic -> dynamicLightColorScheme(context)
            darkTheme -> vividDarkColorScheme()
            else -> vividLightColorScheme()
        }
    }

    val accents = remember(colorScheme.primary, darkTheme) {
        VividAccents.harmonizedTo(colorScheme.primary, darkTheme)
    }

    val view = LocalView.current
    // En un @Preview no hay Activity: tocar la ventana reventaría el render.
    val inPreview = LocalInspectionMode.current
    if (!view.isInEditMode && !inPreview) {
        SideEffect {
            val window = (view.context as Activity).window
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

    // La escala tipográfica se aplica en Activity.attachBaseContext()
    // (Configuration.fontScale). Los `sp` de Compose ya la respetan; volver a
    // multiplicar VividTypography aquí la duplicaría. LocalFontScale se expone
    // solo para los pocos tamaños que se calculan a mano (dp de iconos, etc.).
    CompositionLocalProvider(
        LocalFontScale provides LocaleManager.fontScale,
        LocalVividAccents provides accents
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = VividShapes,
            typography = VividTypography,
            content = content
        )
    }
}

/** Esquema claro de marca (sin color dinámico). */
internal fun vividLightColorScheme(): ColorScheme = lightColorScheme(
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
    surfaceBright = VividBrandColors.SurfaceBright,
    surfaceDim = VividBrandColors.SurfaceDim,
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

/** Esquema oscuro de marca (sin color dinámico). */
internal fun vividDarkColorScheme(): ColorScheme = darkColorScheme(
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
    surfaceBright = VividBrandColorsDark.SurfaceBright,
    surfaceDim = VividBrandColorsDark.SurfaceDim,
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

package com.vivid.app.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSp
import androidx.compose.ui.unit.sp
import com.vivid.app.R

/**
 * Tipografía de marca Vivid — Material You 3 Expressive.
 *
 * Estrategia tipográfica (mejora 2026-08-09):
 *   - Fuente de marca (Sora) SOLO para títulos importantes (display, headline y titleLarge).
 *     Da identidad sin sacrificar legibilidad.
 *   - Fuente del sistema / Roboto para textos largos (body) y etiquetas (label).
 *   - Títulos grandes reservados a "momentos hero"; el resto de la app usa pesos moderados.
 *   - Menos negrita en listas y mejor separación entre título, descripción y metadata
 *     (se maneja con los estilos y spacing en cada pantalla).
 *
 * Los colores los toma dinámicamente del wallpaper del usuario
 * (Android 12+). En <12 usa el fallback Vivid.
 *
 * Escala tipográfica (mejora 2026-08-18): [scaledVividTypography] aplica
 * la escala elegida en Ajustes → Tamaño de fuente a los TextSize en sp,
 * preservando lineHeight, letterSpacing y el resto del estilo.
 */

/** Familia de marca para títulos importantes. */
val SoraFamily = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_extrabold, FontWeight.ExtraBold)
)

/** Escala tipográfica global (Ajustes → Tamaño de fuente). 1.0 = normal. */
val LocalFontScale = compositionLocalOf { 1.0f }

/**
 * Devuelve una copia de [base] con todos los `fontSize` en sp multiplicados
 * por [scale]. `lineHeight` también se reescala si está en sp para mantener
 * proporciones; si está en em u otra unidad, se queda igual.
 */
internal fun Typography.scaled(scale: Float): Typography {
    if (scale == 1.0f) return this
    fun TextStyle.scaledStyle() = copy(
        fontSize = if (fontSize.isSp) (fontSize.value * scale).sp else fontSize,
        lineHeight = when {
            lineHeight.isSp -> (lineHeight.value * scale).sp
            else -> lineHeight
        }
    )
    return Typography(
        displayLarge = displayLarge.scaledStyle(),
        displayMedium = displayMedium.scaledStyle(),
        displaySmall = displaySmall.scaledStyle(),
        headlineLarge = headlineLarge.scaledStyle(),
        headlineMedium = headlineMedium.scaledStyle(),
        headlineSmall = headlineSmall.scaledStyle(),
        titleLarge = titleLarge.scaledStyle(),
        titleMedium = titleMedium.scaledStyle(),
        titleSmall = titleSmall.scaledStyle(),
        bodyLarge = bodyLarge.scaledStyle(),
        bodyMedium = bodyMedium.scaledStyle(),
        bodySmall = bodySmall.scaledStyle(),
        labelLarge = labelLarge.scaledStyle(),
        labelMedium = labelMedium.scaledStyle(),
        labelSmall = labelSmall.scaledStyle()
    )
}

/**
 * Tipografía efectiva: toma [LocalFontScale] y la aplica a [VividTypography].
 * Usar SIEMPRE esta en `MaterialTheme(typography = ...)` para que el cambio
 * de tamaño de fuente se refleje sin reiniciar la app.
 */
@androidx.compose.runtime.Composable
fun effectiveVividTypography(): Typography {
    val scale = LocalFontScale.current
    return VividTypography.scaled(scale)
}

val VividTypography = Typography(
    // ----- DISPLAY (héroe / splash) — fuente de marca, solo en momentos hero -----
    displayLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),

    // ----- HEADLINE (secciones, top bar) — fuente de marca -----
    headlineLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),

    // ----- TITLE (cards, dialogs) — fuente de marca para el título principal -----
    titleLarge = TextStyle(
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // ----- BODY (texto principal) — sistema / Roboto -----
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // ----- LABEL (botones, chips) -----
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

package com.vivid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de fallback (cuando el dispositivo NO soporta Material You dinámico,
 * es decir Android <12 o el usuario desactivó el color dinámico).
 *
 * Tokens Material 3:
 *   - primary / onPrimary / primaryContainer / onPrimaryContainer
 *   - secondary / tertiary / error (y sus containers)
 *   - surface (con sus variantes: surfaceVariant, surfaceTint, ...)
 *
 * Estos se usan solo si el sistema no provee dynamicColor.
 * En Android 12+ el theme toma los colores del wallpaper automáticamente.
 */
internal object VividBrandColors {
    // Morado principal (Vivid) — inspirado en IG purple accent
    val Primary              = Color(0xFF6750A4)
    val OnPrimary            = Color(0xFFFFFFFF)
    val PrimaryContainer     = Color(0xFFEADDFF)
    val OnPrimaryContainer   = Color(0xFF21005D)

    // Secundario (azul-violeta para highlights, capturas)
    val Secondary            = Color(0xFF625B71)
    val OnSecondary          = Color(0xFFFFFFFF)
    val SecondaryContainer   = Color(0xFFE8DEF8)
    val OnSecondaryContainer = Color(0xFF1D192B)

    // Terciario (rosa para accents de likes, hearts)
    val Tertiary             = Color(0xFF7D5260)
    val OnTertiary           = Color(0xFFFFFFFF)
    val TertiaryContainer    = Color(0xFFFFD8E4)
    val OnTertiaryContainer  = Color(0xFF31111D)

    // Errores
    val Error                = Color(0xFFB3261E)
    val OnError              = Color(0xFFFFFFFF)
    val ErrorContainer       = Color(0xFFF9DEDC)
    val OnErrorContainer     = Color(0xFF410E0B)

    // Fondos y superficies
    val Background           = Color(0xFFFFFBFE)
    val OnBackground         = Color(0xFF1C1B1F)
    val Surface              = Color(0xFFFFFBFE)
    val OnSurface            = Color(0xFF1C1B1F)
    val SurfaceVariant       = Color(0xFFE7E0EC)
    val OnSurfaceVariant     = Color(0xFF49454F)
    val Outline              = Color(0xFF79747E)
    val OutlineVariant       = Color(0xFFCAC4D0)
    val SurfaceTint          = Primary
}

/** Variantes oscuras para los mismos tokens */
internal object VividBrandColorsDark {
    val Primary              = Color(0xFFD0BCFF)
    val OnPrimary            = Color(0xFF381E72)
    val PrimaryContainer     = Color(0xFF4F378B)
    val OnPrimaryContainer   = Color(0xFFEADDFF)

    val Secondary            = Color(0xFFCCC2DC)
    val OnSecondary          = Color(0xFF332D41)
    val SecondaryContainer   = Color(0xFF4A4458)
    val OnSecondaryContainer = Color(0xFFE8DEF8)

    val Tertiary             = Color(0xFFEFB8C8)
    val OnTertiary           = Color(0xFF492532)
    val TertiaryContainer    = Color(0xFF633B48)
    val OnTertiaryContainer  = Color(0xFFFFD8E4)

    val Error                = Color(0xFFF2B8B7)
    val OnError              = Color(0xFF601410)
    val ErrorContainer       = Color(0xFF8C1D18)
    val OnErrorContainer     = Color(0xFFF9DEDC)

    val Background           = Color(0xFF1C1B1F)
    val OnBackground         = Color(0xFFE6E1E5)
    val Surface              = Color(0xFF1C1B1F)
    val OnSurface            = Color(0xFFE6E1E5)
    val SurfaceVariant       = Color(0xFF49454F)
    val OnSurfaceVariant     = Color(0xFFCAC4D0)
    val Outline              = Color(0xFF938F99)
    val OutlineVariant       = Color(0xFF49454F)
    val SurfaceTint          = Primary
}

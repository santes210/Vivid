package com.vivid.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca "Vivid Sunset" — GENERADA, no editar a mano.
 *
 *   Fuente: scripts/generate_vivid_palette.py
 *   Regenerar:
 *     python3 scripts/generate_vivid_palette.py > \
 *       vivid-app/app/src/main/java/com/vivid/app/theme/VividColors.kt
 *
 * Se usa cuando NO hay Material You dinámico (Android < 12, o el usuario
 * desactivó "Color dinámico" en Ajustes → Apariencia). Con color dinámico,
 * el esquema sale del wallpaper y solo se conservan los acentos de producto
 * de [VividAccentColors], armonizados hacia el color del sistema.
 *
 * Construcción (ver el script para el detalle): matiz y croma fijos en OkLCh,
 * tono = L* de CIELAB, exactamente el mismo eje de "tone" que usa HCT en
 * Material. Resultado: rampas sin virajes de matiz y con contraste
 * predecible entre pares on-/container.
 *
 * Semillas:
 *   primary   magenta-coral (hue 6°, croma 0.23)   → identidad
 *   secondary rosa apagado  (hue 8°, croma 0.075)  → soporte
 *   tertiary  ámbar         (hue 68°, croma 0.15)  → atardecer / celebración
 *   error     rojo          (hue 27°, croma 0.16)  → peligro, distinto al brand
 *   neutral   gris CÁLIDO   (croma 0.008)          → las fotos respiran mejor
 */

/** Roles M3 en tema claro. Tonos: primary 40/90, neutrales cálidos 99→90. */
internal object VividBrandColors {
    val Primary                 = Color(0xFFB71454)  // primary 40
    val OnPrimary               = Color(0xFFFFFFFF)  // primary 100
    val PrimaryContainer        = Color(0xFFFFD8E0)  // primary 90
    val OnPrimaryContainer      = Color(0xFF3E0218)  // primary 10
    val Secondary               = Color(0xFF854E59)  // secondary 40
    val OnSecondary             = Color(0xFFFFFFFF)  // secondary 100
    val SecondaryContainer      = Color(0xFFFFD9DE)  // secondary 90
    val OnSecondaryContainer    = Color(0xFF390917)  // secondary 10
    val Tertiary                = Color(0xFF875303)  // tertiary 40
    val OnTertiary              = Color(0xFFFFFFFF)  // tertiary 100
    val TertiaryContainer       = Color(0xFFFFDCB8)  // tertiary 90
    val OnTertiaryContainer     = Color(0xFF2B1700)  // tertiary 10
    val Error                   = Color(0xFFAC322D)  // error 40
    val OnError                 = Color(0xFFFFFFFF)  // error 100
    val ErrorContainer          = Color(0xFFFFDAD4)  // error 90
    val OnErrorContainer        = Color(0xFF400203)  // error 10
    val Background              = Color(0xFFFFFBFA)  // neutral 99
    val OnBackground            = Color(0xFF1F1B19)  // neutral 10
    val Surface                 = Color(0xFFFFFBFA)  // neutral 99
    val OnSurface               = Color(0xFF1F1B19)  // neutral 10
    val SurfaceVariant          = Color(0xFFF2DEDB)  // neutral_variant 90
    val OnSurfaceVariant        = Color(0xFF534340)  // neutral_variant 30
    val Outline                 = Color(0xFF847370)  // neutral_variant 50
    val OutlineVariant          = Color(0xFFD5C2BF)  // neutral_variant 80
    val SurfaceContainerLowest  = Color(0xFFFFFFFF)  // neutral 100
    val SurfaceContainerLow     = Color(0xFFF9F2F0)  // neutral 96
    val SurfaceContainer        = Color(0xFFF3ECEA)  // neutral 94
    val SurfaceContainerHigh    = Color(0xFFEDE7E4)  // neutral 92
    val SurfaceContainerHighest = Color(0xFFE8E1DF)  // neutral 90
    val SurfaceBright           = Color(0xFFFFF8F6)  // neutral 98
    val SurfaceDim              = Color(0xFFDFD9D6)  // neutral 87
    val InverseSurface          = Color(0xFF342F2E)  // neutral 20
    val InverseOnSurface        = Color(0xFFF6EFED)  // neutral 95
    val InversePrimary          = Color(0xFFFFAFC1)  // primary 80
    val Scrim                   = Color(0xFF000000)  // neutral 0
    val SurfaceTint             = Primary
}

/** Roles M3 en tema oscuro. Tonos: primary 80/30, neutrales cálidos 6→22. */
internal object VividBrandColorsDark {
    val Primary                 = Color(0xFFFFAFC1)  // primary 80
    val OnPrimary               = Color(0xFF64062B)  // primary 20
    val PrimaryContainer        = Color(0xFF8C0D3F)  // primary 30
    val OnPrimaryContainer      = Color(0xFFFFD8E0)  // primary 90
    val Secondary               = Color(0xFFF5B6C0)  // secondary 80
    val OnSecondary             = Color(0xFF52202B)  // secondary 20
    val SecondaryContainer      = Color(0xFF6B3741)  // secondary 30
    val OnSecondaryContainer    = Color(0xFFFFD9DE)  // secondary 90
    val Tertiary                = Color(0xFFFFB863)  // tertiary 80
    val OnTertiary              = Color(0xFF482A01)  // tertiary 20
    val TertiaryContainer       = Color(0xFF663D01)  // tertiary 30
    val OnTertiaryContainer     = Color(0xFFFFDCB8)  // tertiary 90
    val Error                   = Color(0xFFFFB2A8)  // error 80
    val OnError                 = Color(0xFF660609)  // error 20
    val ErrorContainer          = Color(0xFF8F0F13)  // error 30
    val OnErrorContainer        = Color(0xFFFFDAD4)  // error 90
    val Background              = Color(0xFF171211)  // neutral 6
    val OnBackground            = Color(0xFFE8E1DF)  // neutral 90
    val Surface                 = Color(0xFF171211)  // neutral 6
    val OnSurface               = Color(0xFFE8E1DF)  // neutral 90
    val SurfaceVariant          = Color(0xFF534340)  // neutral_variant 30
    val OnSurfaceVariant        = Color(0xFFD5C2BF)  // neutral_variant 80
    val Outline                 = Color(0xFF9F8D8A)  // neutral_variant 60
    val OutlineVariant          = Color(0xFF534340)  // neutral_variant 30
    val SurfaceContainerLowest  = Color(0xFF110D0C)  // neutral 4
    val SurfaceContainerLow     = Color(0xFF1F1B19)  // neutral 10
    val SurfaceContainer        = Color(0xFF231F1D)  // neutral 12
    val SurfaceContainerHigh    = Color(0xFF2E2927)  // neutral 17
    val SurfaceContainerHighest = Color(0xFF393432)  // neutral 22
    val SurfaceBright           = Color(0xFF3D3836)  // neutral 24
    val SurfaceDim              = Color(0xFF171211)  // neutral 6
    val InverseSurface          = Color(0xFFE8E1DF)  // neutral 90
    val InverseOnSurface        = Color(0xFF342F2E)  // neutral 20
    val InversePrimary          = Color(0xFFB71454)  // primary 40
    val Scrim                   = Color(0xFF000000)  // neutral 0
    val SurfaceTint             = Primary
}

/**
 * Acentos de producto. NO son roles de Material: son constantes de marca que
 * deben sobrevivir al color dinámico (un corazón de like verde porque el
 * wallpaper es verde sería un bug de producto, no una feature).
 *
 * [com.vivid.app.theme.harmonizeWith] los inclina ligeramente hacia el color
 * del sistema para que convivan con la paleta dinámica sin perder identidad.
 */
object VividAccentColors {
    val Like           = Color(0xFFFB266B)
    val LikeDark       = Color(0xFFFF7693)
    val StoryRingStart = Color(0xFFFF4484)
    val StoryRingMid   = Color(0xFFFF6C47)
    val StoryRingEnd   = Color(0xFFEDA109)
    val Verified       = Color(0xFF008BCD)
    val Online         = Color(0xFF23974B)
    val Live           = Color(0xFFEB3138)
}

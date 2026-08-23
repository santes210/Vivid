package com.vivid.app.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Rampas tonales y esquemas de color generados **en runtime** a partir de una
 * semilla de marca.
 *
 * Por qué existe
 * --------------
 * `scripts/generate_vivid_palette.py` genera la paleta "Vivid Sunset" en tiempo
 * de compilación ([VividBrandColors]). Eso vale para *una* identidad, pero
 * Ajustes → Apariencia ofrece ahora varias ([VividSeedPalette]): mantener cinco
 * paletas de 34 roles a mano (o cinco objetos generados) sería ruido puro.
 *
 * Este fichero es el port a Kotlin del mismo algoritmo del script, para poder
 * construir un [ColorScheme] completo desde una semilla:
 *
 *   * el **tono** (0..100) es exactamente L* de CIELAB — el mismo eje que usa
 *     HCT en Material, así que los pares on-/container conservan su contraste;
 *   * **matiz y croma** se fijan en OkLCh (perceptualmente uniforme): las
 *     rampas no viran de matiz al aclararse, cosa que sí pasa en HSL;
 *   * si el croma pedido se sale del gamut sRGB para ese tono, se reduce por
 *     bisección hasta que entra.
 *
 * Coste: ~34 colores × 3 bisecciones. Del orden de décimas de milisegundo y
 * además cacheado por rampa; [VividTheme] lo envuelve en `remember`, así que se
 * calcula una vez por cambio de paleta/modo, nunca por recomposición.
 */

// ── sRGB ⇄ CIELAB ────────────────────────────────────────────────────────────

private fun linearToSrgbD(c: Double): Double =
    if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

/** L* de CIELAB (= "tone" de HCT) de un sRGB lineal. */
private fun labL(r: Double, g: Double, b: Double): Double {
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val fy = if (y > 216.0 / 24389.0) y.pow(1.0 / 3.0) else (24389.0 / 27.0 * y + 16.0) / 116.0
    return 116.0 * fy - 16.0
}

// ── OkLab / OkLCh ────────────────────────────────────────────────────────────

private fun oklabToLinearRgb(l: Double, a: Double, b: Double): DoubleArray {
    val lp = (l + 0.3963377774 * a + 0.2158037573 * b).let { it * it * it }
    val mp = (l - 0.1055613458 * a - 0.0638541728 * b).let { it * it * it }
    val sp = (l - 0.0894841775 * a - 1.2914855480 * b).let { it * it * it }
    return doubleArrayOf(
        +4.0767416621 * lp - 3.3077115913 * mp + 0.2309699292 * sp,
        -1.2684380046 * lp + 2.6097574011 * mp - 0.3413193965 * sp,
        -0.0041960863 * lp - 0.7034186147 * mp + 1.7076147010 * sp
    )
}

private fun oklchToLinearRgb(l: Double, chroma: Double, hueDeg: Double): DoubleArray {
    val h = Math.toRadians(hueDeg)
    return oklabToLinearRgb(l, chroma * cos(h), chroma * sin(h))
}

private fun inGamut(rgb: DoubleArray, eps: Double = 1e-4): Boolean =
    rgb.all { it >= -eps && it <= 1.0 + eps }

/**
 * Color con este [hue]/[chroma] (OkLCh) cuyo L* de CIELAB vale [tone] (0..100).
 *
 * Mismo procedimiento que el script: bisección de L, recorte de croma al gamut
 * y reajuste fino de L (añadir croma mueve un poco el L* real).
 */
internal fun vividToneColor(hue: Float, chroma: Float, tone: Float): Color {
    if (tone >= 100f) return Color.White
    if (tone <= 0f) return Color.Black

    val hueD = hue.toDouble()
    val toneD = tone.toDouble()

    fun solveL(c: Double): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(48) {
            val mid = (lo + hi) / 2
            val rgb = oklchToLinearRgb(mid, c, hueD)
            if (labL(rgb[0], rgb[1], rgb[2]) < toneD) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    // 1) L de OkLab que produce el L* pedido con croma 0.
    val okLGray = solveL(0.0)

    // 2) Croma máximo representable en sRGB para ese matiz y esa luminosidad.
    var target = minOf(chroma.toDouble(), 0.4)
    if (!inGamut(oklchToLinearRgb(okLGray, target, hueD))) {
        var loC = 0.0
        var hiC = target
        repeat(40) {
            val mid = (loC + hiC) / 2
            if (inGamut(oklchToLinearRgb(okLGray, mid, hueD))) loC = mid else hiC = mid
        }
        target = loC
    }

    // 3) Reajuste fino de L con el croma ya definitivo.
    val rgb = oklchToLinearRgb(solveL(target), target, hueD)
    fun ch(v: Double): Float =
        linearToSrgbD(v.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0).toFloat()
    return Color(ch(rgb[0]), ch(rgb[1]), ch(rgb[2]))
}

/** Rampa tonal de un matiz, con caché: `ramp[40]` = tono 40. */
internal class VividTonalRamp(private val hue: Float, private val chroma: Float) {
    private val cache = HashMap<Int, Color>(20)
    operator fun get(tone: Int): Color = cache.getOrPut(tone) {
        vividToneColor(hue, chroma, tone.toFloat())
    }
}

// ── Semillas de marca ────────────────────────────────────────────────────────

/**
 * Semillas de color seleccionables en Ajustes → Apariencia.
 *
 * Solo aplican cuando el color dinámico (Material You) está **desactivado** o
 * el dispositivo es anterior a Android 12: con wallpaper el esquema manda él.
 *
 * Los matices son OkLCh: rojo ≈ 29°, ámbar ≈ 70°, verde ≈ 150°, cian ≈ 200°,
 * azul ≈ 260°, púrpura ≈ 305°, magenta ≈ 350°.
 *
 * [SUNSET] es la identidad histórica y se sirve desde [VividBrandColors], que
 * es exactamente esta misma construcción precalculada por el script.
 */
enum class VividSeedPalette(
    val id: String,
    @field:StringRes val labelRes: Int,
    private val primaryHue: Float,
    private val primaryChroma: Float,
    private val secondaryHue: Float,
    private val secondaryChroma: Float,
    private val tertiaryHue: Float,
    private val tertiaryChroma: Float,
    private val neutralHue: Float,
    private val neutralChroma: Float,
    private val neutralVariantHue: Float,
    private val neutralVariantChroma: Float
) {
    /** Magenta-coral sobre neutros cálidos. La marca. */
    SUNSET(
        id = "sunset",
        labelRes = com.vivid.app.R.string.palette_sunset,
        primaryHue = 6f, primaryChroma = 0.230f,
        secondaryHue = 8f, secondaryChroma = 0.075f,
        tertiaryHue = 68f, tertiaryChroma = 0.150f,
        neutralHue = 40f, neutralChroma = 0.008f,
        neutralVariantHue = 30f, neutralVariantChroma = 0.022f
    ),

    /** Azul profundo con acento cian. */
    OCEAN(
        id = "ocean",
        labelRes = com.vivid.app.R.string.palette_ocean,
        primaryHue = 250f, primaryChroma = 0.160f,
        secondaryHue = 245f, secondaryChroma = 0.060f,
        tertiaryHue = 200f, tertiaryChroma = 0.120f,
        neutralHue = 250f, neutralChroma = 0.007f,
        neutralVariantHue = 250f, neutralVariantChroma = 0.020f
    ),

    /** Verde bosque con acento lima. */
    FOREST(
        id = "forest",
        labelRes = com.vivid.app.R.string.palette_forest,
        primaryHue = 150f, primaryChroma = 0.150f,
        secondaryHue = 150f, secondaryChroma = 0.055f,
        tertiaryHue = 110f, tertiaryChroma = 0.130f,
        neutralHue = 150f, neutralChroma = 0.006f,
        neutralVariantHue = 150f, neutralVariantChroma = 0.018f
    ),

    /** Púrpura suave con acento rosa. */
    LAVENDER(
        id = "lavender",
        labelRes = com.vivid.app.R.string.palette_lavender,
        primaryHue = 305f, primaryChroma = 0.150f,
        secondaryHue = 305f, secondaryChroma = 0.055f,
        tertiaryHue = 345f, tertiaryChroma = 0.120f,
        neutralHue = 305f, neutralChroma = 0.006f,
        neutralVariantHue = 305f, neutralVariantChroma = 0.018f
    ),

    /** Casi sin croma: el contenido (las fotos) manda. */
    MONO(
        id = "mono",
        labelRes = com.vivid.app.R.string.palette_mono,
        primaryHue = 260f, primaryChroma = 0.020f,
        secondaryHue = 260f, secondaryChroma = 0.014f,
        tertiaryHue = 260f, tertiaryChroma = 0.010f,
        neutralHue = 260f, neutralChroma = 0.004f,
        neutralVariantHue = 260f, neutralVariantChroma = 0.008f
    );

    /** Muestra para el selector de Ajustes (tono 50 del primary). */
    val swatch: Color by lazy(LazyThreadSafetyMode.NONE) {
        vividToneColor(primaryHue, primaryChroma, 50f)
    }

    internal fun ramps(): VividRamps = VividRamps(
        primary = VividTonalRamp(primaryHue, primaryChroma),
        secondary = VividTonalRamp(secondaryHue, secondaryChroma),
        tertiary = VividTonalRamp(tertiaryHue, tertiaryChroma),
        // El rojo de error es señal de sistema: NO depende de la marca, para
        // que un "borrar cuenta" siga leyéndose como peligro en toda paleta.
        error = VividTonalRamp(27f, 0.160f),
        neutral = VividTonalRamp(neutralHue, neutralChroma),
        neutralVariant = VividTonalRamp(neutralVariantHue, neutralVariantChroma)
    )

    companion object {
        val Default = SUNSET

        /** Paleta persistida en preferencias; [Default] si el id no existe. */
        fun fromId(id: String?): VividSeedPalette =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

internal class VividRamps(
    val primary: VividTonalRamp,
    val secondary: VividTonalRamp,
    val tertiary: VividTonalRamp,
    val error: VividTonalRamp,
    val neutral: VividTonalRamp,
    val neutralVariant: VividTonalRamp
)

/**
 * [ColorScheme] completo para [palette]. Los tonos son los mismos que emite
 * `generate_vivid_palette.py`, así que SUNSET generado y [VividBrandColors]
 * coinciden (SUNSET se atajan de todas formas para ahorrar el cálculo).
 */
internal fun vividSeedColorScheme(palette: VividSeedPalette, darkTheme: Boolean): ColorScheme {
    if (palette == VividSeedPalette.SUNSET) {
        return if (darkTheme) vividDarkColorScheme() else vividLightColorScheme()
    }
    val r = palette.ramps()
    return if (darkTheme) {
        darkColorScheme(
            primary = r.primary[80],
            onPrimary = r.primary[20],
            primaryContainer = r.primary[30],
            onPrimaryContainer = r.primary[90],
            secondary = r.secondary[80],
            onSecondary = r.secondary[20],
            secondaryContainer = r.secondary[30],
            onSecondaryContainer = r.secondary[90],
            tertiary = r.tertiary[80],
            onTertiary = r.tertiary[20],
            tertiaryContainer = r.tertiary[30],
            onTertiaryContainer = r.tertiary[90],
            error = r.error[80],
            onError = r.error[20],
            errorContainer = r.error[30],
            onErrorContainer = r.error[90],
            background = r.neutral[6],
            onBackground = r.neutral[90],
            surface = r.neutral[6],
            onSurface = r.neutral[90],
            surfaceVariant = r.neutralVariant[30],
            onSurfaceVariant = r.neutralVariant[80],
            surfaceTint = r.primary[80],
            outline = r.neutralVariant[60],
            outlineVariant = r.neutralVariant[30],
            surfaceBright = r.neutral[24],
            surfaceDim = r.neutral[6],
            surfaceContainer = r.neutral[12],
            surfaceContainerHigh = r.neutral[17],
            surfaceContainerHighest = r.neutral[22],
            surfaceContainerLow = r.neutral[10],
            surfaceContainerLowest = r.neutral[4],
            inverseSurface = r.neutral[90],
            inverseOnSurface = r.neutral[20],
            inversePrimary = r.primary[40],
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = r.primary[40],
            onPrimary = r.primary[100],
            primaryContainer = r.primary[90],
            onPrimaryContainer = r.primary[10],
            secondary = r.secondary[40],
            onSecondary = r.secondary[100],
            secondaryContainer = r.secondary[90],
            onSecondaryContainer = r.secondary[10],
            tertiary = r.tertiary[40],
            onTertiary = r.tertiary[100],
            tertiaryContainer = r.tertiary[90],
            onTertiaryContainer = r.tertiary[10],
            error = r.error[40],
            onError = r.error[100],
            errorContainer = r.error[90],
            onErrorContainer = r.error[10],
            background = r.neutral[99],
            onBackground = r.neutral[10],
            surface = r.neutral[99],
            onSurface = r.neutral[10],
            surfaceVariant = r.neutralVariant[90],
            onSurfaceVariant = r.neutralVariant[30],
            surfaceTint = r.primary[40],
            outline = r.neutralVariant[50],
            outlineVariant = r.neutralVariant[80],
            surfaceBright = r.neutral[98],
            surfaceDim = r.neutral[87],
            surfaceContainer = r.neutral[94],
            surfaceContainerHigh = r.neutral[92],
            surfaceContainerHighest = r.neutral[90],
            surfaceContainerLow = r.neutral[96],
            surfaceContainerLowest = r.neutral[100],
            inverseSurface = r.neutral[20],
            inverseOnSurface = r.neutral[95],
            inversePrimary = r.primary[80],
            scrim = Color.Black
        )
    }
}

// ── AMOLED / negro puro ──────────────────────────────────────────────────────

/**
 * Convierte un esquema **oscuro** en su variante AMOLED: fondo y superficie
 * base en negro puro (#000000), que en un panel OLED significa píxel apagado —
 * negro real y menos batería.
 *
 * El detalle importante es lo que NO se pone en negro: si todos los
 * contenedores fueran #000000 la jerarquía desaparece (una tarjeta sobre el
 * fondo dejaría de verse). Los contenedores se recalculan 2–4 tonos por encima
 * del negro conservando **el matiz y el croma del esquema original**, así que
 * la variante AMOLED del color dinámico sigue teniendo el tinte del wallpaper
 * y la de "Vivid Sunset" sigue siendo cálida.
 *
 * Solo tiene sentido en oscuro; en claro se devuelve el esquema tal cual.
 */
internal fun ColorScheme.toAmoled(darkTheme: Boolean): ColorScheme {
    if (!darkTheme) return this

    // Matiz/croma de los neutros del esquema activo: se toma de un contenedor
    // alto porque es donde el tinte es más legible (en un #171211 el croma es
    // ínfimo pero está, y perderlo haría la app gris azulada).
    val (_, hue, chroma) = surfaceContainerHigh.okLch()
    val neutral = VividTonalRamp(hue, chroma)

    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = neutral[4],
        surfaceContainer = neutral[7],
        surfaceContainerHigh = neutral[11],
        surfaceContainerHighest = neutral[15],
        surfaceBright = neutral[18]
    )
}

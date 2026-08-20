package com.vivid.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Armonización de color al estilo Material You, sin dependencias externas.
 *
 * Problema real: con color dinámico activado el esquema sale del wallpaper,
 * pero hay colores que son *producto*, no tema — el corazón del like, el
 * anillo de historias, el punto de "en línea". Si se dejan fijos chocan con
 * un wallpaper verde; si se sustituyen por `primary` dejan de significar algo
 * (un like y un botón primario se ven idénticos).
 *
 * La solución de Material (`Blend.harmonize`) es girar el matiz del color de
 * marca *hacia* el matiz del sistema, como mucho 15°: sigue siendo rojo, pero
 * es "el rojo de este teléfono". Aquí se hace lo mismo en OkLCh, que es
 * perceptualmente uniforme, conservando luminosidad y croma del original.
 */

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

/** sRGB → OkLab (L, a, b). */
private fun Color.toOkLab(): Triple<Float, Float, Float> {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    return Triple(
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    )
}

/** OkLab → sRGB, con recorte a gamut. */
private fun okLabToColor(okL: Float, okA: Float, okB: Float, alpha: Float): Color {
    val l = (okL + 0.3963377774f * okA + 0.2158037573f * okB).let { it * it * it }
    val m = (okL - 0.1055613458f * okA - 0.0638541728f * okB).let { it * it * it }
    val s = (okL - 0.0894841775f * okA - 1.2914855480f * okB).let { it * it * it }
    fun ch(v: Float) = linearToSrgb(v).coerceIn(0f, 1f)
    return Color(
        red = ch(+4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s),
        green = ch(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s),
        blue = ch(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s),
        alpha = alpha
    )
}

/**
 * Devuelve este color girado hasta [maxShiftDegrees] grados de matiz hacia
 * [source], conservando su luminosidad y su croma.
 *
 * Si el color es prácticamente acromático (gris, blanco, negro) se devuelve
 * tal cual: girar el matiz de un gris no significa nada.
 */
fun Color.harmonizeWith(source: Color, maxShiftDegrees: Float = 15f): Color {
    val (l, a, b) = toOkLab()
    val chroma = hypot(a, b)
    if (chroma < 0.01f) return this

    val (_, sa, sb) = source.toOkLab()
    if (hypot(sa, sb) < 0.01f) return this

    val hue = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
    val sourceHue = Math.toDegrees(atan2(sb, sa).toDouble()).toFloat()

    // Diferencia angular con signo, normalizada a (-180, 180].
    var delta = (sourceHue - hue + 540f) % 360f - 180f
    delta = sign(delta) * minOf(abs(delta) * 0.5f, maxShiftDegrees)

    val rad = Math.toRadians((hue + delta).toDouble()).toFloat()
    return okLabToColor(l, chroma * cos(rad), chroma * sin(rad), alpha)
}

/**
 * Acentos de producto ya resueltos para el tema activo (claro/oscuro) y
 * armonizados con el esquema en uso.
 *
 * Se leen con `LocalVividAccents.current` en vez de importar
 * [VividAccentColors] directamente: así el mismo composable funciona con la
 * paleta de marca, con color dinámico y en un `@Preview`.
 */
@Immutable
data class VividAccents(
    val like: Color,
    val storyRing: List<Color>,
    val verified: Color,
    val online: Color,
    val live: Color
) {
    companion object {
        /** Acentos para [seed] (normalmente `colorScheme.primary`). */
        fun harmonizedTo(seed: Color, darkTheme: Boolean): VividAccents = VividAccents(
            like = (if (darkTheme) VividAccentColors.LikeDark else VividAccentColors.Like)
                .harmonizeWith(seed),
            storyRing = listOf(
                VividAccentColors.StoryRingStart.harmonizeWith(seed),
                VividAccentColors.StoryRingMid.harmonizeWith(seed),
                VividAccentColors.StoryRingEnd.harmonizeWith(seed)
            ),
            verified = VividAccentColors.Verified.harmonizeWith(seed),
            online = VividAccentColors.Online.harmonizeWith(seed),
            live = VividAccentColors.Live.harmonizeWith(seed)
        )
    }
}

/**
 * Valor por defecto sin armonizar: solo aplica fuera de [VividTheme]
 * (por ejemplo en un preview que renderiza un componente suelto).
 */
val LocalVividAccents = staticCompositionLocalOf {
    VividAccents.harmonizedTo(VividBrandColors.Primary, darkTheme = false)
}

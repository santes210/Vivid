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

/** Este color en OkLCh: `(L, hue en grados, croma)`. */
internal fun Color.okLch(): Triple<Float, Float, Float> {
    val (l, a, b) = toOkLab()
    return Triple(l, Math.toDegrees(atan2(b, a).toDouble()).toFloat(), hypot(a, b))
}

/**
 * Devuelve este color girado hasta [maxShiftDegrees] grados de matiz hacia
 * [source], conservando su luminosidad y su croma.
 *
 * [strength] (0..1) escala la mezcla: 1 = armonización completa al estilo
 * `Blend.harmonize` de Material, 0 = el color intacto. Sirve para los acentos
 * cuyo significado *es* su matiz — ver [VividAccents.harmonizedTo].
 *
 * Si el color es prácticamente acromático (gris, blanco, negro) se devuelve
 * tal cual: girar el matiz de un gris no significa nada.
 */
fun Color.harmonizeWith(
    source: Color,
    maxShiftDegrees: Float = 15f,
    strength: Float = 1f
): Color {
    if (strength <= 0f) return this
    val (l, a, b) = toOkLab()
    val chroma = hypot(a, b)
    if (chroma < 0.01f) return this

    val (_, sa, sb) = source.toOkLab()
    if (hypot(sa, sb) < 0.01f) return this

    val hue = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
    val sourceHue = Math.toDegrees(atan2(sb, sa).toDouble()).toFloat()

    // Diferencia angular con signo, normalizada a (-180, 180].
    var delta = (sourceHue - hue + 540f) % 360f - 180f
    delta = sign(delta) * minOf(abs(delta) * 0.5f, maxShiftDegrees) * strength.coerceIn(0f, 1f)

    val rad = Math.toRadians((hue + delta).toDouble()).toFloat()
    return okLabToColor(l, chroma * cos(rad), chroma * sin(rad), alpha)
}

// ── Contraste ────────────────────────────────────────────────────────────────

/** Luminancia relativa WCAG 2.x. */
private fun Color.relativeLuminance(): Float {
    val r = srgbToLinear(red)
    val g = srgbToLinear(green)
    val b = srgbToLinear(blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

/**
 * Ratio de contraste WCAG entre dos colores opacos (1..21).
 *
 * Referencias que se usan en la app: 3.0 para gráficos y elementos de UI no
 * textuales (WCAG 1.4.11 — el corazón del like, el punto de "en línea", el
 * anillo de historia), 4.5 para texto normal.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * Aclara u oscurece este color (solo la L de OkLab, matiz y croma intactos)
 * hasta alcanzar [minRatio] de contraste contra [background].
 *
 * Es la red de seguridad de los acentos: con color dinámico o en AMOLED, un
 * acento perfectamente válido sobre #171211 puede quedarse corto sobre otro
 * fondo. Antes de rendirse prueba en las dos direcciones y se queda con la que
 * más contraste consiga.
 */
fun Color.ensureContrastAgainst(background: Color, minRatio: Float = 3f): Color {
    if (contrastRatio(this, background) >= minRatio) return this
    val (l, a, b) = toOkLab()
    // Sobre fondo oscuro se aclara; sobre fondo claro se oscurece.
    val goLighter = background.relativeLuminance() < 0.5f
    var best = this
    var bestRatio = contrastRatio(this, background)
    var step = 0.02f
    while (step <= 0.6f) {
        val newL = (if (goLighter) l + step else l - step).coerceIn(0f, 1f)
        val candidate = okLabToColor(newL, a, b, alpha)
        val ratio = contrastRatio(candidate, background)
        if (ratio > bestRatio) {
            best = candidate
            bestRatio = ratio
        }
        if (ratio >= minRatio) return candidate
        step += 0.02f
    }
    return best
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
        /**
         * Cuánto se deja girar cada acento hacia el color del sistema.
         *
         * No todos los acentos valen lo mismo. El **like** y el **live** son
         * rojos *semánticos*: el usuario espera un corazón rojo, y con un
         * wallpaper verde una armonización al 100 % lo empuja hacia el marrón
         * apagado (rojo + 15° hacia verde, que es exactamente el peor sitio
         * del espacio de color). Se mezclan solo un 30 %: quedan ~4-5° de
         * giro, suficiente para que no chirríen, insuficiente para dejar de
         * ser rojos.
         *
         * El anillo de historias, el verificado y el "en línea" sí son
         * decorativos/convencionales y se armonizan del todo.
         */
        private const val SEMANTIC_RED_STRENGTH = 0.3f

        /**
         * Acentos para [seed] (normalmente `colorScheme.primary`).
         *
         * [surface] es el fondo sobre el que se pintan: se usa para garantizar
         * 3:1 de contraste (WCAG 1.4.11, elementos gráficos). Importa sobre
         * todo en AMOLED y con esquemas dinámicos muy claros, donde un acento
         * calibrado para #171211 puede perderse.
         */
        fun harmonizedTo(
            seed: Color,
            darkTheme: Boolean,
            surface: Color = if (darkTheme) Color.Black else Color.White
        ): VividAccents {
            fun accent(base: Color, strength: Float = 1f, minRatio: Float = 3f) =
                base.harmonizeWith(seed, strength = strength)
                    .ensureContrastAgainst(surface, minRatio)

            return VividAccents(
                like = accent(
                    if (darkTheme) VividAccentColors.LikeDark else VividAccentColors.Like,
                    strength = SEMANTIC_RED_STRENGTH
                ),
                // El degradado del anillo no necesita contraste AA propio (va
                // sobre la foto del avatar, no sobre la superficie), pero sí
                // debe distinguirse del fondo del feed: 1.6:1 basta y evita
                // que el gradiente se aplane si un tramo topa con el límite.
                storyRing = listOf(
                    accent(VividAccentColors.StoryRingStart, minRatio = 1.6f),
                    accent(VividAccentColors.StoryRingMid, minRatio = 1.6f),
                    accent(VividAccentColors.StoryRingEnd, minRatio = 1.6f)
                ),
                verified = accent(VividAccentColors.Verified),
                online = accent(VividAccentColors.Online),
                live = accent(VividAccentColors.Live, strength = SEMANTIC_RED_STRENGTH)
            )
        }
    }
}

/**
 * Valor por defecto sin armonizar: solo aplica fuera de [VividTheme]
 * (por ejemplo en un preview que renderiza un componente suelto).
 */
val LocalVividAccents = staticCompositionLocalOf {
    VividAccents.harmonizedTo(VividBrandColors.Primary, darkTheme = false)
}

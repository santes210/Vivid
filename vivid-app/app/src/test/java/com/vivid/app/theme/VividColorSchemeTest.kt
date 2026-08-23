package com.vivid.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Contratos del sistema de color: rampas tonales, esquemas por semilla, modo
 * AMOLED y armonización de los acentos de producto.
 *
 * Estos tests existen porque los errores de color no revientan la app: se
 * quedan ahí, en forma de texto gris sobre gris o de un corazón marrón, hasta
 * que alguien los ve en una captura.
 */
class VividColorSchemeTest {

    private fun hueOf(color: Color): Float = color.okLch().second

    private fun hueDelta(a: Color, b: Color): Float =
        abs((hueOf(a) - hueOf(b) + 540f) % 360f - 180f)

    // ── Rampas tonales ───────────────────────────────────────────────────────

    @Test
    fun `los extremos de la rampa son negro y blanco`() {
        assertEquals(Color.Black, vividToneColor(6f, 0.23f, 0f))
        assertEquals(Color.White, vividToneColor(6f, 0.23f, 100f))
    }

    @Test
    fun `la rampa es monotona en luminosidad`() {
        val ramp = VividTonalRamp(150f, 0.15f)
        var previous = -1f
        listOf(4, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99).forEach { tone ->
            val l = ramp[tone].okLch().first
            assertTrue("el tono $tone no sube en L", l > previous)
            previous = l
        }
    }

    @Test
    fun `la rampa conserva el matiz al aclarar`() {
        val ramp = VividTonalRamp(250f, 0.16f)
        // El clásico bug de HSL: el azul viraba a morado en los tonos claros.
        assertTrue(hueDelta(ramp[30], ramp[80]) < 8f)
    }

    // ── Esquemas por semilla ────────────────────────────────────────────────

    @Test
    fun `sunset generado coincide con la paleta de marca precalculada`() {
        assertEquals(
            VividBrandColorsDark.Primary,
            vividSeedColorScheme(VividSeedPalette.SUNSET, darkTheme = true).primary
        )
        assertEquals(
            VividBrandColors.Primary,
            vividSeedColorScheme(VividSeedPalette.SUNSET, darkTheme = false).primary
        )
    }

    @Test
    fun `cada semilla cumple los contrastes minimos de M3`() {
        VividSeedPalette.entries.forEach { palette ->
            listOf(true, false).forEach { dark ->
                val s = vividSeedColorScheme(palette, dark)
                val where = "${palette.id} dark=$dark"
                // 4.5:1 = WCAG AA para texto normal.
                assertTrue("$where onSurface", contrastRatio(s.onSurface, s.surface) >= 4.5f)
                assertTrue("$where onPrimary", contrastRatio(s.onPrimary, s.primary) >= 4.5f)
                assertTrue(
                    "$where onPrimaryContainer",
                    contrastRatio(s.onPrimaryContainer, s.primaryContainer) >= 4.5f
                )
                assertTrue(
                    "$where onSecondaryContainer",
                    contrastRatio(s.onSecondaryContainer, s.secondaryContainer) >= 4.5f
                )
                assertTrue(
                    "$where onTertiaryContainer",
                    contrastRatio(s.onTertiaryContainer, s.tertiaryContainer) >= 4.5f
                )
                assertTrue("$where onError", contrastRatio(s.onError, s.error) >= 4.5f)
                // 3:1 = WCAG AA para elementos gráficos (bordes, iconos).
                assertTrue("$where outline", contrastRatio(s.outline, s.surface) >= 3f)
            }
        }
    }

    @Test
    fun `el rojo de error no depende de la semilla`() {
        val ocean = vividSeedColorScheme(VividSeedPalette.OCEAN, darkTheme = false)
        val forest = vividSeedColorScheme(VividSeedPalette.FOREST, darkTheme = false)
        assertEquals(ocean.error, forest.error)
    }

    @Test
    fun `las muestras del selector se distinguen entre si`() {
        val swatches = VividSeedPalette.entries.map { it.swatch }
        assertEquals(swatches.size, swatches.toSet().size)
    }

    // ── AMOLED ───────────────────────────────────────────────────────────────

    @Test
    fun `amoled pone el fondo en negro puro`() {
        val amoled = vividSeedColorScheme(VividSeedPalette.SUNSET, darkTheme = true)
            .toAmoled(darkTheme = true)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color.Black, amoled.surface)
        assertEquals(Color.Black, amoled.surfaceContainerLowest)
        assertEquals(Color.Black, amoled.surfaceDim)
    }

    @Test
    fun `amoled conserva la jerarquia de contenedores`() {
        val amoled = vividSeedColorScheme(VividSeedPalette.SUNSET, darkTheme = true)
            .toAmoled(darkTheme = true)
        val ladder = listOf(
            amoled.surfaceContainerLowest,
            amoled.surfaceContainerLow,
            amoled.surfaceContainer,
            amoled.surfaceContainerHigh,
            amoled.surfaceContainerHighest,
            amoled.surfaceBright
        )
        ladder.zipWithNext { lower, higher ->
            assertTrue(
                "los contenedores AMOLED deben subir de tono",
                higher.okLch().first > lower.okLch().first
            )
        }
        // Un contenedor negro sobre fondo negro sería invisible.
        assertNotEquals(Color.Black, amoled.surfaceContainer)
    }

    @Test
    fun `amoled mantiene el texto legible`() {
        VividSeedPalette.entries.forEach { palette ->
            val amoled = vividSeedColorScheme(palette, darkTheme = true).toAmoled(true)
            assertTrue(
                "${palette.id}: onSurface sobre negro",
                contrastRatio(amoled.onSurface, amoled.surface) >= 4.5f
            )
            assertTrue(
                "${palette.id}: onSurfaceVariant sobre contenedor",
                contrastRatio(amoled.onSurfaceVariant, amoled.surfaceContainer) >= 4.5f
            )
        }
    }

    @Test
    fun `amoled no toca el tema claro`() {
        val light = vividSeedColorScheme(VividSeedPalette.SUNSET, darkTheme = false)
        assertSame(light, light.toAmoled(darkTheme = false))
    }

    // ── Armonización de acentos ─────────────────────────────────────────────

    @Test
    fun `el like apenas gira hacia un sistema verde`() {
        val green = Color(0xFF2E7D32)
        val accents = VividAccents.harmonizedTo(green, darkTheme = true, surface = Color.Black)
        val original = VividAccentColors.LikeDark
        val shift = hueDelta(accents.like, original)
        // Armonización al 30 %: como mucho 15° * 0.5 * 0.3 = 4.5° de giro.
        assertTrue("el like giró $shift°, debería ser <= 5°", shift <= 5f)
        // Y sigue siendo rojo/rosa, no marrón: el matiz se queda en el sector
        // cálido de OkLCh (aprox. -30°..40°).
        val hue = hueOf(accents.like)
        assertTrue("el like dejó de ser rojo (hue $hue)", hue > -30f && hue < 40f)
    }

    @Test
    fun `los acentos decorativos si se armonizan del todo`() {
        val green = Color(0xFF2E7D32)
        val accents = VividAccents.harmonizedTo(green, darkTheme = true, surface = Color.Black)
        val verifiedShift = hueDelta(accents.verified, VividAccentColors.Verified)
        assertTrue(
            "el verificado debería girar más que el like (giró $verifiedShift°)",
            verifiedShift > 5f
        )
    }

    @Test
    fun `los acentos mantienen 3 a 1 contra la superficie`() {
        listOf(
            Triple(Color.Black, true, "amoled"),
            Triple(Color(0xFF171211), true, "oscuro cálido"),
            Triple(Color.White, false, "claro")
        ).forEach { (surface, dark, name) ->
            val accents = VividAccents.harmonizedTo(
                seed = Color(0xFF2E7D32),
                darkTheme = dark,
                surface = surface
            )
            listOf(
                "like" to accents.like,
                "verified" to accents.verified,
                "online" to accents.online,
                "live" to accents.live
            ).forEach { (label, color) ->
                assertTrue(
                    "$label sobre $name: ${contrastRatio(color, surface)}",
                    contrastRatio(color, surface) >= 3f
                )
            }
        }
    }

    @Test
    fun `sin armonizacion con semilla acromatica`() {
        // Un sistema en escala de grises no tiene matiz al que girar.
        val accents = VividAccents.harmonizedTo(Color(0xFF808080), darkTheme = false)
        assertEquals(0f, hueDelta(accents.verified, VividAccentColors.Verified), 0.01f)
    }

    @Test
    fun `ensureContrastAgainst solo mueve la luminosidad`() {
        val faint = Color(0xFF101010)
        val fixed = faint.ensureContrastAgainst(Color.Black, minRatio = 3f)
        assertTrue(contrastRatio(fixed, Color.Black) >= 3f)
    }

    @Test
    fun `contrastRatio conoce sus extremos`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.05f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.001f)
    }
}

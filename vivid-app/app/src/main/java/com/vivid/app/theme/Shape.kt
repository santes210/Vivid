package com.vivid.app.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.PillShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material You 3 Expressive — Shape system (formas con propósito y variedad).
 *
 * La pieza central es el **squircle** (superelipse) — curvatura continua, la silueta
 * que distingue Expressive del M3 clásico. Pero Expressive NO es "todo squircle":
 * es un **vocabulario de formas** (estrellas, ráfagas, pétalos, corazones, diamantes,
 * festones, píldoras, dientes de sierra) cada una con su rol.
 *
 * Jerarquía y vocabulario Vivid:
 *  - Containers principales (cards, botones, campos): squircles 4 / 6.
 *  - Acentos y momentos hero: estrellas, ráfagas, diamantes, corazones.
 *  - Indicadores y badges: píldoras, pétalos/gotas.
 *  - Bordes decorativos / shelves: festones y dientes de sierra.
 *  - Bottom sheets / modales: solo esquinas superiores redondeadas.
 *  - Avatares y contenido: circulares / squircles.
 */

val VividShapes = Shapes(
    extraSmall = SquircleShape(4f),      // chips, badges
    small      = SquircleShape(4f),      // multimedia, controls pequeños
    medium     = SquircleShape(4f),      // tarjetas secundarias, campos reposo
    large      = SquircleShape(4f),      // botones primarios, cards secundarias
    extraLarge = SquircleShape(3.5f)     // hero cards, FABs, bottom sheets
)

/**
 * Tokens expresivos adicionales para uso directo en pantallas.
 * Usa estos tokens (no RoundedCornerShape literales) para consistencia.
 */
object VividExpressiveShapes {
    // ---------- Squircles (contenedores) ----------
    val Squircle: Shape = SquircleShape(4f)              // estándar
    val SquircleSoft: Shape = SquircleShape(4.5f)        // más redondeado
    val SquircleSharp: Shape = SquircleShape(6f)         // casi cuadrado
    val SquircleHero: Shape = SquircleShape(3.5f)        // más suave, hero
    val SquircleTight: Shape = SquircleShape(3f)         // muy suave / casi círculo

    // ---------- Campos y controles ----------
    val FieldResting: Shape = SquircleShape(4f)
    val FieldFocused: Shape = SquircleShape(4f)
    val SearchBar: Shape = SquircleShape(4f)
    val SearchBarActive: Shape = SquircleShape(3.5f)
    val SegmentedControl: Shape = SquircleShape(4f)

    // ---------- Botones ----------
    val PrimaryButton: Shape = SquircleShape(4f)
    val PrimaryButtonPressed: Shape = SquircleShape(6f)
    val SecondaryButton: Shape = SquircleShape(4f)
    val IconButton: Shape = CircleShape
    val ButtonMorphResting: Shape = SquircleShape(4f)
    val ButtonMorphPressed: Shape = SquircleShape(6f)

    // ---------- Tarjetas ----------
    val SmallCard: Shape = SquircleShape(4f)
    val MediumCard: Shape = SquircleShape(4f)
    val HeroCard: Shape = SquircleShape(3.5f)
    val HeroCardLarge: Shape = SquircleShape(3.5f)

    // ---------- Superficies ----------
    val BottomSheet: Shape = RoundedCornerTop(28.dp)
    val Modal: Shape = SquircleShape(4f)
    val Dialog: Shape = SquircleShape(4f)

    // ---------- Contenido ----------
    val Media: Shape = SquircleShape(4f)
    val MediaLarge: Shape = SquircleShape(4f)
    val Avatar: Shape = CircleShape
    val AvatarSquircle: Shape = SquircleShape(4f)
    val StoryRing: Shape = CircleShape

    // ---------- Seleccionado / morph ----------
    val ChipSelected: Shape = SquircleShape(4f)
    val ChipUnselected: Shape = SquircleShape(4f)
    val SelectedContainer: Shape = SquircleShape(4f)
    val SelectedContainerActive: Shape = SquircleShape(3.5f)
    val IconResting: Shape = CircleShape
    val IconChecked: Shape = SquircleShape(4f)
    val IconPressed: Shape = SquircleShape(4f)

    // ---------- Píldoras ----------
    val Pill: Shape = PillShape                       // stadium
    val PillSmall: Shape = PillShape
    val TabPill: Shape = PillShape

    // ---------- Estrellas ----------
    val Star: Shape = StarShape(5, 0.42f)
    val StarSharp: Shape = StarShape(5, 0.30f)
    val StarSoft: Shape = StarShape(5, 0.55f)
    val StarFour: Shape = StarShape(4, 0.40f)         // "chispa" de 4 puntas
    val StarSix: Shape = StarShape(6, 0.45f)

    // ---------- Ráfagas / soles ----------
    val Burst: Shape = BurstShape(12, 0.35f)
    val BurstFine: Shape = BurstShape(16, 0.30f)
    val BurstCoarse: Shape = BurstShape(8, 0.42f)
    val Sparkle: Shape = StarShape(4, 0.30f)          // chispa fina

    // ---------- Pétalos / gotas ----------
    val DropDown: Shape = TeardropShape(0)            // punta abajo
    val DropUp: Shape = TeardropShape(1)              // punta arriba
    val DropRight: Shape = TeardropShape(2)
    val DropLeft: Shape = TeardropShape(3)
    val Petal: Shape = TeardropShape(0)

    // ---------- Corazones / gemas ----------
    val Heart: Shape = HeartShape()
    val Diamond: Shape = DiamondShape(3f)
    val DiamondSharp: Shape = DiamondShape(2f)
    val DiamondSoft: Shape = DiamondShape(4f)

    // ---------- Festones / sierras (bordes decorativos) ----------
    val Scallop: Shape = ScallopShape(5, onTop = true)
    val ScallopBottom: Shape = ScallopShape(5, onTop = false)
    val ScallopWave: Shape = ScallopShape(7, onTop = true)
    val Sawtooth: Shape = SawtoothShape(6, onTop = true)
    val SawtoothBottom: Shape = SawtoothShape(6, onTop = false)
}

/** Forma con solo esquinas superiores redondeadas (bottom sheets / modales). */
fun RoundedCornerTop(cornerSize: Dp): Shape =
    androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = cornerSize,
        topEnd = cornerSize
    )

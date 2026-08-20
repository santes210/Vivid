package com.vivid.app.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon

/**
 * Material 3 Expressive — Shape system (formas con propósito).
 *
 * Las formas comunican jerarquía, no solo “todo redondeado”. Cada radio
 * tiene un rol semántico.
 *
 * Jerarquía Vivid:
 *  - Campos (TextField, Search): 16–20dp → cómodo al tacto, focus cambia a 20dp
 *  - Botones primarios: 20–24dp → prominentes, pressed 12dp para feedback
 *  - Tarjetas pequeñas / secundaria: 16dp
 *  - Tarjetas hero / destacadas: 28–32dp
 *  - Bottom sheets / modales: 28dp solo arriba (expressive squircle)
 *  - Avatares: circulares
 *  - Contenido multimedia (imagen/video): 12–16dp
 *  - Elementos seleccionados: forma más expresiva (squircle 20→28)
 */
val VividShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, badges
    small      = RoundedCornerShape(12.dp),  // multimedia, segmented controls pequeños
    medium     = RoundedCornerShape(16.dp),  // tarjetas secundarias, campos reposo
    large      = RoundedCornerShape(20.dp),  // botones primarios, campos focus, cards secundarias elevadas
    extraLarge = RoundedCornerShape(28.dp)   // hero cards, FABs extendidos, bottom sheets
)

/**
 * Tokens expresivos adicionales para uso directo en pantallas.
 * Usar estos en vez de RoundedCornerShape literales para consistencia.
 */
object VividExpressiveShapes {
    // Campos y controles
    val FieldResting: Shape = RoundedCornerShape(16.dp)
    val FieldFocused: Shape = RoundedCornerShape(20.dp)
    val SearchBar: Shape = RoundedCornerShape(24.dp)
    val SearchBarActive: Shape = RoundedCornerShape(28.dp)

    // Botones
    val PrimaryButton: Shape = RoundedCornerShape(20.dp)
    val PrimaryButtonPressed: Shape = RoundedCornerShape(12.dp)
    val SecondaryButton: Shape = RoundedCornerShape(16.dp)
    val IconButton: Shape = CircleShape
    val SegmentedControl: Shape = RoundedCornerShape(12.dp)

    // Tarjetas
    val SmallCard: Shape = RoundedCornerShape(16.dp)
    val MediumCard: Shape = RoundedCornerShape(20.dp)
    val HeroCard: Shape = RoundedCornerShape(28.dp)
    val HeroCardLarge: Shape = RoundedCornerShape(32.dp)

    // Superficies
    val BottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Modal: Shape = RoundedCornerShape(28.dp)
    val Dialog: Shape = RoundedCornerShape(28.dp)

    // Contenido
    val Media: Shape = RoundedCornerShape(12.dp)
    val MediaLarge: Shape = RoundedCornerShape(16.dp)
    val Avatar: Shape = CircleShape
    val AvatarSquircle: Shape = RoundedCornerShape(12.dp) // para estado seleccionado

    // Seleccionado / expressive morph
    val ChipSelected: Shape = RoundedCornerShape(12.dp)
    val ChipUnselected: Shape = RoundedCornerShape(16.dp)
    val SelectedContainer: Shape = RoundedCornerShape(20.dp)
    val SelectedContainerActive: Shape = RoundedCornerShape(28.dp)
}

/**
 * Las 35 formas de Material 3 Expressive ([MaterialShapes]) y su uso en Vivid.
 *
 * No son `RoundedCornerShape` disfrazados: cada una es un `RoundedPolygon` de
 * androidx.graphics.shapes, así que además de recortar se pueden **interpolar
 * entre sí** (ver `ui/components/VividMorphShape.kt`), que es justo lo que
 * hace que el sistema de formas sea un sistema y no un catálogo de sellos.
 *
 * [Catalog] tiene las 35 con nombre legible; el preview
 * `MaterialShapesCatalogPreview` (`ui/preview/ShapeCatalogPreview.kt`) las
 * pinta todas para poder elegir sin compilar la app.
 *
 * Debajo del catálogo están los **roles**: el resto de la app usa
 * `VividMaterialShapes.Celebration`, no `MaterialShapes.Burst`. Si mañana la
 * celebración pasa a ser `SoftBurst`, se cambia aquí y cambia en todos lados.
 *
 * Todas son experimentales en material3 1.5.0-alpha: este archivo es el único
 * punto de la app que las construye.
 */
object VividMaterialShapes {

    // ── Roles semánticos (lo que usa la app) ─────────────────────────────

    /** Galleta de 9 puntas: estados vacíos, ilustraciones de sección. */
    val EmptyStateContainer: Shape = MaterialShapes.Cookie9Sided.toShape()

    /** Explosión: confirmaciones, celebraciones, badges de logro. */
    val Celebration: Shape = MaterialShapes.Burst.toShape()

    /** Trébol: avatar destacado (historia nueva, cuenta recomendada). */
    val AvatarHighlight: Shape = MaterialShapes.Clover4Leaf.toShape()

    /** Pastilla suave: chips y contenedores de estado. */
    val SoftPill: Shape = MaterialShapes.Pill.toShape()

    /** Corazón: reventón del doble toque, badge de "más gustado". */
    val Like: Shape = MaterialShapes.Heart.toShape()

    /** Flor: logros y momentos "hero" del perfil. */
    val Achievement: Shape = MaterialShapes.Flower.toShape()

    /** Gema: contenido destacado / verificado. */
    val Featured: Shape = MaterialShapes.Gem.toShape()

    // ── Polígonos para morphing (sin convertir a Shape) ──────────────────
    // rememberVividMorph() necesita el RoundedPolygon, no el Shape ya cerrado.

    /** Reposo de un control que se transforma al pulsarse. */
    val MorphResting: RoundedPolygon = MaterialShapes.Circle

    /** Estado pulsado / activo del mismo control. */
    val MorphPressed: RoundedPolygon = MaterialShapes.Cookie9Sided

    /** Avatar sin historias nuevas. */
    val AvatarResting: RoundedPolygon = MaterialShapes.Circle

    /** Avatar con historia sin ver. */
    val AvatarActive: RoundedPolygon = MaterialShapes.Clover4Leaf

    /**
     * Secuencia de polígonos del indicador de carga de Vivid.
     *
     * `LoadingIndicator` transforma una forma en la siguiente; con esta lista
     * la espera "sabe" a Vivid en vez de al indicador por defecto.
     */
    val LoadingSequence: List<RoundedPolygon> = listOf(
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Flower,
        MaterialShapes.Sunny,
        MaterialShapes.Pentagon
    )

    // ── Catálogo completo (35) ───────────────────────────────────────────

    /**
     * Las 35 formas con su nombre, en el orden de la referencia oficial.
     *
     * Sirve para el preview del catálogo y para pantallas de depuración; la
     * app de producción debería consumir los roles de arriba.
     */
    val Catalog: List<Pair<String, RoundedPolygon>> = listOf(
        "Circle" to MaterialShapes.Circle,
        "Square" to MaterialShapes.Square,
        "Slanted" to MaterialShapes.Slanted,
        "Arch" to MaterialShapes.Arch,
        "SemiCircle" to MaterialShapes.SemiCircle,
        "Oval" to MaterialShapes.Oval,
        "Pill" to MaterialShapes.Pill,
        "Triangle" to MaterialShapes.Triangle,
        "Arrow" to MaterialShapes.Arrow,
        "Fan" to MaterialShapes.Fan,
        "Diamond" to MaterialShapes.Diamond,
        "ClamShell" to MaterialShapes.ClamShell,
        "Pentagon" to MaterialShapes.Pentagon,
        "Gem" to MaterialShapes.Gem,
        "Sunny" to MaterialShapes.Sunny,
        "VerySunny" to MaterialShapes.VerySunny,
        "Cookie4Sided" to MaterialShapes.Cookie4Sided,
        "Cookie6Sided" to MaterialShapes.Cookie6Sided,
        "Cookie7Sided" to MaterialShapes.Cookie7Sided,
        "Cookie9Sided" to MaterialShapes.Cookie9Sided,
        "Cookie12Sided" to MaterialShapes.Cookie12Sided,
        "Ghostish" to MaterialShapes.Ghostish,
        "Clover4Leaf" to MaterialShapes.Clover4Leaf,
        "Clover8Leaf" to MaterialShapes.Clover8Leaf,
        "Burst" to MaterialShapes.Burst,
        "SoftBurst" to MaterialShapes.SoftBurst,
        "Boom" to MaterialShapes.Boom,
        "SoftBoom" to MaterialShapes.SoftBoom,
        "Flower" to MaterialShapes.Flower,
        "Puffy" to MaterialShapes.Puffy,
        "PuffyDiamond" to MaterialShapes.PuffyDiamond,
        "PixelCircle" to MaterialShapes.PixelCircle,
        "PixelTriangle" to MaterialShapes.PixelTriangle,
        "Bun" to MaterialShapes.Bun,
        "Heart" to MaterialShapes.Heart
    )
}

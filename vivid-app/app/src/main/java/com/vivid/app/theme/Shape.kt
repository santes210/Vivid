package com.vivid.app.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material You 3 Expressive — Shape system (formas con propósito).
 *
 * Inspirado en M3 Expressive (2025): las formas comunican jerarquía,
 * no solo “todo redondeado”. Cada radio tiene un rol semántico.
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

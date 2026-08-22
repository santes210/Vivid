package com.vivid.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.VividMaterialShapes

/**
 * Insignia de cuenta verificada con forma de **gema** (`MaterialShapes.Gem`
 * vía el rol [VividMaterialShapes.Featured]) en vez del clásico check redondo.
 *
 * Es un detalle chico, pero la silueta angular + el acento de producto
 * armonizado lo hacen sentir "premium" y, sobre todo, coherente con el resto
 * del sistema de formas de Material Expressive (no es un sello importado de
 * otra librería de iconos).
 *
 * Uso típico: junto al nombre para el que tiene `isVerified = true`, o como
 * *overlay* en una esquina del avatar:
 *
 * ```
 * Row(verticalAlignment = Alignment.CenterVertically) {
 *     Text(displayName, …)
 *     VividVerifiedBadge()
 * }
 * ```
 *
 * Por defecto no se muestra en ningún sitio: las pantallas lo pintan solo si
 * el usuario trae `isVerified` (p. ej. el perfil lo lee de Firestore). Así el
 * componente está listo para cuando llegue el dato sin cambiar el comportamiento
 * actual.
 *
 * @param size    Lado del contenedor (la gema es cuadrada). 16dp junto a texto,
 *                20-24dp como overlay de avatar.
 */
@Composable
fun VividVerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    contentDescription: String? = "Cuenta verificada"
) {
    val verifiedColor = LocalVividAccents.current.verified
    Surface(
        modifier = modifier.size(size),
        shape = VividMaterialShapes.Featured,
        color = verifiedColor,
        contentColor = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(size * 0.62f)
            )
        }
    }
}

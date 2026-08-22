package com.vivid.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado de Vivid.
 *
 * Las pantallas no inventan `8.dp` / `16.dp` sueltos para gaps y paddings
 * de ritmo: usan un token de aquí. Así el aire vertical es el mismo en
 * feed, perfil, create y ajustes, y mañana se puede abrir o cerrar el
 * lenguaje en un solo sitio.
 *
 * Ritmo (4 → 8 → 12 → 16 → 24 → 32 → 48):
 *
 *   xxs   4   gap interno de un chip, skeleton entre líneas
 *   xs    8   icono ↔ texto en un botón, padding compacto
 *   s    12   avatar ↔ nombre, filas de una lista
 *   m    16   padding de pantalla, entre bloques de una card
 *   l    24   entre secciones, padding de un hero
 *   xl   32   estados vacíos, respiro de una columna
 *   xxl  48   full-screen loading / empty
 *
 * Fuera de esta escala (y a propósito): `2.dp` hairlines, paddings
 * intermedios (6 / 10 / 14 / 18 / 20 / 28), tamaños de componente
 * (`Modifier.size(44.dp)`), elevaciones, grosores de borde y radios
 * (esos viven en [VividExpressiveShapes]).
 */
object VividSpace {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

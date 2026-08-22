package com.vivid.app.theme

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens de feedback (snackbars y diálogos).
 *
 * Viven fuera de Compose para poder testearlos sin Android y para que
 * [VividSnackbarHost] / [com.vivid.app.ui.components.VividAlertDialog] no
 * inventen radios ni anchos distintos en cada pantalla.
 *
 *  - Forma: [VividExpressiveShapes.Dialog] (28 dp), la misma en snackbar y
 *    AlertDialog. En tableta el snackbar no se estira: [SnackbarMaxWidth].
 *  - Color: `surfaceContainerHigh` / `onSurface` (no inverseSurface). Así
 *    el aviso habla el mismo idioma tonal que las cards y los sheets.
 */
object VividFeedbackTokens {
    val DialogCorner: Dp = 28.dp
    val SnackbarMaxWidth: Dp = 600.dp
    val DialogShape: Shape = VividExpressiveShapes.Dialog
    val SnackbarShape: Shape = VividExpressiveShapes.Snackbar
}

package com.vivid.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vivid.app.theme.VividFeedbackTokens
import com.vivid.app.theme.VividMaterialShapes

/**
 * Tono semántico de un [VividAlertDialog].
 *
 *  - [Standard]: diálogo informativo o de confirmación neutra.
 *  - [Success]: confirmaciones tipo "Publicado". Si no se pasa `icon`,
 *    se pinta [VividCelebrationIcon] (Burst de Material Shapes).
 *  - [Destructive]: borrados y cierres de sesión. No cambia la forma;
 *    el color de los botones lo pone la pantalla.
 */
enum class VividDialogTone {
    Standard,
    Success,
    Destructive
}

/**
 * AlertDialog estándar de Vivid.
 *
 * Misma forma que el snackbar ([VividFeedbackTokens.DialogShape], 28 dp) y
 * el mismo par tonal `surfaceContainerHigh` / `onSurface`. Es un drop-in
 * de `AlertDialog`: acepta los mismos parámetros nombrados para no romper
 * las llamadas existentes.
 */
@Composable
fun VividAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = VividFeedbackTokens.DialogShape,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
    tone: VividDialogTone = VividDialogTone.Standard
) {
    val scheme = MaterialTheme.colorScheme
    val resolvedContainer = if (containerColor == Color.Unspecified) {
        scheme.surfaceContainerHigh
    } else {
        containerColor
    }
    val resolvedIconColor = if (iconContentColor == Color.Unspecified) {
        when (tone) {
            VividDialogTone.Success -> scheme.onTertiaryContainer
            VividDialogTone.Destructive -> scheme.error
            VividDialogTone.Standard -> scheme.secondary
        }
    } else {
        iconContentColor
    }
    val resolvedTitleColor = if (titleContentColor == Color.Unspecified) {
        scheme.onSurface
    } else {
        titleContentColor
    }
    val resolvedTextColor = if (textContentColor == Color.Unspecified) {
        scheme.onSurface
    } else {
        textContentColor
    }
    val resolvedIcon = icon ?: if (tone == VividDialogTone.Success) {
        { VividCelebrationIcon() }
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = resolvedIcon,
        title = title,
        text = text,
        shape = shape,
        containerColor = resolvedContainer,
        iconContentColor = resolvedIconColor,
        titleContentColor = resolvedTitleColor,
        textContentColor = resolvedTextColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}

/**
 * Icono de celebración: polígono Burst ([VividMaterialShapes.Celebration])
 * con un check. Para diálogos de éxito ("Publicado", "Listo") y overlays
 * de publicación.
 */
@Composable
fun VividCelebrationIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Surface(
        shape = VividMaterialShapes.Celebration,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

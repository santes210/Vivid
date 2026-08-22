package com.vivid.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vivid.app.theme.VividFeedbackTokens
import com.vivid.app.theme.VividSpace

/**
 * SnackbarHost estándar de Vivid.
 *
 * Sustituye a `SnackbarHost` suelto: forma [VividFeedbackTokens.SnackbarShape]
 * (28 dp), `surfaceContainerHigh` / `onSurface`, y
 * [VividFeedbackTokens.SnackbarMaxWidth] para que en tableta no se convierta
 * en una franja de borde a borde.
 */
@Composable
fun VividSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (SnackbarData) -> Unit = { data -> VividSnackbar(data) }
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = snackbar
    )
}

@Composable
fun VividSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Snackbar(
            snackbarData = snackbarData,
            modifier = Modifier
                .padding(horizontal = VividSpace.m, vertical = VividSpace.s)
                .widthIn(max = VividFeedbackTokens.SnackbarMaxWidth)
                .fillMaxWidth(),
            shape = VividFeedbackTokens.SnackbarShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

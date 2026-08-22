package com.vivid.app.presentation.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivid.app.ui.preview.VividPreview
import com.vivid.app.ui.preview.VividPreviewA11y
import com.vivid.app.ui.preview.VividPreviewSurface
import com.vivid.app.theme.VividSpace

/**
 * Previews de la conversación.
 *
 * Una conversación es justo el sitio donde más se nota un fallo de diseño
 * (agrupación de burbujas, mensaje larguísimo, reacción encima del texto) y el
 * más caro de reproducir a mano: hacían falta dos cuentas y mensajes reales.
 */

private fun message(
    text: String,
    mine: Boolean,
    reaction: String = "",
    edited: Boolean = false
) = Message(
    id = text.hashCode().toString(),
    text = text,
    senderId = if (mine) "me" else "other",
    timestamp = System.currentTimeMillis(),
    isRead = true,
    isDelivered = true,
    reaction = reaction,
    lastEditedAt = if (edited) System.currentTimeMillis() else 0L
)

@VividPreviewA11y
@Composable
private fun ConversationPreview() {
    VividPreviewSurface(padding = 12) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
        ) {
            MessageBubble(
                message = message("¿Vas mañana a la playa?", mine = false),
                isMine = false,
                isGroupStart = true,
                isGroupEnd = false
            )
            MessageBubble(
                message = message("Salgo temprano, como a las 6", mine = false),
                isMine = false,
                isGroupStart = false,
                isGroupEnd = true
            )
            MessageBubble(
                message = message(
                    "Va, llevo la cámara y el trípode. Si el cielo aguanta despejado " +
                        "deberíamos alcanzar la hora dorada sin problema.",
                    mine = true,
                    reaction = "🔥"
                ),
                isMine = true,
                isGroupStart = true,
                isGroupEnd = true
            )
        }
    }
}

@VividPreview
@Composable
private fun TypingIndicatorPreview() {
    VividPreviewSurface {
        TypingIndicatorBubble()
    }
}

@VividPreview
@Composable
private fun DateHeaderPreview() {
    VividPreviewSurface {
        DateHeaderPill(timestamp = System.currentTimeMillis())
    }
}

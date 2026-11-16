package com.vivid.app.presentation.reels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.ui.components.UserAvatar

@Composable
internal fun ShareRecipientPicker(
    recipients: List<ShareRecipient>,
    selected: Set<String>,
    statuses: Map<String, ReelShareStatus>,
    onToggle: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(recipients, key = { it.chatId }) { person ->
            val status = statuses[person.chatId]
            val chosen = person.chatId in selected
            Surface(
                shape = VividExpressiveShapes.MediumCard,
                color = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.width(112.dp).toggleable(
                    value = chosen,
                    enabled = status != ReelShareStatus.SENDING && status != ReelShareStatus.SENT,
                    role = Role.Checkbox,
                    onValueChange = { onToggle(person.chatId) }
                )
            ) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        UserAvatar(imageUrl = person.avatarUrl, name = person.name, size = 52.dp, contentDescription = null)
                        if (chosen) Surface(color = MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape,
                            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp)) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.padding(3.dp))
                        }
                    }
                    Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(when (status) {
                        ReelShareStatus.SENDING -> R.string.chat_sending
                        ReelShareStatus.SENT -> R.string.chat_sent
                        ReelShareStatus.FAILED -> R.string.reel_share_send_error
                        else -> if (chosen) R.string.share_selected else R.string.share_select
                    }), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
        }
    }
}

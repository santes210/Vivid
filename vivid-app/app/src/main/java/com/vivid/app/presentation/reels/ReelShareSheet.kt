package com.vivid.app.presentation.reels

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.ui.components.UserAvatar

data class ShareContent(val link: String, val caption: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentShareSheet(
    content: ShareContent,
    speed: Float = 1f,
    onSpeed: (Float) -> Unit = {},
    muted: Boolean = false,
    onMuted: (Boolean) -> Unit = {},
    repeat: Boolean = false,
    onRepeat: (Boolean) -> Unit = {},
    onRestart: () -> Unit = {},
    playbackControls: Boolean = true,
    onDismiss: () -> Unit,
    viewModel: ReelShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val recipients by viewModel.recipients.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.loadFailed.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    var notice by remember { mutableStateOf<String?>(null) }
    val link = content.link
    val text = stringResource(R.string.content_share_message, link)
    DisposableEffect(viewModel) {
        viewModel.loadRecipients()
        onDispose { viewModel.stopRecipients() }
    }

    fun share(whatsapp: Boolean) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            if (whatsapp) {
                try {
                    context.startActivity(Intent(intent).setPackage("com.whatsapp"))
                } catch (_: android.content.ActivityNotFoundException) {
                    context.startActivity(Intent(intent).setPackage("com.whatsapp.w4b"))
                }
            } else context.startActivity(Intent.createChooser(intent, context.getString(R.string.content_share_title)))
        } catch (_: Exception) {
            notice = context.getString(if (whatsapp) R.string.reel_share_whatsapp_missing else R.string.reel_share_external_error)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.content_share_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.content_share_link_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.reel_share_people), style = MaterialTheme.typography.titleMedium)
            when {
                loading -> CircularProgressIndicator(Modifier.size(28.dp))
                failed -> TextButton(onClick = viewModel::loadRecipients) {
                    Text(stringResource(R.string.reel_share_load_error))
                }
                recipients.isEmpty() -> Text(stringResource(R.string.reel_share_no_chats))
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recipients, key = { it.chatId }) { person ->
                        val status = statuses["$link:${person.chatId}"]
                        FilledTonalButton(
                            onClick = { viewModel.send(link, person, text) },
                            enabled = status != ReelShareStatus.SENDING && status != ReelShareStatus.SENT,
                            shape = VividExpressiveShapes.MediumCard,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                UserAvatar(imageUrl = "", name = person.name, size = 44.dp)
                                Text(person.name, maxLines = 1, modifier = Modifier.widthIn(max = 120.dp))
                                Text(stringResource(when (status) {
                                    ReelShareStatus.SENDING -> R.string.chat_sending
                                    ReelShareStatus.SENT -> R.string.chat_sent
                                    ReelShareStatus.FAILED -> R.string.reel_share_send_error
                                    else -> R.string.reel_share_send
                                }), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    FilledTonalButton(onClick = { share(true) }) {
                        Icon(Icons.Default.Chat, null); Spacer(Modifier.width(8.dp)); Text("WhatsApp")
                    }
                }
                item {
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(link))
                        notice = context.getString(R.string.reel_share_copied)
                    }) {
                        Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reel_share_copy))
                    }
                }
                item {
                    FilledTonalButton(onClick = { share(false) }) {
                        Icon(Icons.Default.MoreHoriz, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reel_share_more))
                    }
                }
            }
            if (playbackControls) Surface(shape = VividExpressiveShapes.MediumCard, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.reel_share_speed), style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0.5f, 1f, 1.5f, 2f)) { value ->
                            FilterChip(selected = speed == value, onClick = { onSpeed(value) }, label = { Text("${value}×") })
                        }
                    }
                }
            }
            Text(stringResource(R.string.reel_tools_title), style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playbackControls) item {
                    FilterChip(selected = muted, onClick = { onMuted(!muted) },
                        label = { Text(stringResource(R.string.reel_tools_mute)) },
                        leadingIcon = { Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null) })
                }
                if (playbackControls) item {
                    FilterChip(selected = repeat, onClick = { onRepeat(!repeat) },
                        label = { Text(stringResource(R.string.reel_tools_repeat)) },
                        leadingIcon = { Icon(Icons.Default.Repeat, null) })
                }
                if (playbackControls) item {
                    AssistChip(onClick = onRestart, label = { Text(stringResource(R.string.reel_tools_restart)) },
                        leadingIcon = { Icon(Icons.Default.Replay, null) })
                }
                if (content.caption.isNotBlank()) {
                    item {
                        AssistChip(onClick = {
                            clipboard.setText(AnnotatedString(content.caption))
                            notice = context.getString(R.string.reel_tools_text_copied)
                        }, label = { Text(stringResource(R.string.reel_tools_copy_text)) },
                            leadingIcon = { Icon(Icons.Default.TextFields, null) })
                    }
                }
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

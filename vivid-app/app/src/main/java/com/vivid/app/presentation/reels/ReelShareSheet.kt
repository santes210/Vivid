package com.vivid.app.presentation.reels

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes

data class ShareContent(val link: String, val caption: String, val ownerId: String = "")

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
    onDeleted: () -> Unit = {},
    viewModel: ReelShareViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val recipients by viewModel.recipients.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val failed by viewModel.loadFailed.collectAsState()
    val allStatuses by viewModel.statuses.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val link = content.link
    var selected by remember(link) { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable(link) { mutableStateOf("") }
    var note by rememberSaveable(link) { mutableStateOf("") }
    var notice by remember(link) { mutableStateOf<String?>(null) }
    val text = stringResource(R.string.content_share_message, link)
    val statuses = recipients.mapNotNull { person -> allStatuses["$link:${person.chatId}"]?.let { person.chatId to it } }.toMap()
    val selectedPeople = recipients.filter { it.chatId in selected }
    val visiblePeople = recipients.filter { ShareSelection.matches(it.name, query) }
    val failedIds = ShareSelection.retryable(selected, statuses)
    val sentCount = selected.count { statuses[it] == ReelShareStatus.SENT }
    val pendingCount = selected.count { statuses[it] == ReelShareStatus.SENDING }
    val canSend = selectedPeople.any { statuses[it.chatId] != ReelShareStatus.SENT && statuses[it.chatId] != ReelShareStatus.SENDING }
    val frozen = link in notes
    val noteValue = notes[link] ?: note
    DisposableEffect(viewModel) {
        viewModel.loadRecipients()
        onDispose { viewModel.stopRecipients() }
    }

    fun share(whatsapp: Boolean) {
        // The optional note is for Vivid recipients; external apps retain the reference-only payload.
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        try {
            if (whatsapp) {
                try { context.startActivity(Intent(intent).setPackage("com.whatsapp")) }
                catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(intent).setPackage("com.whatsapp.w4b")) }
            } else context.startActivity(Intent.createChooser(intent, context.getString(R.string.content_share_title)))
        } catch (_: Exception) {
            notice = context.getString(if (whatsapp) R.string.reel_share_whatsapp_missing else R.string.reel_share_external_error)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
            .imePadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.content_share_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.share_choose_people), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.share_close)) }
                }
                if (content.caption.isNotBlank()) Surface(shape = VividExpressiveShapes.MediumCard, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null)
                        Spacer(Modifier.width(12.dp))
                        Text(content.caption, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val reference = Regex("vivid://(post|reel)/([A-Za-z0-9_-]{1,128})").matchEntire(content.link)
                if (reference != null) com.vivid.app.presentation.common.DeleteContentAction(
                    collection = if (reference.groupValues[1] == "post") "posts" else "reels",
                    id = reference.groupValues[2], ownerId = content.ownerId,
                    onDeleted = { onDismiss(); onDeleted() })
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = VividExpressiveShapes.SearchBar,
                    label = { Text(stringResource(R.string.share_search)) }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.share_clear_search)) } })
                Text(stringResource(R.string.share_search_scope), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                when {
                    loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    failed -> TextButton(onClick = viewModel::loadRecipients) { Text(stringResource(R.string.reel_share_load_error)) }
                    recipients.isEmpty() -> Text(stringResource(R.string.reel_share_no_chats))
                    visiblePeople.isEmpty() -> Text(stringResource(R.string.share_no_matches))
                    else -> ShareRecipientPicker(visiblePeople, selected, statuses) { id -> selected = if (id in selected) selected - id else selected + id }
                }
                if (selectedPeople.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.share_selected_count, selectedPeople.size), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.share_clear_selection)) }
                    }
                    Text(selectedPeople.joinToString { it.name }, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = noteValue, onValueChange = { note = it.take(ShareSelection.MAX_NOTE_LENGTH) },
                        enabled = !frozen, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                        shape = VividExpressiveShapes.MediumCard,
                        label = { Text(stringResource(R.string.share_note)) },
                        supportingText = { Text(if (frozen) stringResource(R.string.share_note_frozen) else "${noteValue.length}/${ShareSelection.MAX_NOTE_LENGTH}") })
                    if (sentCount + pendingCount + failedIds.size > 0) {
                        Text(stringResource(R.string.share_summary, sentCount, pendingCount, failedIds.size), style = MaterialTheme.typography.bodySmall)
                    }
                    if (failedIds.isNotEmpty()) TextButton(onClick = { viewModel.sendSelection(link, failedIds, text, noteValue) }) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.share_retry_failed))
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.share_apps), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { ShareAction(Icons.Default.Chat, "WhatsApp") { share(true) } }
                    item { ShareAction(Icons.Default.ContentCopy, stringResource(R.string.reel_share_copy)) {
                        clipboard.setText(AnnotatedString(link)); notice = context.getString(R.string.reel_share_copied)
                    } }
                    item { ShareAction(Icons.Default.MoreHoriz, stringResource(R.string.reel_share_more)) { share(false) } }
                    if (content.caption.isNotBlank()) item { ShareAction(Icons.Default.TextFields, stringResource(R.string.reel_tools_copy_text)) {
                        clipboard.setText(AnnotatedString(content.caption)); notice = context.getString(R.string.reel_tools_text_copied)
                    } }
                }
                if (playbackControls) Surface(shape = VividExpressiveShapes.MediumCard, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.reel_share_speed), style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(0.5f, 1f, 1.5f, 2f)) { value ->
                                FilterChip(selected = speed == value, onClick = { onSpeed(value) }, label = { Text("${value}×") })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = muted, onClick = { onMuted(!muted) }, label = { Text(stringResource(R.string.reel_tools_mute)) }) }
                            item { FilterChip(selected = repeat, onClick = { onRepeat(!repeat) }, label = { Text(stringResource(R.string.reel_tools_repeat)) }) }
                            item { AssistChip(onClick = onRestart, label = { Text(stringResource(R.string.reel_tools_restart)) }) }
                        }
                    }
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.content_share_link_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 4.dp) {
                Button(onClick = { viewModel.sendSelection(link, selected, text, noteValue) }, enabled = canSend && !loading && !failed,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 56.dp),
                    shape = VividExpressiveShapes.SearchBar) {
                    Icon(Icons.Default.Send, null); Spacer(Modifier.width(10.dp))
                    Text(if (selectedPeople.isEmpty()) stringResource(R.string.share_choose_people)
                        else stringResource(R.string.share_send_count, selectedPeople.count { statuses[it.chatId] != ReelShareStatus.SENT && statuses[it.chatId] != ReelShareStatus.SENDING }))
                }
            }
        }
    }
}

@Composable
private fun ShareAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.width(90.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = VividExpressiveShapes.MediumCard, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
    }
}

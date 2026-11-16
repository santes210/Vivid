package com.vivid.app.presentation.create

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ReelDraftPanel(uid: String, current: ReelDraft?, publishing: Boolean,
    onRestore: (ReelDraft) -> Unit, onSavedPaths: (ReelDraft) -> Unit, onDiscard: () -> Unit,
    onBusy: (Boolean) -> Unit, forPost: Boolean = false) {
    val context = LocalContext.current
    val store = remember(uid, forPost) { ReelDraftStore(context, uid, forPost) }
    val scope = rememberCoroutineScope()
    var stored by remember(uid, forPost) { mutableStateOf<ReelDraft?>(null) }
    var busy by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(uid, forPost, retry) {
        busy = true
        try { stored = store.load(); failed = false }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    SideEffect { onBusy(busy) }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (forPost) R.string.post_draft_title else R.string.reel_draft_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (forPost) R.string.post_draft_local else R.string.reel_draft_local), style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Text(stringResource(R.string.reel_draft_failed), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !busy && !publishing, onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
            }
            if (saved) Text(stringResource(R.string.reel_draft_saved), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(enabled = current != null && !busy && !publishing, onClick = {
                // Replacing an existing saved draft is explicit and confirmed.
                confirmation = "save"
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reel_draft_save)) }
            if (stored != null) OutlinedButton(enabled = !busy && !publishing, onClick = { confirmation = "restore" },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reel_draft_restore)) }
            if (stored != null || failed) TextButton(enabled = !busy && !publishing, onClick = { confirmation = "delete" }) {
                Text(stringResource(R.string.reel_draft_delete))
            }
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(stringResource(if (forPost) R.string.post_draft_title else R.string.reel_draft_title)) },
            text = { Text(stringResource(when (action) {
                "restore" -> R.string.reel_draft_restore_confirm
                "delete" -> R.string.reel_draft_delete_confirm
                else -> R.string.reel_draft_save_confirm
            })) },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                if (action == "restore") {
                    stored?.let(onRestore)
                } else {
                    val snapshot = current
                    busy = true
                    scope.launch {
                        try {
                            if (action == "delete") {
                                store.delete(); stored = null; saved = false; onDiscard()
                            } else if (snapshot != null) {
                                store.save(snapshot)
                                stored = store.load()
                                stored?.let(onSavedPaths)
                                saved = true
                            }
                            failed = false
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }
            }) { Text(stringResource(R.string.reel_draft_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

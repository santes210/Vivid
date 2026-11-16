package com.vivid.app.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.app.R

@Composable
internal fun SavedCollectionDialog(initialName: String, names: List<String>,
    onDismiss: () -> Unit, onSave: (String, (Boolean) -> Unit) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.saved_organize)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.saved_collection_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40); failed = false },
                    enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.saved_collection_name)) })
                TextButton(enabled = !busy, onClick = { name = "" }) { Text(stringResource(R.string.saved_unfiled)) }
                names.forEach { existing -> TextButton(enabled = !busy, onClick = { name = existing }) { Text(existing) } }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.saved_collection_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { FilledTonalButton(enabled = !busy, onClick = {
            busy = true; failed = false
            onSave(name) { success ->
                busy = false
                if (success) onDismiss() else failed = true
            }
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

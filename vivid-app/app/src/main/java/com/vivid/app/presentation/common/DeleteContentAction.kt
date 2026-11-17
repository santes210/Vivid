package com.vivid.app.presentation.common

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.R
import com.vivid.app.ui.components.VividAlertDialog
import com.vivid.app.ui.components.VividDialogTone

/** Author-only affordance; the transaction and Firestore rules independently enforce ownership. */
@Composable
internal fun DeleteContentAction(collection: String, id: String, ownerId: String,
    modifier: Modifier = Modifier, compact: Boolean = false, onDeleted: () -> Unit = {},
    viewModel: DeleteContentViewModel = hiltViewModel()) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    if (ownerId.isBlank() || ownerId != uid) return
    val context = LocalContext.current
    var confirm by remember(collection, id) { mutableStateOf(false) }
    var busy by remember(collection, id) { mutableStateOf(false) }
    var failed by remember(collection, id) { mutableStateOf(false) }
    if (compact) FilledTonalIconButton(onClick = { confirm = true }, modifier = modifier) {
        Icon(Icons.Default.Delete, stringResource(R.string.own_content_delete), tint = MaterialTheme.colorScheme.error)
    } else TextButton(onClick = { confirm = true }, modifier = modifier) {
        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.own_content_delete), color = MaterialTheme.colorScheme.error)
    }
    if (confirm) VividAlertDialog(onDismissRequest = { if (!busy) confirm = false },
        tone = VividDialogTone.Destructive,
        title = { Text(stringResource(R.string.own_content_delete)) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(stringResource(R.string.own_content_delete_confirm))
                if (busy) LinearProgressIndicator()
                if (failed) Text(stringResource(R.string.own_content_delete_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), onClick = {
            busy = true; failed = false
            viewModel.delete(collection, id) { cleaned ->
                busy = false
                if (cleaned == null) failed = true
                else {
                    confirm = false
                    Toast.makeText(context, context.getString(if (cleaned) R.string.own_content_deleted else R.string.own_content_deleted_cleanup), Toast.LENGTH_LONG).show()
                    onDeleted()
                }
            }
        }) { Text(stringResource(R.string.own_content_delete)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } })
}

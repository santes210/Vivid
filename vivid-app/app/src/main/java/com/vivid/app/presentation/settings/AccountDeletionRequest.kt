package com.vivid.app.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.vivid.app.R
import com.vivid.app.ui.components.VividAlertDialog
import com.vivid.app.ui.components.VividSettingsGroup
import com.vivid.app.ui.components.VividSettingsItem

// Existing support address. A request needs manual review and identity verification;
// an email/UID is not authorization to delete an account.
internal const val ACCOUNT_SUPPORT_EMAIL = "poncho2010santes@gmail.com"

/** Entry point for manual support, NOT the incomplete client-side purge method. */
@Composable
internal fun AccountDeletionRequest(
    signedIn: Boolean,
    onOpenEmail: () -> Boolean,
    onCopyRequest: () -> Unit
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var emailUnavailable by rememberSaveable { mutableStateOf(false) }

    VividSettingsGroup(title = stringResource(R.string.account_deletion_group)) {
        VividSettingsItem(
            title = stringResource(R.string.account_deletion_title),
            subtitle = stringResource(R.string.account_deletion_subtitle),
            icon = Icons.Outlined.DeleteOutline,
            onClick = { emailUnavailable = false; showDialog = true }
        )
    }

    if (showDialog) {
        VividAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.account_deletion_request_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.account_deletion_explanation))
                    Text(stringResource(R.string.account_deletion_contact, ACCOUNT_SUPPORT_EMAIL))
                    if (!signedIn) {
                        Text(stringResource(R.string.account_deletion_sign_in))
                    }
                    if (emailUnavailable) {
                        Text(
                            stringResource(R.string.account_deletion_no_email),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = onCopyRequest, enabled = signedIn) {
                        Text(stringResource(R.string.account_deletion_copy))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = signedIn,
                    onClick = {
                        // Opening a mail composer is never reported as a sent request.
                        if (onOpenEmail()) showDialog = false
                        else emailUnavailable = true
                    }
                ) { Text(stringResource(R.string.account_deletion_open_email)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.account_deletion_cancel))
                }
            }
        )
    }
}

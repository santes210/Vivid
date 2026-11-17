package com.vivid.app.presentation.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.vivid.app.domain.repository.SocialUserPreview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.app.R
import com.vivid.app.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileConnectionsSheet(
    uid: String,
    followers: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onMessage: (SocialUserPreview) -> Unit,
    viewModel: ProfileConnectionsViewModel = hiltViewModel()
) {
    val pages by viewModel.pages.collectAsState()
    val state = pages["$uid/$followers"] ?: ConnectionsState()
    LaunchedEffect(uid, followers) {
        if (!state.loaded && !state.loading) viewModel.load(uid, followers)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(stringResource(if (followers) R.string.profile_followers_title else R.string.profile_following_title),
                style = MaterialTheme.typography.headlineSmall)
            TextButton(enabled = !state.loading, onClick = { viewModel.load(uid, followers, refresh = true) }) {
                Text(stringResource(R.string.profile_connections_refresh))
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.users, key = { it.uid }) { user ->
                    val messageDescription = stringResource(R.string.cd_send_message_to, user.displayName.ifBlank { user.username })
                    ListItem(
                        modifier = Modifier.clickable { onDismiss(); onOpenProfile(user.uid) },
                        headlineContent = { Text(user.displayName.ifBlank { user.username }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("@${user.username}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { UserAvatar(imageUrl = user.avatarUrl, name = user.displayName, size = 48.dp) },
                        trailingContent = {
                            if (user.uid != uid) FilledTonalButton(
                                onClick = { onMessage(user) },
                                modifier = Modifier.semantics { contentDescription = messageDescription }
                            ) { Text(stringResource(R.string.action_message)) }
                        }
                    )
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp)) }
                if (state.failed) item {
                    Text(stringResource(R.string.profile_connections_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.load(uid, followers) }) {
                        Text(stringResource(R.string.profile_connections_retry))
                    }
                }
                if (state.loaded && state.users.isEmpty() && !state.loading && !state.failed) item {
                    Text(stringResource(if (followers) R.string.profile_followers_empty else R.string.profile_following_empty))
                }
                if (state.loaded && state.hasMore && !state.loading && !state.failed) item {
                    TextButton(onClick = { viewModel.load(uid, followers) }) {
                        Text(stringResource(R.string.profile_connections_more))
                    }
                }
            }
        }
    }
}

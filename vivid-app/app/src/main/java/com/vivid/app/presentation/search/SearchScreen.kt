package com.vivid.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.vivid.app.R
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost

@Immutable
data class SearchUser(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val isFollowing: Boolean = false
)

@Composable
fun SearchScreen(
    onUserClick: (SearchUser) -> Unit,
    onMessageClick: (SearchUser) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel(),
    blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val query by searchViewModel.query.collectAsState()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val users = searchViewModel.users.collectAsLazyPagingItems()
    val normalized = ExplorePaging.normalizeQuery(query)
    val searching = ExplorePaging.isValidUserQuery(normalized)

    LaunchedEffect(blockedUsersState.userIds, blockedUsersState.isLoaded) {
        if (blockedUsersState.isLoaded) {
            searchViewModel.setBlockedUserIds(blockedUsersState.userIds)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VividOfflineBannerHost()

        OutlinedTextField(
            value = query,
            onValueChange = searchViewModel::setQuery,
            label = { Text(stringResource(R.string.search_people_label)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        when {
            !searching -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.search_min_chars),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            users.loadState.refresh is LoadState.Loading && users.itemCount == 0 -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            users.loadState.refresh is LoadState.Error && users.itemCount == 0 -> {
                val err = users.loadState.refresh as LoadState.Error
                VividErrorState(
                    message = err.error.localizedMessage ?: stringResource(R.string.search_error),
                    onRetry = { users.retry() }
                )
            }
            users.itemCount == 0 -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.search_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn {
                    items(
                        count = users.itemCount,
                        key = users.itemKey { it.uid }
                    ) { index ->
                        val user = users[index] ?: return@items
                        UserSearchItem(
                            user = user,
                            onClick = { onUserClick(user) },
                            onMessageClick = { onMessageClick(user) }
                        )
                    }
                    if (users.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchItem(
    user: SearchUser,
    onClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    val rowCd = stringResource(R.string.cd_user_row, user.displayName, user.username)
    val messageCd = stringResource(R.string.cd_send_message_to, user.displayName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowCd
                role = Role.Button
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            imageUrl = user.avatarUrl,
            name = user.displayName,
            // Ancla de la transición avatar → perfil.
            userId = user.uid,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onMessageClick,
            modifier = Modifier
                .height(36.dp)
                .semantics { contentDescription = messageCd }
        ) {
            Icon(
                Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.action_message))
        }
    }
}

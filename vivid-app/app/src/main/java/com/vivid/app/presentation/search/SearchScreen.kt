package com.vivid.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import com.vivid.app.R
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.explore.ExploreViewModel
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.theme.VividSpace

@Immutable
data class SearchUser(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val isFollowing: Boolean = false
)

/**
 * Pantalla suelta de búsqueda (fuera del tab Explorar).
 *
 * Usa el MISMO buscador inteligente que Explorar — [SmartSearchPanel] con
 * segmentos Todo/Personas/Temas/Posts — para que buscar desde aquí y desde
 * el tab sean la experiencia integrada, no dos búsquedas distintas.
 */
@Composable
fun SearchScreen(
    onUserClick: (SearchUser) -> Unit,
    onMessageClick: (SearchUser) -> Unit,
    onTagClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    searchViewModel: SearchViewModel = hiltViewModel(),
    blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel()
) {
    val query by searchViewModel.query.collectAsState()
    val state by searchViewModel.uiState.collectAsState()
    val history by searchViewModel.history.collectAsState()
    val exploreTags by exploreViewModel.tags.collectAsState()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()

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
                .padding(VividSpace.m),
            singleLine = true
        )

        SmartSearchPanel(
            state = state,
            history = history,
            trending = exploreTags.map { SearchTagResult(it.tag, it.count) },
            onTabChange = searchViewModel::setTab,
            onRecentClick = { raw ->
                searchViewModel.setQuery(raw)
                searchViewModel.recordSearch(raw)
            },
            onRecentRemove = searchViewModel::removeHistory,
            onClearHistory = searchViewModel::clearHistory,
            onTagClick = onTagClick,
            onUserClick = { user ->
                searchViewModel.recordSearch(query)
                onUserClick(user)
            },
            onUserMessage = onMessageClick,
            onPostClick = onPostClick,
            onRetry = searchViewModel::retry
        )
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
            .padding(horizontal = VividSpace.m, vertical = VividSpace.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            imageUrl = user.avatarUrl,
            name = user.displayName,
            // Ancla de la transición avatar → perfil.
            userId = user.uid,
            size = 52.dp
        )

        Spacer(modifier = Modifier.width(VividSpace.m))

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
            Spacer(modifier = Modifier.width(VividSpace.xxs))
            Text(stringResource(R.string.action_message))
        }
    }
}

package com.vivid.app.presentation.search

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.vivid.app.ui.components.VividSearchBar
import com.vivid.app.ui.components.pressPrimaryButtonShape
import com.vivid.app.ui.haptics.rememberVividHaptics
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
 * Usa el MISMO buscador inteligente que Explorar — [SmartSearchPanel] con su
 * `ButtonGroup` de Todo/Personas/Temas/Posts — y la MISMA [VividSearchBar] de
 * Material 3: buscar desde aquí y desde el tab es una sola experiencia
 * integrada, no dos búsquedas distintas. Con el campo colapsado el panel
 * queda debajo (recientes, tendencias o resultados en vivo); al enfocar la
 * barra se expande a pantalla completa con el mismo panel dentro.
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
    val haptics = rememberVividHaptics()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(blockedUsersState.userIds, blockedUsersState.isLoaded) {
        if (blockedUsersState.isLoaded) {
            searchViewModel.setBlockedUserIds(blockedUsersState.userIds)
        }
    }

    val panel: @Composable () -> Unit = {
        SmartSearchPanel(
            state = state,
            history = history,
            trending = exploreTags.map { SearchTagResult(it.tag, it.count) },
            onTabChange = searchViewModel::setTab,
            onRecentClick = { raw ->
                haptics.tick()
                searchViewModel.setQuery(raw)
                searchViewModel.recordSearch(raw)
            },
            onRecentRemove = searchViewModel::removeHistory,
            onClearHistory = searchViewModel::clearHistory,
            onTagClick = onTagClick,
            onUserClick = { user ->
                haptics.tick()
                searchViewModel.recordSearch(query)
                onUserClick(user)
            },
            onUserMessage = { user ->
                haptics.tick()
                onMessageClick(user)
            },
            onPostClick = onPostClick,
            onRetry = searchViewModel::retry
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VividOfflineBannerHost()

        VividSearchBar(
            query = query,
            onQueryChange = searchViewModel::setQuery,
            onSearch = { raw ->
                if (SearchHistory.canRecord(raw)) {
                    searchViewModel.recordSearch(raw)
                }
                searchExpanded = true
            },
            expanded = searchExpanded,
            onExpandedChange = { searchExpanded = it },
            docked = false,
            placeholder = stringResource(R.string.search_people_or_tags)
        ) {
            panel()
        }

        if (!searchExpanded) {
            panel()
        }
    }
}

/**
 * Fila de persona del buscador: card expressiva con la misma presión que los
 * botones anchos del design system (la esquina se comprime 20 → 12 dp al
 * pulsarla, [pressPrimaryButtonShape]) y el avatar anclado para la transición
 * compartida hacia el perfil.
 */
@Composable
fun UserSearchItem(
    user: SearchUser,
    onClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    val haptics = rememberVividHaptics()
    val interactions = remember { MutableInteractionSource() }
    val rowCd = stringResource(R.string.cd_user_row, user.displayName, user.username)
    val messageCd = stringResource(R.string.cd_send_message_to, user.displayName)
    Surface(
        onClick = onClick,
        interactionSource = interactions,
        shape = pressPrimaryButtonShape(interactions),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xxs)
            .semantics(mergeDescendants = true) {
                contentDescription = rowCd
                role = Role.Button
            }
    ) {
        Row(
            modifier = Modifier.padding(VividSpace.s),
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
                onClick = {
                    haptics.tick()
                    onMessageClick()
                },
                modifier = Modifier
                    .padding(end = VividSpace.xxs)
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
}

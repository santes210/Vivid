package com.vivid.app.presentation.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.vivid.app.R
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.presentation.search.SearchHistory
import com.vivid.app.presentation.search.SearchSuggestion
import com.vivid.app.presentation.search.SearchUser
import com.vivid.app.presentation.search.SearchViewModel
import com.vivid.app.presentation.search.UserSearchItem
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.components.VividSearchBar
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.ui.motion.vividSharedElement

private const val TABLET_MIN_WIDTH_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val selectedTag by exploreViewModel.selectedTag.collectAsState()
    val searchQuery by searchViewModel.query.collectAsState()
    val history by searchViewModel.history.collectAsState()

    LaunchedEffect(blockedUsersState.userIds, blockedUsersState.isLoaded) {
        if (blockedUsersState.isLoaded) {
            exploreViewModel.setBlockedUserIds(blockedUsersState.userIds)
            searchViewModel.setBlockedUserIds(blockedUsersState.userIds)
        }
    }

    val haptics = rememberVividHaptics()
    val posts = exploreViewModel.posts.collectAsLazyPagingItems()
    val users = searchViewModel.users.collectAsLazyPagingItems()
    val searching = ExplorePaging.isValidUserQuery(ExplorePaging.normalizeQuery(searchQuery))
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    val suggestions = SearchHistory.suggestions(searchQuery, history)

    fun collapseSearch() {
        searchExpanded = false
    }

    fun applyQuery(raw: String) {
        searchViewModel.setQuery(raw)
        searchViewModel.recordSearch(raw)
        searchExpanded = true
    }

    fun applyTag(tag: String) {
        haptics.tick()
        searchViewModel.setQuery("")
        exploreViewModel.selectTag(tag)
        collapseSearch()
    }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            VividOfflineBannerHost()

            VividSearchBar(
                query = searchQuery,
                onQueryChange = searchViewModel::setQuery,
                onSearch = { raw ->
                    if (SearchHistory.canRecord(raw)) {
                        searchViewModel.recordSearch(raw)
                    }
                    searchExpanded = true
                },
                expanded = searchExpanded,
                onExpandedChange = { next ->
                    searchExpanded = next
                    if (!next) searchViewModel.setQuery("")
                },
                docked = isTablet,
                placeholder = stringResource(R.string.search_people_label)
            ) {
                ExploreSearchPanel(
                    searching = searching,
                    suggestions = suggestions,
                    history = history,
                    users = users,
                    onRecentClick = { query ->
                        haptics.tick()
                        applyQuery(query)
                    },
                    onRecentRemove = searchViewModel::removeHistory,
                    onClearHistory = searchViewModel::clearHistory,
                    onTagClick = ::applyTag,
                    onUserClick = { user ->
                        searchViewModel.recordSearch(searchQuery)
                        onProfileClick(user.uid)
                    }
                )
            }

            if (!searchExpanded || isTablet) {
                ExploreBrowseContent(
                    selectedTag = selectedTag,
                    posts = posts,
                    onSelectTag = { tag ->
                        if (tag != selectedTag) haptics.tick()
                        exploreViewModel.selectTag(tag)
                    },
                    onPostClick = { postId ->
                        haptics.tick()
                        onPostClick(postId)
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ExploreSearchPanel(
    searching: Boolean,
    suggestions: List<SearchSuggestion>,
    history: List<String>,
    users: LazyPagingItems<SearchUser>,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTagClick: (String) -> Unit,
    onUserClick: (SearchUser) -> Unit
) {
    if (!searching) {
        val recents = suggestions.filterIsInstance<SearchSuggestion.Recent>()
        val tags = suggestions.filterIsInstance<SearchSuggestion.Tag>()
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (recents.isNotEmpty()) {
                item(key = "history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.search_recent),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        if (history.isNotEmpty()) {
                            TextButton(onClick = onClearHistory) {
                                Text(stringResource(R.string.search_clear_history))
                            }
                        }
                    }
                }
                items(recents, key = { "recent_${it.query}" }) { recent ->
                    ListItem(
                        headlineContent = { Text(recent.query) },
                        leadingContent = {
                            Icon(Icons.Default.History, contentDescription = null)
                        },
                        trailingContent = {
                            IconButton(onClick = { onRecentRemove(recent.query) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.search_remove_recent)
                                )
                            }
                        },
                        modifier = Modifier.clickable { onRecentClick(recent.query) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (tags.isNotEmpty()) {
                item(key = "tags_header") {
                    Text(
                        stringResource(R.string.search_suggested_tags),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.s)
                    )
                }
                items(tags, key = { "tag_${it.tag}" }) { suggestion ->
                    ListItem(
                        headlineContent = { Text("#${suggestion.tag}") },
                        leadingContent = {
                            Icon(Icons.Default.Tag, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onTagClick(suggestion.tag) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            if (recents.isEmpty() && tags.isEmpty()) {
                item(key = "min_chars") {
                    Text(
                        stringResource(R.string.search_min_chars),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(VividSpace.l),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    when {
        users.loadState.refresh is LoadState.Loading && users.itemCount == 0 -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(VividSpace.xxl),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator(polygons = VividMaterialShapes.LoadingSequence)
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(VividSpace.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = VividMaterialShapes.EmptyStateContainer,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(104.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(VividSpace.m))
                Text(
                    stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                val matchingTags = suggestions.filterIsInstance<SearchSuggestion.Tag>()
                if (matchingTags.isNotEmpty()) {
                    item(key = "inline_tags") {
                        Text(
                            stringResource(R.string.search_suggested_tags),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.s)
                        )
                    }
                    items(matchingTags, key = { "hit_tag_${it.tag}" }) { suggestion ->
                        ListItem(
                            headlineContent = { Text("#${suggestion.tag}") },
                            leadingContent = { Icon(Icons.Default.Tag, contentDescription = null) },
                            modifier = Modifier.clickable { onTagClick(suggestion.tag) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    item(key = "people_divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                items(
                    count = users.itemCount,
                    key = users.itemKey { it.uid }
                ) { index ->
                    val user = users[index] ?: return@items
                    UserSearchItem(
                        user = user,
                        onClick = { onUserClick(user) },
                        onMessageClick = { onUserClick(user) }
                    )
                }
                if (users.loadState.append is LoadState.Loading) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(VividSpace.m),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                Modifier.size(32.dp),
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreBrowseContent(
    selectedTag: String,
    posts: LazyPagingItems<PostData>,
    onSelectTag: (String) -> Unit,
    onPostClick: (String) -> Unit
) {
    // Filtros por tema.
    //
    // Aquí vivía un ButtonGroup de M3 Expressive y fue un error de
    // diseño mío: ButtonGroup está pensado para un grupo PEQUEÑO y
    // FIJO de acciones relacionadas (3-5), que se comprimen entre
    // sí y mandan el sobrante a un menú. Con 8 temas (y la lista
    // puede crecer) en un teléfono no cabe ninguno, todo se va al
    // overflow y el cálculo de anchos del componente revienta en
    // runtime ("ButtonGroup width cannot be unbounded" / crash por
    // densidad). Para una lista de filtros que crece, el patrón de
    // Material es una fila desplazable de chips.
    //
    // Se conservan las mejoras que sí aportaban: háptico al
    // cambiar de filtro y formas expresivas según la selección.
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.s, vertical = VividSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)
    ) {
        items(ExplorePaging.TAGS, key = { it }) { tag ->
            val isSelected = selectedTag == tag
            FilterChip(
                selected = isSelected,
                onClick = { onSelectTag(tag) },
                label = { Text("#$tag") },
                shape = if (isSelected) {
                    VividExpressiveShapes.ChipSelected
                } else {
                    VividExpressiveShapes.ChipUnselected
                },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }

    when {
        posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0 -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator(polygons = VividMaterialShapes.LoadingSequence)
            }
        }
        posts.loadState.refresh is LoadState.Error && posts.itemCount == 0 -> {
            val err = posts.loadState.refresh as LoadState.Error
            VividErrorState(
                message = err.error.localizedMessage ?: stringResource(R.string.explore_error),
                onRetry = { posts.retry() }
            )
        }
        posts.itemCount == 0 -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(VividSpace.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = VividMaterialShapes.EmptyStateContainer,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(112.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(VividSpace.m))
                Text(
                    stringResource(R.string.explore_empty, selectedTag),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(VividSpace.xxs),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xxs),
                verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
            ) {
                items(
                    count = posts.itemCount,
                    key = posts.itemKey { it.id }
                ) { index ->
                    val post = posts[index] ?: return@items
                    val postCd = stringResource(R.string.cd_post_by, post.username)
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .semantics { contentDescription = postCd }
                            .clickable { onPostClick(post.id) },
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        if (post.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = post.imageUrl,
                                contentDescription = postCd,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .vividSharedElement(VividSharedKeys.postImage(post.id)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "#${post.caption.take(10)}",
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                )
                            }
                        }
                    }
                }
                if (posts.loadState.append is LoadState.Loading) {
                    items(3, span = { GridItemSpan(1) }) {
                        Box(
                            modifier = Modifier.aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                Modifier.size(28.dp),
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                        }
                    }
                }
            }
        }
    }
}

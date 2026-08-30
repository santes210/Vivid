package com.vivid.app.presentation.explore

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
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
import com.vivid.app.theme.VividMotion
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividAsyncImage
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBanner
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.components.VividPullToRefreshBox
import com.vivid.app.ui.components.VividSearchBar
import com.vivid.app.ui.components.VividSkeletonGrid
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.ui.components.rememberVividMorph
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.util.Hashtags

private const val TABLET_MIN_WIDTH_DP = 600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    /** Tag preseleccionado al llegar desde un hashtag tocado en un post. */
    initialTag: String = "",
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val selectedTag by exploreViewModel.selectedTag.collectAsState()
    val exploreTags by exploreViewModel.tags.collectAsState()
    val cachedTagPosts by exploreViewModel.cachedPosts.collectAsState()
    val searchQuery by searchViewModel.query.collectAsState()
    val history by searchViewModel.history.collectAsState()

    LaunchedEffect(initialTag) {
        if (initialTag.isNotBlank()) exploreViewModel.selectTag(initialTag)
    }

    LaunchedEffect(selectedTag) {
        exploreViewModel.loadCachedPosts(selectedTag)
    }

    LaunchedEffect(blockedUsersState.userIds, blockedUsersState.isLoaded) {
        if (blockedUsersState.isLoaded) {
            exploreViewModel.setBlockedUserIds(blockedUsersState.userIds)
            searchViewModel.setBlockedUserIds(blockedUsersState.userIds)
        }
    }

    val haptics = rememberVividHaptics()
    val posts = exploreViewModel.posts.collectAsLazyPagingItems()
    val users = searchViewModel.users.collectAsLazyPagingItems()
    val normalizedQuery = ExplorePaging.normalizeQuery(searchQuery)
    val tagQuery = Hashtags.parseQuery(searchQuery)
    val searching = ExplorePaging.isValidUserQuery(normalizedQuery)
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_MIN_WIDTH_DP
    var searchExpanded by rememberSaveable { mutableStateOf(false) }

    val suggestions = SearchHistory.suggestions(searchQuery, history)

    fun collapseSearch() {
        searchExpanded = false
    }

    fun applyQuery(raw: String) {
        val tag = Hashtags.parseQuery(raw)
        if (tag != null) {
            haptics.tick()
            searchViewModel.setQuery("")
            searchViewModel.recordSearch(Hashtags.display(tag))
            exploreViewModel.selectTag(tag)
            collapseSearch()
            return
        }
        searchViewModel.setQuery(raw)
        searchViewModel.recordSearch(raw)
        searchExpanded = true
    }

    fun applyTag(tag: String) {
        haptics.tick()
        searchViewModel.setQuery("")
        searchViewModel.recordSearch(Hashtags.display(tag))
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
                    val tag = Hashtags.parseQuery(raw) ?: tagQuery
                    if (tag != null) {
                        applyTag(tag)
                    } else {
                        if (SearchHistory.canRecord(raw)) {
                            searchViewModel.recordSearch(raw)
                        }
                        searchExpanded = true
                    }
                },
                expanded = searchExpanded,
                onExpandedChange = { next ->
                    searchExpanded = next
                    if (!next) searchViewModel.setQuery("")
                },
                docked = isTablet,
                placeholder = stringResource(R.string.search_people_or_tags)
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
                    tags = exploreTags,
                    cachedPosts = cachedTagPosts,
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

@OptIn(ExperimentalLayoutApi::class)
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
                    val isTag = Hashtags.parseQuery(recent.query) != null
                    ListItem(
                        headlineContent = { Text(recent.query) },
                        leadingContent = {
                            Icon(
                                if (isTag) ExploreTopics.icon(
                                    Hashtags.parseQuery(recent.query).orEmpty()
                                ) else Icons.Default.History,
                                contentDescription = null
                            )
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
                item(key = "tags_flow") {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                        horizontalArrangement = Arrangement.spacedBy(VividSpace.xs),
                        verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
                    ) {
                        tags.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { onTagClick(suggestion.tag) },
                                label = { Text(Hashtags.display(suggestion.tag)) },
                                icon = {
                                    Icon(
                                        ExploreTopics.icon(suggestion.tag),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = VividExpressiveShapes.ChipUnselected
                            )
                        }
                    }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                repeat(5) { VividSkeletonListItem() }
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
                    item(key = "inline_tags_flow") {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                            horizontalArrangement = Arrangement.spacedBy(VividSpace.xs),
                            verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
                        ) {
                            matchingTags.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { onTagClick(suggestion.tag) },
                                    label = { Text(Hashtags.display(suggestion.tag)) },
                                    icon = {
                                        Icon(
                                            ExploreTopics.icon(suggestion.tag),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = VividExpressiveShapes.ChipUnselected
                                )
                            }
                        }
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
private fun ColumnScope.ExploreBrowseContent(
    selectedTag: String,
    tags: List<ExploreTag>,
    cachedPosts: List<PostData>,
    posts: LazyPagingItems<PostData>,
    onSelectTag: (String) -> Unit,
    onPostClick: (String) -> Unit
) {
    val chipTags = remember(tags, selectedTag) {
        if (tags.any { it.tag == selectedTag }) tags
        else listOf(ExploreTag(selectedTag, 0)) + tags
    }
    val chipListState = rememberLazyListState()
    LaunchedEffect(selectedTag, chipTags) {
        val index = chipTags.indexOfFirst { it.tag == selectedTag }
        if (index >= 0) {
            runCatching { chipListState.animateScrollToItem(index) }
        }
    }

    LazyRow(
        state = chipListState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = VividSpace.s, vertical = VividSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)
    ) {
        items(chipTags, key = { it.tag }) { tagItem ->
            ExploreTagChip(
                tag = tagItem.tag,
                selected = selectedTag == tagItem.tag,
                count = tagItem.count,
                onClick = { onSelectTag(tagItem.tag) }
            )
        }
    }

    ExploreTopicHeader(tag = selectedTag)

    val refreshing = posts.loadState.refresh is LoadState.Loading && posts.itemCount > 0
    VividPullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { posts.refresh() },
        modifier = Modifier.weight(1f).fillMaxWidth()
    ) {
        when {
            posts.loadState.refresh is LoadState.Loading && posts.itemCount == 0 -> {
                Box(modifier = Modifier.fillMaxSize().padding(VividSpace.xxs)) {
                    VividSkeletonGrid(columns = 3, count = 18)
                }
            }
            posts.loadState.refresh is LoadState.Error && posts.itemCount == 0 -> {
                if (cachedPosts.isNotEmpty()) {
                    VividOfflineBanner(message = stringResource(R.string.explore_offline_cached))
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(VividSpace.xxs),
                        horizontalArrangement = Arrangement.spacedBy(VividSpace.xxs),
                        verticalItemSpacing = VividSpace.xxs
                    ) {
                        itemsIndexed(cachedPosts, key = { _, post -> post.id }) { index, post ->
                            ExplorePostCard(
                                post = post,
                                aspect = exploreCellAspectRatio(index),
                                onClick = { onPostClick(post.id) }
                            )
                        }
                    }
                } else {
                    val err = posts.loadState.refresh as LoadState.Error
                    VividErrorState(
                        message = err.error.localizedMessage ?: stringResource(R.string.explore_error),
                        onRetry = { posts.retry() }
                    )
                }
            }
            posts.itemCount == 0 -> {
                ExploreEmptyTagState(
                    selectedTag = selectedTag,
                    onSelectTag = onSelectTag
                )
            }
            else -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(VividSpace.xxs),
                    horizontalArrangement = Arrangement.spacedBy(VividSpace.xxs),
                    verticalItemSpacing = VividSpace.xxs
                ) {
                    items(
                        count = posts.itemCount,
                        key = posts.itemKey { it.id }
                    ) { index ->
                        val post = posts[index] ?: return@items
                        ExplorePostCard(
                            post = post,
                            aspect = exploreCellAspectRatio(index),
                            onClick = { onPostClick(post.id) }
                        )
                    }
                    if (posts.loadState.append is LoadState.Loading) {
                        items(3) {
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
}

@Composable
internal fun ExploreTagChip(
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0
) {
    val corner by animateDpAsState(
        targetValue = if (selected) 12.dp else 20.dp,
        animationSpec = VividMotion.fastSpatial(),
        label = "exploreTagCorner"
    )
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = VividMotion.fastSpatial(),
        label = "exploreTagMorph"
    )
    val iconShape = rememberVividMorph(
        start = VividMaterialShapes.MorphResting,
        end = VividMaterialShapes.topicPolygon(tag),
        progress = morphProgress
    )
    val pair = ExploreTopics.containerPair(tag)
    val chipCd = stringResource(R.string.cd_explore_tag, Hashtags.display(tag))
    val label = if (count > 0) "${Hashtags.display(tag)} · $count" else Hashtags.display(tag)
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = chipCd },
        label = { Text(label) },
        shape = RoundedCornerShape(corner),
        leadingIcon = {
            Surface(
                shape = iconShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    pair.container
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    pair.onContainer
                },
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        ExploreTopics.icon(tag),
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = pair.container,
            selectedLabelColor = pair.onContainer,
            selectedLeadingIconColor = pair.onContainer
        )
    )
}

@Composable
internal fun ExploreTopicHeader(tag: String, modifier: Modifier = Modifier) {
    val pair = ExploreTopics.containerPair(tag)
    val shape = rememberVividMorph(
        start = VividMaterialShapes.MorphResting,
        end = VividMaterialShapes.topicPolygon(tag),
        progress = 1f
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xs),
        shape = VividExpressiveShapes.MediumCard,
        color = pair.container,
        contentColor = pair.onContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        ExploreTopics.icon(tag),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.size(VividSpace.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    Hashtags.display(tag),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.explore_topic_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = pair.onContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreEmptyTagState(
    selectedTag: String,
    onSelectTag: (String) -> Unit
) {
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
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(VividSpace.xs))
        Text(
            stringResource(R.string.explore_empty_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(VividSpace.m))
        val others = ExplorePaging.TAGS.filter { it != selectedTag }.take(6)
        if (others.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xs),
                verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
            ) {
                others.forEach { tag ->
                    SuggestionChip(
                        onClick = { onSelectTag(tag) },
                        label = { Text(Hashtags.display(tag)) },
                        icon = {
                            Icon(
                                ExploreTopics.icon(tag),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = VividExpressiveShapes.ChipUnselected
                    )
                }
            }
        }
    }
}

/**
 * Celda del mosaico de Explorar, compartida por el grid en vivo y el
 * fallback offline (mismo aspecto, misma transición compartida).
 */
@Composable
internal fun ExplorePostCard(
    post: PostData,
    aspect: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val postCd = stringResource(R.string.cd_post_by, post.username)
    val imageModel = post.imageUrl.ifBlank { post.thumbnailUrl }
    Card(
        modifier = modifier
            .aspectRatio(aspect)
            .semantics { contentDescription = postCd }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageModel.isNotBlank()) {
                VividAsyncImage(
                    model = imageModel,
                    contentDescription = postCd,
                    modifier = Modifier.fillMaxSize(),
                    sharedKey = VividSharedKeys.postImage(post.id)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        Hashtags.display(post.caption.take(12).trim()).ifBlank {
                            post.caption.take(10)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (post.isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(VividSpace.xs)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_explore_video),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Alias de preview: tile cuadrado del mosaico. */
@Composable
internal fun ExplorePostTile(
    post: PostData,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) = ExplorePostCard(
    post = post,
    aspect = if (featured) 0.75f else 1f,
    onClick = onClick,
    modifier = modifier.fillMaxWidth()
)

/**
 * Ritmo del mosaico de Explore:
 *   - índices ≡ 2 y 7 (módulo 10): retrato 3:4 → 0.75
 *   - índice ≡ 5 (módulo 10): paisaje 4:3 → 1.33
 *   - resto: cuadrado 1:1
 */
private fun exploreCellAspectRatio(index: Int): Float = when (index % 10) {
    2, 7 -> 0.75f
    5 -> 1.33f
    else -> 1f
}

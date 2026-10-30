package com.vivid.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vivid.app.R
import com.vivid.app.presentation.explore.ExplorePostCard
import com.vivid.app.presentation.explore.ExploreTagChip
import com.vivid.app.presentation.explore.ExploreTopicHeader
import com.vivid.app.presentation.explore.ExploreTopics
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividEmptyState
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividSkeletonGrid
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.ui.components.rememberVividMorph
import com.vivid.app.util.Hashtags

private const val POSTS_PER_ROW = 3

/**
 * Panel del buscador inteligente (Material 3 Expressive).
 *
 * Es el contenido expandido de la [com.vivid.app.ui.components.VividSearchBar]:
 * segmentos Todo/Personas/Temas/Posts, recientes en chips borrables, temas en
 * tendencia con las formas de Vivid y resultados unificados con skeletons,
 * estados vacíos y de error del design system.
 *
 * El componente es tonto: recibe el [SmartSearchUiState] ya calculado y
 * emite eventos. Así el mismo panel sirve en Explorar (docked en tableta,
 * pantalla completa en teléfono) y en la pantalla de búsqueda suelta.
 */
@Composable
fun SmartSearchPanel(
    state: SmartSearchUiState,
    history: List<String>,
    trending: List<SearchTagResult>,
    onTabChange: (SearchTab) -> Unit,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTagClick: (String) -> Unit,
    onUserClick: (SearchUser) -> Unit,
    onUserMessage: (SearchUser) -> Unit,
    onPostClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = VividSpace.l),
        verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
    ) {
        item(key = "tabs") {
            SmartSearchTabs(selected = state.tab, onSelect = onTabChange)
        }

        val tagIntent = SmartSearch.tagIntent(state.query)
        if (tagIntent != null && state.isSearching && state.tab.let { it == SearchTab.ALL || it == SearchTab.TAGS }) {
            // Atajo hero: el `#tag` escrito se puede abrir directo en Explorar.
            item(key = "tag_hero") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VividSpace.xs)
                        .clickable { onTagClick(tagIntent) }
                ) {
                    ExploreTopicHeader(tag = tagIntent)
                }
            }
        }

        if (!state.isSearching) {
            smartSearchIdleContent(
                history = history,
                trending = trending,
                onRecentClick = onRecentClick,
                onRecentRemove = onRecentRemove,
                onClearHistory = onClearHistory,
                onTagClick = onTagClick
            )
            return@LazyColumn
        }

        when {
            state.isLoading -> smartSearchLoadingContent(state.tab)
            state.error -> item(key = "error") {
                VividErrorState(
                    message = stringResource(R.string.search_error),
                    onRetry = onRetry
                )
            }
            else -> smartSearchResultsContent(
                state = state,
                onTabChange = onTabChange,
                onTagClick = onTagClick,
                onUserClick = onUserClick,
                onUserMessage = onUserMessage,
                onPostClick = onPostClick
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Segmentos
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSearchTabs(selected: SearchTab, onSelect: (SearchTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xs)
    ) {
        SearchTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = SearchTab.entries.size
                ),
                icon = { SegmentedButtonDefaults.Icon(active = selected == tab) },
                label = {
                    Text(
                        text = stringResource(searchTabLabelRes(tab)),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun searchTabLabelRes(tab: SearchTab): Int = when (tab) {
    SearchTab.ALL -> R.string.search_tab_all
    SearchTab.PEOPLE -> R.string.search_tab_people
    SearchTab.TAGS -> R.string.search_tab_tags
    SearchTab.POSTS -> R.string.search_tab_posts
}

// ─────────────────────────────────────────────────────────────────────────
//  Estado inicial (sin buscar): recientes + tendencias
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.smartSearchIdleContent(
    history: List<String>,
    trending: List<SearchTagResult>,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTagClick: (String) -> Unit
) {
    if (history.isNotEmpty()) {
        item(key = "recent_header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_recent),
                icon = Icons.Default.History,
                actionLabel = stringResource(R.string.search_clear_history),
                onAction = onClearHistory
            )
        }
        item(key = "recent_chips") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VividSpace.m),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xs),
                verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
            ) {
                history.forEach { recent ->
                    val isTag = Hashtags.parseQuery(recent) != null
                    val removeCd = stringResource(R.string.search_remove_recent)
                    InputChip(
                        selected = false,
                        onClick = { onRecentClick(recent) },
                        label = { Text(recent) },
                        leadingIcon = {
                            Icon(
                                if (isTag) Icons.Default.Tag else Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { onRecentRemove(recent) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = removeCd,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        },
                        shape = VividExpressiveShapes.ChipUnselected
                    )
                }
            }
        }
    }

    if (trending.isNotEmpty()) {
        item(key = "trending_header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_trending),
                icon = Icons.Default.TrendingUp
            )
        }
        item(key = "trending_chips") {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VividSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xs),
                verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
            ) {
                trending.take(TAGS_TRENDING_LIMIT).forEach { result ->
                    ExploreTagChip(
                        tag = result.tag,
                        selected = false,
                        onClick = { onTagClick(result.tag) },
                        count = result.count
                    )
                }
            }
        }
    }

    if (history.isEmpty() && trending.isEmpty()) {
        item(key = "hint") {
            Text(
                text = stringResource(R.string.search_min_chars),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VividSpace.l)
            )
        }
    }
}

private const val TAGS_TRENDING_LIMIT = 10

// ─────────────────────────────────────────────────────────────────────────
//  Carga
// ─────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.smartSearchLoadingContent(tab: SearchTab) {
    when (tab) {
        SearchTab.POSTS -> item(key = "loading_grid") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VividSpace.xxs)
            ) {
                VividSkeletonGrid(columns = POSTS_PER_ROW, count = 6)
            }
        }
        // Personas / Temas / Todo: filas tipo lista + retícula resumida.
        else -> {
            items(SKELETON_ROWS) { index -> VividSkeletonListItem() }
            if (tab == SearchTab.ALL) {
                item(key = "loading_grid") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(VividSpace.xxs)
                    ) {
                        VividSkeletonGrid(columns = POSTS_PER_ROW, count = 3)
                    }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 4

// ─────────────────────────────────────────────────────────────────────────
//  Resultados
// ─────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.smartSearchResultsContent(
    state: SmartSearchUiState,
    onTabChange: (SearchTab) -> Unit,
    onTagClick: (String) -> Unit,
    onUserClick: (SearchUser) -> Unit,
    onUserMessage: (SearchUser) -> Unit,
    onPostClick: (String) -> Unit
) {
    val allEmpty = state.users.isEmpty() && state.tags.isEmpty() && state.posts.isEmpty()
    if (allEmpty) {
        item(key = "empty") {
            SmartSearchEmptyState(tab = state.tab, query = state.query)
        }
        return
    }

    val isAll = state.tab == SearchTab.ALL
    val showPeople = isAll || state.tab == SearchTab.PEOPLE
    val showTags = isAll || state.tab == SearchTab.TAGS
    val showPosts = isAll || state.tab == SearchTab.POSTS

    val visibleUsers =
        if (isAll) state.users.take(SmartSearch.PEOPLE_PREVIEW) else state.users
    val visibleTags =
        if (isAll) state.tags.take(SmartSearch.TAGS_PREVIEW) else state.tags
    val visiblePosts =
        if (isAll) state.posts.take(SmartSearch.POSTS_PREVIEW) else state.posts

    if (showPeople && visibleUsers.isNotEmpty()) {
        item(key = "people_header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_section_people),
                icon = Icons.Default.PersonSearch,
                actionLabel = stringResource(R.string.search_see_all)
                    .takeIf { isAll && state.users.size > visibleUsers.size },
                onAction = { onTabChange(SearchTab.PEOPLE) }
            )
        }
        items(visibleUsers, key = { "user_${it.uid}" }) { user ->
            UserSearchItem(
                user = user,
                onClick = { onUserClick(user) },
                onMessageClick = { onUserMessage(user) }
            )
        }
    }

    if (showTags && visibleTags.isNotEmpty()) {
        item(key = "tags_header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_section_tags),
                icon = Icons.Default.Tag,
                actionLabel = stringResource(R.string.search_see_all)
                    .takeIf { isAll && state.tags.size > visibleTags.size },
                onAction = { onTabChange(SearchTab.TAGS) }
            )
        }
        items(visibleTags, key = { "tag_${it.tag}" }) { result ->
            SmartTagRow(result = result, onClick = { onTagClick(result.tag) })
        }
    }

    if (showPosts && visiblePosts.isNotEmpty()) {
        item(key = "posts_header") {
            SearchSectionHeader(
                title = stringResource(R.string.search_section_posts),
                icon = Icons.Default.GridView,
                actionLabel = stringResource(R.string.search_see_all)
                    .takeIf { isAll && state.posts.size > visiblePosts.size },
                onAction = { onTabChange(SearchTab.POSTS) }
            )
        }
        val rows = visiblePosts.chunked(POSTS_PER_ROW)
        items(rows.size, key = { "postrow_$it" }) { rowIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VividSpace.xxs),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xxs)
            ) {
                rows[rowIndex].forEach { post ->
                    Box(modifier = Modifier.weight(1f)) {
                        ExplorePostCard(
                            post = post,
                            aspect = 1f,
                            onClick = { onPostClick(post.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartSearchEmptyState(tab: SearchTab, query: String) {
    val icon: ImageVector = when (tab) {
        SearchTab.PEOPLE -> Icons.Default.PersonSearch
        SearchTab.TAGS -> Icons.Default.Tag
        SearchTab.POSTS -> Icons.Default.GridView
        SearchTab.ALL -> Icons.Outlined.SearchOff
    }
    val title = when (tab) {
        SearchTab.PEOPLE -> stringResource(R.string.search_empty_people, query)
        SearchTab.TAGS -> stringResource(R.string.search_empty_tags, query)
        SearchTab.POSTS -> stringResource(R.string.search_empty_posts, query)
        SearchTab.ALL -> stringResource(R.string.search_empty_all, query)
    }
    VividEmptyState(
        icon = icon,
        title = title,
        subtitle = stringResource(R.string.search_empty_hint)
    )
}

// ─────────────────────────────────────────────────────────────────────────
//  Bloques reutilizables
// ─────────────────────────────────────────────────────────────────────────

/**
 * Cabecera de sección con icono del design system y acción opcional
 * ("Borrar" en recientes, "Ver todo" para saltar al segmento completo).
 */
@Composable
private fun SearchSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(VividSpace.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Fila de resultado de hashtag: icono con la silueta del tema (morph),
 * conteo de publicaciones y chevrón hacia Explorar.
 */
@Composable
private fun SmartTagRow(
    result: SearchTagResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = result.tag
    val pair = ExploreTopics.containerPair(tag)
    val iconShape = rememberVividMorph(
        start = VividMaterialShapes.MorphResting,
        end = VividMaterialShapes.topicPolygon(tag),
        progress = 1f
    )
    val rowCd = stringResource(R.string.cd_search_tag_row, Hashtags.display(tag), result.count)
    Surface(
        shape = VividExpressiveShapes.SmallCard,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xxs)
            .semantics { contentDescription = rowCd }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(VividSpace.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = iconShape,
                color = pair.container,
                contentColor = pair.onContainer,
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
            Spacer(modifier = Modifier.width(VividSpace.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Hashtags.display(tag),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (result.count > 0) {
                        stringResource(R.string.search_tags_count, result.count)
                    } else {
                        stringResource(R.string.explore_topic_subtitle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

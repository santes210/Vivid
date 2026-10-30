package com.vivid.app.presentation.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.vivid.app.theme.VividMotion
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividEmptyState
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividSkeletonGrid
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.ui.components.pressMorphShape
import com.vivid.app.ui.components.pressPrimaryButtonShape
import com.vivid.app.ui.components.rememberVividMorph
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.util.Hashtags

private const val POSTS_PER_ROW = 3
private const val TAGS_TRENDING_LIMIT = 10
private const val SKELETON_ROWS = 4

/** Fases visibles del panel: cada una con su propia lista, unidas por motion. */
private enum class SearchPanelPhase { IDLE, LOADING, ERROR, RESULTS }

/**
 * Panel del buscador inteligente (Material 3 Expressive).
 *
 * Es el contenido expandido de la [com.vivid.app.ui.components.VividSearchBar]
 * y habla el MISMO lenguaje expressivo que el resto de Vivid:
 *
 *  - **Pestañas con `ButtonGroup`** (patrón de las pestañas del perfil):
 *    grupo fijo de 3-4 alternativas donde el activo se ensancha y comprime a
 *    sus vecinos con el `MotionScheme`. Con tick háptico al cambiar.
 *  - **Transiciones de fase con `AnimatedContent`** (fade + escala) usando
 *    [VividMotion] — el movimiento respeta "reducir movimiento".
 *  - **Feedback de presión con formas que se transforman** ([pressMorphShape]
 *    en el botón de borrar recientes, [pressPrimaryButtonShape] en las filas
 *    de personas y hashtags): la silueta comunica el estado, igual que el
 *    botón Crear y el FAB de Reels.
 *  - **Contenedores de sección** con [VividMaterialShapes.SoftPill] y los
 *    pares de color (primary/secondary/tertiary) de los temas de Explorar.
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
    val phase = when {
        !state.isSearching -> SearchPanelPhase.IDLE
        state.isLoading -> SearchPanelPhase.LOADING
        state.error -> SearchPanelPhase.ERROR
        else -> SearchPanelPhase.RESULTS
    }

    // Specs del MotionScheme fuera del lambda de transitionSpec (que no es
    // composable): spatial para la escala, effects para el fundido.
    val fadeSpec = VividMotion.effects<Float>()
    val scaleSpec = VividMotion.fastSpatial<Float>()

    Column(modifier = modifier.fillMaxWidth()) {
        SmartSearchTabs(selected = state.tab, onSelect = onTabChange)

        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (
                    fadeIn(animationSpec = fadeSpec) +
                        scaleIn(initialScale = 0.94f, animationSpec = scaleSpec)
                    ) togetherWith (
                    fadeOut(animationSpec = fadeSpec) +
                        scaleOut(targetScale = 1.04f, animationSpec = scaleSpec)
                    )
            },
            label = "smartSearchPhase",
            modifier = Modifier.fillMaxWidth()
        ) { target ->
            when (target) {
                SearchPanelPhase.IDLE -> SmartSearchIdleList(
                    history = history,
                    trending = trending,
                    onRecentClick = onRecentClick,
                    onRecentRemove = onRecentRemove,
                    onClearHistory = onClearHistory,
                    onTagClick = onTagClick
                )
                SearchPanelPhase.LOADING -> SmartSearchLoadingList(state.tab)
                SearchPanelPhase.ERROR -> VividErrorState(
                    message = stringResource(R.string.search_error),
                    onRetry = onRetry
                )
                SearchPanelPhase.RESULTS -> SmartSearchResultsList(
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
}

// ─────────────────────────────────────────────────────────────────────────
//  Pestañas — ButtonGroup (M3 Expressive), el patrón del perfil
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSearchTabs(selected: SearchTab, onSelect: (SearchTab) -> Unit) {
    val haptics = rememberVividHaptics()
    // stringResource NO puede llamarse dentro del content de ButtonGroup (no
    // es composable): se resuelven las etiquetas antes.
    val labels = SearchTab.entries.map { stringResource(searchTabLabelRes(it)) }
    ButtonGroup(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
        overflowIndicator = {}
    ) {
        SearchTab.entries.forEachIndexed { index, tab ->
            toggleableItem(
                weight = 1f,
                checked = selected == tab,
                onCheckedChange = {
                    if (selected != tab) {
                        haptics.tick()
                        onSelect(tab)
                    }
                },
                label = labels[index],
                icon = {
                    Icon(
                        imageVector = searchTabIcon(tab),
                        contentDescription = labels[index]
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

private fun searchTabIcon(tab: SearchTab): ImageVector = when (tab) {
    SearchTab.ALL -> Icons.Default.AutoAwesome
    SearchTab.PEOPLE -> Icons.Default.PersonSearch
    SearchTab.TAGS -> Icons.Default.Tag
    SearchTab.POSTS -> Icons.Default.GridView
}

// ─────────────────────────────────────────────────────────────────────────
//  Estado inicial (sin buscar): recientes + tendencias
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartSearchIdleList(
    history: List<String>,
    trending: List<SearchTagResult>,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearHistory: () -> Unit,
    onTagClick: (String) -> Unit
) {
    val haptics = rememberVividHaptics()
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = VividSpace.l),
        verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
    ) {
        if (history.isNotEmpty()) {
            item(key = "recent_header") {
                SearchSectionHeader(
                    title = stringResource(R.string.search_recent),
                    icon = Icons.Default.History,
                    tint = neutralTint(),
                    actionLabel = stringResource(R.string.search_clear_history),
                    onAction = {
                        haptics.toggleOff()
                        onClearHistory()
                    }
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
                        SmartRecentChip(
                            recent = recent,
                            onClick = { onRecentClick(recent) },
                            onRemove = { onRecentRemove(recent) }
                        )
                    }
                }
            }
        }

        if (trending.isNotEmpty()) {
            item(key = "trending_header") {
                SearchSectionHeader(
                    title = stringResource(R.string.search_trending),
                    icon = Icons.Default.TrendingUp,
                    tint = neutralTint()
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
}

/**
 * Chip de reciente borrable. El botón de quitar se transforma al pulsarlo
 * (círculo → galleta de 9 puntas, [pressMorphShape]): el mismo lenguaje de
 * forma que el botón Crear.
 */
@Composable
private fun SmartRecentChip(
    recent: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val haptics = rememberVividHaptics()
    val removeCd = stringResource(R.string.search_remove_recent)
    val isTag = Hashtags.parseQuery(recent) != null
    val removeInteractions = remember { MutableInteractionSource() }
    InputChip(
        selected = false,
        onClick = onClick,
        label = { Text(recent) },
        leadingIcon = {
            Icon(
                if (isTag) Icons.Default.Tag else Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        trailingIcon = {
            Surface(
                onClick = {
                    haptics.toggleOff()
                    onRemove()
                },
                interactionSource = removeInteractions,
                shape = pressMorphShape(removeInteractions),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = removeCd,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        },
        shape = VividExpressiveShapes.ChipUnselected
    )
}

// ─────────────────────────────────────────────────────────────────────────
//  Carga
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSearchLoadingList(tab: SearchTab) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = VividSpace.l),
        verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
    ) {
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
            else -> {
                items(SKELETON_ROWS) { VividSkeletonListItem() }
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
}

// ─────────────────────────────────────────────────────────────────────────
//  Resultados
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SmartSearchResultsList(
    state: SmartSearchUiState,
    onTabChange: (SearchTab) -> Unit,
    onTagClick: (String) -> Unit,
    onUserClick: (SearchUser) -> Unit,
    onUserMessage: (SearchUser) -> Unit,
    onPostClick: (String) -> Unit
) {
    val tagIntent = SmartSearch.tagIntent(state.query)
    val allEmpty = state.users.isEmpty() && state.tags.isEmpty() && state.posts.isEmpty()
    val isAll = state.tab == SearchTab.ALL
    val visibleUsers = if (isAll) state.users.take(SmartSearch.PEOPLE_PREVIEW) else state.users
    val visibleTags = if (isAll) state.tags.take(SmartSearch.TAGS_PREVIEW) else state.tags
    val visiblePosts = if (isAll) state.posts.take(SmartSearch.POSTS_PREVIEW) else state.posts
    val showPeople = isAll || state.tab == SearchTab.PEOPLE
    val showTags = isAll || state.tab == SearchTab.TAGS
    val showPosts = isAll || state.tab == SearchTab.POSTS

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = VividSpace.l),
        verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
    ) {
        // Atajo hero: el `#tag` escrito se puede abrir directo en Explorar.
        if (tagIntent != null && (isAll || state.tab == SearchTab.TAGS)) {
            item(key = "tag_hero") {
                Surface(
                    onClick = { onTagClick(tagIntent) },
                    shape = VividExpressiveShapes.MediumCard,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VividSpace.xs)
                ) {
                    ExploreTopicHeader(tag = tagIntent)
                }
            }
        }

        if (allEmpty) {
            item(key = "empty") {
                SmartSearchEmptyState(tab = state.tab, query = state.query)
            }
            return@LazyColumn
        }

        if (showPeople && visibleUsers.isNotEmpty()) {
            item(key = "people_header") {
                SearchSectionHeader(
                    title = stringResource(R.string.search_section_people),
                    icon = Icons.Default.PersonSearch,
                    tint = sectionTint(0),
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
                    tint = sectionTint(1),
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
                    tint = sectionTint(2),
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

/** Tinte de contenedor de sección, al estilo de los temas de Explorar. */
private data class SectionTint(val container: Color, val onContainer: Color)

@Composable
private fun sectionTint(index: Int): SectionTint {
    val scheme = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> SectionTint(scheme.primaryContainer, scheme.onPrimaryContainer)
        1 -> SectionTint(scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> SectionTint(scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
}

@Composable
private fun neutralTint(): SectionTint {
    val scheme = MaterialTheme.colorScheme
    return SectionTint(scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
}

/**
 * Cabecera de sección: icono en pastilla [VividMaterialShapes.SoftPill] con
 * el tinte de la sección, título en negrita y acción opcional ("Borrar" en
 * recientes, "Ver todo" para saltar al segmento completo, con tick háptico).
 */
@Composable
private fun SearchSectionHeader(
    title: String,
    icon: ImageVector,
    tint: SectionTint,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val haptics = rememberVividHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = VividMaterialShapes.SoftPill,
            color = tint.container,
            contentColor = tint.onContainer,
            modifier = Modifier.size(width = 36.dp, height = 22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(VividSpace.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = {
                    haptics.tick()
                    onAction()
                }
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Fila de resultado de hashtag: icono con la silueta del tema (morph),
 * conteo de publicaciones y chevrón hacia Explorar. Al pulsarla la esquina
 * se comprime 20 → 12 dp ([pressPrimaryButtonShape]), el feedback
 * horizontal del design system.
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
    val interactions = remember { MutableInteractionSource() }
    val rowCd = stringResource(R.string.cd_search_tag_row, Hashtags.display(tag), result.count)
    Surface(
        onClick = onClick,
        interactionSource = interactions,
        shape = pressPrimaryButtonShape(interactions),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VividSpace.s, vertical = VividSpace.xxs)
            .semantics { contentDescription = rowCd }
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

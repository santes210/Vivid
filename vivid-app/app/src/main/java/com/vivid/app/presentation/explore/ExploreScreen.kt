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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Tag
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
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividAsyncImage
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBanner
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.components.VividSearchBar
import com.vivid.app.ui.components.VividSkeletonGrid
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys

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

    // Navegar desde un #tag (feed/detalle) preselecciona el filtro.
    LaunchedEffect(initialTag) {
        if (initialTag.isNotBlank()) exploreViewModel.selectTag(initialTag)
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
            // Filas skeleton con la misma estructura que UserSearchItem:
            // avatar + dos líneas. Consistente con el resto de la app.
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
    tags: List<ExploreTag>,
    cachedPosts: List<PostData>,
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
    // El tag activo siempre tiene chip: puede ser uno recién descubierto o
    // uno llegado por navegación (#tag en un post) que aún no está en el
    // catálogo; sin esto el filtro se aplicaría "a ciegas".
    val chipTags = remember(tags, selectedTag) {
        if (tags.any { it.tag == selectedTag }) tags
        else listOf(ExploreTag(selectedTag, 0)) + tags
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.s, vertical = VividSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)
    ) {
        items(chipTags, key = { it.tag }) { tagItem ->
            val isSelected = selectedTag == tagItem.tag
            FilterChip(
                selected = isSelected,
                onClick = { onSelectTag(tagItem.tag) },
                label = {
                    // El conteo es el número de posts recientes que usan el
                    // tag: popularidad real, no decoración.
                    Text(
                        if (tagItem.count > 0) "#${tagItem.tag} · ${tagItem.count}"
                        else "#${tagItem.tag}"
                    )
                },
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
            // Skeleton con la MISMA estructura que el grid final: la pantalla
            // no "salta" cuando llegan los datos, solo se rellena.
            Box(modifier = Modifier.fillMaxSize().padding(VividSpace.xxs)) {
                VividSkeletonGrid(columns = 3, count = 18)
            }
        }
        posts.loadState.refresh is LoadState.Error && posts.itemCount == 0 -> {
            if (cachedPosts.isNotEmpty()) {
                // Sin conexión pero con cache: el mismo mosaico servido desde
                // Room (posts que el feed/Explorar ya guardaron). Firestore
                // deja de ser requisito para abrir Explorar.
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
            // Mosaico estilo "descubrimiento": staggered grid de 3 columnas
            // donde la mayoría de celdas son 1:1 pero cada pocas aparece un
            // acento vertical (3:4) u horizontal (4:3). Un grid 100 % uniforme
            // se ve como tablero de ajedrez; el ritmo mixto hace que el ojo
            // recorra en diagonal y la pantalla "respire". El patrón es
            // determinista (función del índice) para que se sienta diseñado,
            // no aleatorio, y las posiciones sean estables entre recomposiciones.
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

/**
 * Celda del mosaico de Explorar, compartida por el grid en vivo y el
 * fallback offline (mismo aspecto, misma transición compartida).
 */
@Composable
private fun ExplorePostCard(post: PostData, aspect: Float, onClick: () -> Unit) {
    val postCd = stringResource(R.string.cd_post_by, post.username)
    Card(
        modifier = Modifier
            .aspectRatio(aspect)
            .semantics { contentDescription = postCd }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        if (post.imageUrl.isNotBlank()) {
            VividAsyncImage(
                model = post.imageUrl,
                contentDescription = postCd,
                modifier = Modifier.fillMaxSize(),
                // La miniatura ES la del detalle: misma clave compartida que
                // PostDetailScreen.
                sharedKey = VividSharedKeys.postImage(post.id)
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

/**
 * Ritmo del mosaico de Explore (ver comentario en el grid):
 *   - índices ≡ 2 y 7 (módulo 10): retrato 3:4 → 0.75
 *   - índice ≡ 5 (módulo 10): paisaje 4:3 → 1.33
 *   - resto: cuadrado 1:1
 */
private fun exploreCellAspectRatio(index: Int): Float = when (index % 10) {
    2, 7 -> 0.75f
    5 -> 1.33f
    else -> 1f
}

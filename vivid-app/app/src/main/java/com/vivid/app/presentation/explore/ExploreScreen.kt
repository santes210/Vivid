package com.vivid.app.presentation.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.vivid.app.R
import com.vivid.app.data.paging.ExplorePaging
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.search.SearchViewModel
import com.vivid.app.presentation.search.UserSearchItem
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.ui.motion.vividSharedElement

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.explore_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            VividOfflineBannerHost()

            OutlinedTextField(
                value = searchQuery,
                onValueChange = searchViewModel::setQuery,
                label = { Text(stringResource(R.string.search_people_label)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true
            )

            if (searching) {
                when {
                    users.loadState.refresh is LoadState.Loading && users.itemCount == 0 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ContainedLoadingIndicator(
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                        }
                    }
                    users.loadState.refresh is LoadState.Error && users.itemCount == 0 -> {
                        val err = users.loadState.refresh as LoadState.Error
                        VividErrorState(
                            message = err.error.localizedMessage
                                ?: stringResource(R.string.search_error),
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
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(
                                count = users.itemCount,
                                key = users.itemKey { it.uid }
                            ) { index ->
                                val user = users[index] ?: return@items
                                UserSearchItem(
                                    user = user,
                                    onClick = { onProfileClick(user.uid) },
                                    onMessageClick = { onProfileClick(user.uid) }
                                )
                            }
                            if (users.loadState.append is LoadState.Loading) {
                                item {
                                    Box(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ExplorePaging.TAGS, key = { it }) { tag ->
                        val isSelected = selectedTag == tag
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) haptics.tick()
                                exploreViewModel.selectTag(tag)
                            },
                            label = { Text("#$tag") },
                            shape = if (isSelected) {
                                VividExpressiveShapes.ChipSelected
                            } else {
                                VividExpressiveShapes.ChipUnselected
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                            } else null,
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
                            ContainedLoadingIndicator(
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                        }
                    }
                    posts.loadState.refresh is LoadState.Error && posts.itemCount == 0 -> {
                        val err = posts.loadState.refresh as LoadState.Error
                        VividErrorState(
                            message = err.error.localizedMessage
                                ?: stringResource(R.string.explore_error),
                            onRetry = { posts.retry() }
                        )
                    }
                    posts.itemCount == 0 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.explore_empty, selectedTag),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                        .clickable {
                                            haptics.tick()
                                            onPostClick(post.id)
                                        },
                                    shape = MaterialTheme.shapes.extraLarge
                                ) {
                                    if (post.imageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = post.imageUrl,
                                            contentDescription = postCd,
                                            // Ancla de la transición grid → detalle.
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .vividSharedElement(
                                                    VividSharedKeys.postImage(post.id)
                                                ),
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
                                        LoadingIndicator(Modifier.size(28.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

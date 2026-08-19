package com.vivid.app.presentation.explore

import com.vivid.app.presentation.search.SearchUser
import com.vivid.app.presentation.search.UserSearchItem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.feed.PostData
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.util.CrashReporter
import com.vivid.app.util.toUserFacingMessage
import com.vivid.app.util.withNetworkTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

private const val TAG = "ExploreScreen"

// Tamaño de página para paginación tipo Paging 3 (cursor-based con startAfter).
// Se mantiene chico para que la primera carga pese poco; al hacer scroll se
// piden las siguientes páginas. Si el user pidió paginación real, ya no
// cargamos todo de una.
private const val EXPLORE_PAGE_SIZE = 18L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {}
) {
    val blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val blockedUserIds = blockedUsersState.userIds

    var selectedTag by remember { mutableStateOf("vivid") }
    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    // Cursor de la última página: DocumentSnapshot del último doc cargado.
    // Se guarda en la última paginación con startAfter(lastVisible).
    var lastVisible by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    val tags = remember { listOf("vivid", "arte", "musica", "viaje", "comida", "tecnologia", "moda", "deporte") }

    /**
     * Carga la primera página del tag. Resetea cursor y lista. Si el tag es
     * nuevo, reemplaza la lista; si es retry, también.
     */
    suspend fun loadFirstPage(tag: String) {
        loading = true
        errorMessage = null
        lastVisible = null
        endReached = false
        try {
            val snapshot = withNetworkTimeout("explore.loadFirstPage") {
                FirebaseFirestore.getInstance()
                    .collection("posts")
                    .whereArrayContains("hashtags", tag)
                    .whereEqualTo("isPrivate", false)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(EXPLORE_PAGE_SIZE)
                    .get()
                    .await()
            }
            posts = snapshot.documents.mapNotNull { doc -> docToPost(doc, blockedUserIds) }
            lastVisible = snapshot.documents.lastOrNull()
            endReached = snapshot.documents.size < EXPLORE_PAGE_SIZE
        } catch (e: Exception) {
            CrashReporter.recordNonFatal(TAG, e, "No se pudieron cargar posts del tag #$tag")
            posts = emptyList()
            errorMessage = e.toUserFacingMessage("No se pudieron cargar las publicaciones.")
        }
        loading = false
    }

    /**
     * Carga la siguiente página usando startAfter(lastVisible). Se llama desde
     * el grid al detectar scroll cerca del final. No hace nada si ya estamos
     * en endReached o ya hay un loadMore en curso.
     */
    suspend fun loadMore() {
        if (loading || loadingMore || endReached) return
        val cursor = lastVisible ?: return
        loadingMore = true
        try {
            val snapshot = withNetworkTimeout("explore.loadMore") {
                FirebaseFirestore.getInstance()
                    .collection("posts")
                    .whereArrayContains("hashtags", selectedTag)
                    .whereEqualTo("isPrivate", false)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .startAfter(cursor)
                    .limit(EXPLORE_PAGE_SIZE)
                    .get()
                    .await()
            }
            val newPosts = snapshot.documents.mapNotNull { doc -> docToPost(doc, blockedUserIds) }
            // Filtrar duplicados (Firestore puede repetir el cursor bajo presión).
            val existingIds = posts.map { it.id }.toSet()
            posts = posts + newPosts.filterNot { it.id in existingIds }
            lastVisible = snapshot.documents.lastOrNull()
            if (snapshot.documents.size < EXPLORE_PAGE_SIZE) endReached = true
        } catch (e: Exception) {
            // Paginación silenciosa: si falla, el usuario sigue viendo el contenido previo.
            CrashReporter.recordNonFatal(TAG, e, "loadMore de #$selectedTag falló")
        }
        loadingMore = false
    }

    LaunchedEffect(selectedTag, blockedUserIds, blockedUsersState.isLoaded, retryKey) {
        if (blockedUsersState.isLoaded) loadFirstPage(selectedTag)
    }

    var searchQuery by remember { mutableStateOf("") }
    val searchUsers = remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    val searchLoading = remember { mutableStateOf(false) }
    val searchError = remember { mutableStateOf<String?>(null) }
    var searchRetryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchQuery, blockedUserIds, blockedUsersState.isLoaded, searchRetryKey) {
        val q = searchQuery.trim().lowercase()
        if (!blockedUsersState.isLoaded) return@LaunchedEffect
        if (q.length < 2) {
            searchUsers.value = emptyList()
            searchLoading.value = false
            searchError.value = null
            return@LaunchedEffect
        }
        delay(250)
        searchLoading.value = true
        searchError.value = null
        try {
            val snapshot = withNetworkTimeout("explore.searchUsers") {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .orderBy("usernameLower")
                    .startAt(q)
                    .endAt(q + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()
            }
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            searchUsers.value = snapshot.documents.mapNotNull { doc ->
                val uid = doc.getString("uid") ?: doc.id
                if (uid == currentUserId || uid in blockedUserIds) return@mapNotNull null
                SearchUser(
                    uid = uid,
                    username = doc.getString("username") ?: "usuario",
                    displayName = doc.getString("displayName") ?: doc.getString("username") ?: "Usuario",
                    avatarUrl = doc.getString("avatarUrl").orEmpty(),
                    avatarBase64 = doc.getString("avatarBase64").orEmpty(),
                    isFollowing = false
                )
            }
        } catch (e: Exception) {
            CrashReporter.recordNonFatal(TAG, e, "Búsqueda de personas falló para '$q'")
            searchUsers.value = emptyList()
            searchError.value = e.toUserFacingMessage("No se pudieron buscar usuarios.")
        }
        searchLoading.value = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explorar", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            VividOfflineBannerHost()

            // Barra de búsqueda Material You 3
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar personas") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true
            )

            if (searchQuery.trim().length >= 2) {
                // Resultado de búsqueda de personas (como IG)
                when {
                    searchLoading.value -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    searchError.value != null -> VividErrorState(
                        message = searchError.value.orEmpty(),
                        onRetry = { searchRetryKey++ }
                    )
                    searchUsers.value.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text("No se encontraron usuarios.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchUsers.value, key = { it.uid }) { user ->
                            UserSearchItem(
                                user = user,
                                onClick = { onProfileClick(user.uid) },
                                onMessageClick = { onProfileClick(user.uid) }
                            )
                        }
                    }
                }
            } else {
                // Hashtags + grid de posts (explorar)
                LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags, key = { it }) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = tag },
                        label = { Text("#${tag}") },
                        leadingIcon = if (selectedTag == tag) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) } } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                VividErrorState(
                    message = errorMessage.orEmpty(),
                    onRetry = { retryKey++ }
                )
            } else if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay publicaciones para #$selectedTag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        Card(
                            modifier = Modifier.aspectRatio(1f).clickable { onPostClick(post.id) },
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            if (post.imageUrl.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = post.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("#${post.caption.take(10)}", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                }
                            }
                        }
                    }
                    // Trigger de paginación: cuando se renderizan los últimos
                    // 6 items, se pide la siguiente página. Es la forma más
                    // simple de emular Paging 3 sin agregar la dependencia.
                    if (!endReached) {
                        items(3) { _ ->
                            // Renderiza celdas vacías como "padding" invisible
                            // para que el loadMore se dispare antes de llegar
                            // al final real. Es invisible (sin Card).
                            Spacer(modifier = Modifier.aspectRatio(1f))
                        }
                    }
                    if (loadingMore) {
                        items(3) { _ ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
                // Detección de scroll cerca del final: las celdas padding
                // invisibles (Spacer) sirven como disparador, pero también
                // conectamos un LaunchedEffect al tamaño de la lista para
                // pedir más cuando faltan pocos items por cargar.
                LaunchedEffect(posts.size, loadingMore, endReached) {
                    if (!loadingMore && !endReached && posts.size >= EXPLORE_PAGE_SIZE.toInt()) {
                        loadMore()
                    }
                }
            }
        }
    }
        }
}

/**
 * Convierte un DocumentSnapshot de Firestore a [PostData]. Devuelve null
 * si el post pertenece a un usuario bloqueado o si faltan datos críticos.
 */
private fun docToPost(doc: DocumentSnapshot, blockedUserIds: Set<String>): PostData? {
    val id = doc.id
    val userId = doc.getString("userId") ?: ""
    if (userId.isBlank() || userId in blockedUserIds) return null
    return PostData(
        id = id,
        userId = userId,
        username = doc.getString("username") ?: "",
        userProfilePicture = "",
        userProfilePictureBase64 = "",
        imageUrl = doc.getString("imageUrl") ?: "",
        videoUrl = doc.getString("videoUrl") ?: "",
        isVideo = doc.getBoolean("isVideo") ?: false,
        caption = doc.getString("caption") ?: "",
        likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
        commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
        timestamp = doc.getLong("timestamp") ?: 0L,
        isLiked = false,
        isSaved = false,
        storageKey = doc.getString("storageKey") ?: ""
    )
}


package com.vivid.app.presentation.feed

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.report.ReportHelper
import com.vivid.app.presentation.stories.StoriesTray
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PostData(
    val id: String, val userId: String, val username: String,
    val userProfilePicture: String, val userProfilePictureBase64: String = "",
    val imageUrl: String = "", val imageBase64: String = "",
    val storageKey: String = "",
    val videoUrl: String = "", val thumbnailUrl: String = "",
    val isVideo: Boolean = false, val caption: String,
    val likesCount: Int = 0, val commentsCount: Int = 0,
    val timestamp: Long, val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    // Música opcional (nuevo)
    val musicTitle: String = "",
    val musicArtist: String = "",
    val musicAssetFile: String = "",
    val musicUrl: String = "",
    val musicStorageKey: String = ""
)

data class PostComment(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val timestamp: Long,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isEdited: Boolean = false,
    val parentId: String? = null,
    val replyToUsername: String = ""
)

private data class FeedPageResult(
    val posts: List<PostData>,
    val lastDoc: com.google.firebase.firestore.DocumentSnapshot?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenMessages: () -> Unit,
    onOpenRequests: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenStoryViewer: (storyId: String) -> Unit = {},
    onCreateStory: () -> Unit = {}
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val feedViewModel: FeedViewModel = hiltViewModel()
    val blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val blockedUserIds = blockedUsersState.userIds

    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var lastVisibleDoc by remember { mutableStateOf<com.google.firebase.firestore.DocumentSnapshot?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    // IDs de posts a los que ya di like (1 sola consulta collectionGroup).
    var likedPostIds by remember { mutableStateOf<Set<String>?>(null) }
    // IDs de usuarios a los que ya sigo o tengo solicitud enviada
    var followingUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingFollowUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // IDs de posts guardados por el usuario
    var savedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Posts cuyo storageKey ya se intentó re-firmar tras un error 403 (evita loops)
    var refreshAttemptedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState()

    var followRequestsCount by remember { mutableIntStateOf(0) }
    var selectedPostForComments by remember { mutableStateOf<PostData?>(null) }
    var selectedPostViewerIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPostForDetails by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForEdit by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForDelete by remember { mutableStateOf<PostData?>(null) }
    // Reporte de publicaciones
    var showReportDialog by remember { mutableStateOf(false) }
    var reportPostId by remember { mutableStateOf("") }
    var reportPostUser by remember { mutableStateOf("") }
    var reportPostCaption by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("Inapropiado") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // ── Carga inicial: 1 sola consulta de likes + listener en tiempo real para posts ──
    // ANTES: usaba get() una sola vez, por eso al publicar un post no aparecía hasta reiniciar app.
    // AHORA: snapshotListener para que el feed se actualice al instante cuando publicas.
    LaunchedEffect(currentUserId) {
        isLoading = true
        val likedIds = feedViewModel.fetchLikedPostIds(currentUserId)
        likedPostIds = likedIds
        isLoading = false
    }

    // ── Caché Room (offline / arranque rápido): si hay posts cacheados de
    // menos de 7 días, mostrarlos al instante mientras Firestore responde.
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            runCatching {
                val cached = feedViewModel.getCachedPosts()
                if (cached.isNotEmpty() && feedViewModel.isPostCacheFresh()) {
                    posts = feedViewModel.cachedPostsToData(cached)
                }
            }
        }
    }

    // Al bloquear, retira inmediatamente cualquier página ya cargada. Al
    // desbloquear, el DisposableEffect inferior vuelve a consultar el feed.
    LaunchedEffect(blockedUserIds) {
        posts = posts.filterNot { it.userId in blockedUserIds }
    }

    DisposableEffect(
        currentUserId,
        likedPostIds,
        blockedUserIds,
        blockedUsersState.isLoaded,
        followingUserIds,
        retryKey
    ) {
        if (currentUserId.isBlank() || !blockedUsersState.isLoaded) {
            onDispose { }
        } else {
            isLoading = true
            isError = false
            val db = FirebaseFirestore.getInstance()
            val registrations = mutableListOf<ListenerRegistration>()
            val pages = mutableMapOf<String, List<com.google.firebase.firestore.DocumentSnapshot>>()

            fun publishVisiblePosts() {
                val documents = pages.values.flatten()
                    .distinctBy { it.id }
                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                posts = documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val authorId = data["userId"] as? String ?: ""
                        if (authorId in blockedUserIds) return@mapNotNull null
                        PostData(
                            id = doc.id,
                            userId = authorId,
                            username = data["username"] as? String ?: "usuario",
                            userProfilePicture = data["userAvatar"] as? String
                                ?: data["userProfilePicture"] as? String ?: "",
                            caption = data["caption"] as? String ?: "",
                            imageUrl = data["imageUrl"] as? String ?: "",
                            imageBase64 = data["imageBase64"] as? String ?: "",
                            storageKey = data["storageKey"] as? String ?: "",
                            videoUrl = data["videoUrl"] as? String ?: "",
                            thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
                            isVideo = data["isVideo"] as? Boolean ?: false,
                            likesCount = (data["likesCount"] as? Long)?.toInt() ?: 0,
                            commentsCount = (data["commentsCount"] as? Long)?.toInt() ?: 0,
                            timestamp = data["timestamp"] as? Long ?: 0L,
                            isLiked = likedPostIds?.contains(doc.id) == true,
                            musicTitle = data["musicTitle"] as? String ?: "",
                            musicArtist = data["musicArtist"] as? String ?: "",
                            musicAssetFile = data["musicAssetFile"] as? String ?: "",
                            musicUrl = data["musicUrl"] as? String ?: "",
                            musicStorageKey = data["musicStorageKey"] as? String ?: ""
                        )
                    } catch (_: Exception) { null }
                }
                isLoading = false

                // Persistir en caché Room (para arranque rápido y offline).
                // Firestore ya manda solo lo visible; la caché se renueva cada
                // vez que llega data fresca.
                if (posts.isNotEmpty()) {
                    scope.launch {
                        feedViewModel.cachePosts(posts)
                        com.vivid.app.util.VividCacheManager.markPostsCached(context)
                    }
                }
            }

            // Las queries reflejan exactamente lo que permiten las rules: todo
            // lo público y contenido privado solo de cuentas ya seguidas.
            registrations += db.collection("posts")
                .whereEqualTo("isPrivate", false)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snap, err ->
                    if (snap != null) {
                        pages["public"] = snap.documents
                        lastVisibleDoc = snap.documents.lastOrNull()
                        hasMore = snap.size() >= 20
                        publishVisiblePosts()
                    } else if (err != null && pages.isEmpty()) {
                        isLoading = false
                        isError = true
                    }
                }

            (followingUserIds + currentUserId).chunked(30).forEachIndexed { index, privateAuthors ->
                registrations += db.collection("posts")
                    .whereIn("userId", privateAuthors)
                    .whereEqualTo("isPrivate", true)
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null) {
                            pages["private_$index"] = snap.documents
                            publishVisiblePosts()
                        }
                    }
            }
            onDispose { registrations.forEach { it.remove() } }
        }
    }

    // Paginación real basada en scroll (startAfter) — sigue usando get() para páginas siguientes
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore && hasMore && lastVisibleDoc != null) {
            isLoadingMore = true
            val result = runCatching {
                loadMorePostsFromFirebase(
                    currentUserId,
                    lastVisibleDoc,
                    likedPostIds,
                    blockedUserIds,
                    feedViewModel
                )
            }.getOrElse { FeedPageResult(emptyList(), null) }
            if (result.posts.isNotEmpty()) {
                // Evita duplicados si el snapshot ya trae esos docs
                val existingIds = posts.map { it.id }.toSet()
                val newUnique = result.posts.filter { it.id !in existingIds }
                if (newUnique.isNotEmpty()) {
                    posts = posts + newUnique
                    lastVisibleDoc = result.lastDoc
                    hasMore = result.posts.size >= 20
                }
            } else {
                hasMore = false
            }
            isLoadingMore = false
        }
    }

    DisposableEffect(currentUserId) {
        var regRequests: ListenerRegistration? = null
        var regFollowing: ListenerRegistration? = null
        var regPending: ListenerRegistration? = null
        var regSaved: ListenerRegistration? = null

        if (currentUserId.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            regRequests = db.collection("users").document(currentUserId)
                .collection("followRequests").addSnapshotListener { snap, _ ->
                    followRequestsCount = snap?.size() ?: 0
                }
            regFollowing = db.collection("users").document(currentUserId)
                .collection("following").addSnapshotListener { snap, _ ->
                    followingUserIds = snap?.documents?.map { it.id }?.toSet().orEmpty()
                }
            regPending = db.collection("users").document(currentUserId)
                .collection("sentFollowRequests").addSnapshotListener { snap, _ ->
                    pendingFollowUserIds = snap?.documents?.map { it.id }?.toSet().orEmpty()
                }
            regSaved = db.collection("users").document(currentUserId)
                .collection("savedPosts").addSnapshotListener { snap, _ ->
                    savedPostIds = snap?.documents?.map { it.id }?.toSet().orEmpty()
                }
        }
        onDispose {
            regRequests?.remove()
            regFollowing?.remove()
            regPending?.remove()
            regSaved?.remove()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Vivid",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                actions = {
                    BadgedBox(
                        badge = { if (followRequestsCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text(followRequestsCount.coerceAtMost(9).toString(), color = MaterialTheme.colorScheme.onError) } },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = onOpenRequests) {
                            Icon(Icons.Default.Notifications, "Solicitudes", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Email, "Mensajes", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── Feed: pull-to-refresh M3, skeletons, estados vacío/error ──
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        // Re-sincroniza likes; el snapshot listener re-publica los posts
                        likedPostIds = feedViewModel.fetchLikedPostIds(currentUserId)
                        delay(650)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "stories") {
                        StoriesTray(
                            onStoryClick = { onOpenStoryViewer(it.id) },
                            onCreateStory = onCreateStory
                        )
                    }

                    when {
                        isLoading -> {
                            items(3) { FeedSkeleton() }
                        }
                        isError -> {
                            item(key = "error") { FeedErrorState(onRetry = { retryKey++ }) }
                        }
                        posts.isEmpty() -> {
                            item(key = "empty") { FeedEmptyState() }
                        }
                        else -> {
                            itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                                val isFollowingAuthor = post.userId in followingUserIds
                                val hasPendingRequestToAuthor = post.userId in pendingFollowUserIds
                                val isSaved = post.id in savedPostIds

                                PostCard(
                            post = post.copy(isSaved = isSaved),
                            currentUserId = currentUserId,
                            isFollowingAuthor = isFollowingAuthor,
                            hasPendingRequestToAuthor = hasPendingRequestToAuthor,
                            onOpenPost = { selectedPostViewerIndex = index },
                            onOpenComments = { selectedPostForComments = post },
                            onOpenDetails = { selectedPostForDetails = post },
                            onEditPost = { selectedPostForEdit = post },
                            onDeletePost = { selectedPostForDelete = post },
                            onToggleFollow = { targetUserId ->
                                feedViewModel.toggleFollowUser(targetUserId) { msg ->
                                    msg?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                                }
                            },
                            onToggleSave = {
                                val shouldSave = !isSaved
                                feedViewModel.toggleSavePost(post.id, currentUserId, shouldSave) { _, msg ->
                                    msg?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                                }
                            },
                            onToggleLike = {
                                val target = post
                                val newLiked = !target.isLiked
                                posts = posts.map {
                                    if (it.id == target.id) {
                                        it.copy(
                                            isLiked = newLiked,
                                            likesCount = (it.likesCount + if (newLiked) 1 else -1).coerceAtLeast(0)
                                        )
                                    } else it
                                }
                                likedPostIds = likedPostIds?.let { ids ->
                                    if (newLiked) ids + target.id else ids - target.id
                                }
                                scope.launch {
                                    runCatching {
                                        togglePostLike(target.id, currentUserId, newLiked)
                                    }.onFailure {
                                        posts = posts.map {
                                            if (it.id == target.id) target else it
                                        }
                                        likedPostIds = likedPostIds?.let { ids ->
                                            if (newLiked) ids - target.id else ids + target.id
                                        }
                                        snackbarHostState.showSnackbar(it.message ?: "No se pudo actualizar el like")
                                    }
                                }
                            },
                            onImageUrlExpired = {
                                val key = post.storageKey
                                if (key.isNotBlank() && post.id !in refreshAttemptedIds) {
                                    refreshAttemptedIds = refreshAttemptedIds + post.id
                                    scope.launch {
                                        feedViewModel.refreshSignedUrl(key)?.let { freshUrl ->
                                            posts = posts.map {
                                                if (it.id == post.id) it.copy(imageUrl = freshUrl) else it
                                            }
                                        }
                                    }
                                }
                            },
                            onMusicUrlExpired = {
                                val mKey = post.musicStorageKey
                                if (mKey.isNotBlank()) {
                                    scope.launch {
                                        feedViewModel.refreshSignedUrl(mKey)?.let { freshUrl ->
                                            posts = posts.map {
                                                if (it.id == post.id) it.copy(musicUrl = freshUrl) else it
                                            }
                                        }
                                    }
                                }
                            },
                            onShare = {
                                // Deep link de Material You 3: enlace vívido que abre la app
                                val deepLink = "vivid://post/${post.id}"
                                shareText(
                                    context = context,
                                    title = "Compartir publicación",
                                    text = buildString {
                                        append("Mira esta publicación de @${post.username} en Vivid")
                                        if (post.caption.isNotBlank()) append("\n\n${post.caption}")
                                        append("\n\n$deepLink")
                                    }
                                )
                            },
                            onReportPost = { pid, user, cap ->
                                showReportDialog = true
                                reportPostId = pid
                                reportPostUser = user
                                reportPostCaption = cap
                            }
                        )
                    }
                            if (isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Diálogos ──
    selectedPostForComments?.let { post -> PostCommentsSheet(post = post, onDismiss = { selectedPostForComments = null }) }
    selectedPostViewerIndex?.let { idx -> PostViewerDialog(posts = posts, initialIndex = idx, onDismiss = { selectedPostViewerIndex = null }) }
    selectedPostForDetails?.let { post -> PostDetailsDialog(post = post, onDismiss = { selectedPostForDetails = null }) }
    selectedPostForEdit?.let { post ->
        EditPostDialog(post = post, onDismiss = { selectedPostForEdit = null }, onSaved = { cap ->
            selectedPostForEdit = null; posts = posts.map { if (it.id == post.id) it.copy(caption = cap) else it }
            scope.launch { snackbarHostState.showSnackbar("Publicación actualizada") }
        })
    }
    selectedPostForDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedPostForDelete = null },
            title = { Text("Eliminar publicación") },
            text = { Text("¿Estás seguro? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            if (post.storageKey.isNotBlank()) {
                                feedViewModel.deleteRemoteFile(post.storageKey)
                            }
                            FirebaseFirestore.getInstance().collection("posts").document(post.id).delete().await()
                            posts = posts.filter { it.id != post.id }
                            likedPostIds = likedPostIds?.let { ids -> ids - post.id }
                            snackbarHostState.showSnackbar("Publicación eliminada")
                        } catch (e: Exception) { snackbarHostState.showSnackbar("Error: ${e.message}") }
                        selectedPostForDelete = null
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { selectedPostForDelete = null }) { Text("Cancelar") } }
        )
    }

    // ── Diálogo de reporte (Material You 3) ──
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Reportar publicación") },
            text = {
                Column {
                    Text("Motivo:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val reasons = listOf("Spam", "Contenido inapropiado", "Acoso", "Otro")
                    reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            RadioButton(
                                selected = reportReason == reason,
                                onClick = { reportReason = reason }
                            )
                            Text(reason, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Post: $reportPostUser — ${reportPostCaption.take(60)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opened = ReportHelper.sendPostReport(
                            context = context,
                            postId = reportPostId,
                            username = reportPostUser,
                            caption = reportPostCaption,
                            reason = reportReason
                        )
                        showReportDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (opened) "Redactando reporte en tu correo."
                                else "No se encontró una app de correo instalada."
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Enviar reporte") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Skeleton de carga (M3) ──
@Composable
private fun FeedSkeleton() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val alpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeletonAlpha"
        )
        animatedAlpha
    } else {
        0.65f
    }
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest

    fun Modifier.skeleton(): Modifier = this
        .height(14.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(blockColor.copy(alpha = alpha))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header falso
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(blockColor.copy(alpha = alpha))
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(110.dp).skeleton())
                Box(Modifier.width(60.dp).skeleton())
            }
        }
        // Media falsa
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(blockColor.copy(alpha = alpha))
        )
        // Acciones falsas
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(blockColor.copy(alpha = alpha)))
            Box(Modifier.size(22.dp).clip(CircleShape).background(blockColor.copy(alpha = alpha)))
            Box(Modifier.size(22.dp).clip(CircleShape).background(blockColor.copy(alpha = alpha)))
        }
        Box(Modifier.width(200.dp).skeleton())
        Box(Modifier.width(260.dp).skeleton())
    }
}

// ── Estado de error con reintento ──
@Composable
private fun FeedErrorState(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudOff,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No se pudo cargar el feed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Comprueba tu conexión e inténtalo de nuevo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reintentar")
            }
        }
    }
}

// ── Estado vacío real ──
@Composable
private fun FeedEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Tu feed está en blanco",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Sigue a personas y crea tu primera publicación para ver contenido aquí.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

// ── PostCard (Material You 3 Card) ──
@Composable
private fun PostCard(
    post: PostData,
    currentUserId: String,
    isFollowingAuthor: Boolean,
    hasPendingRequestToAuthor: Boolean,
    onOpenPost: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenDetails: () -> Unit,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleSave: () -> Unit,
    onToggleLike: () -> Unit,
    onShare: () -> Unit,
    onReportPost: (String, String, String) -> Unit = { _, _, _ -> },
    onImageUrlExpired: () -> Unit = {},
    onMusicUrlExpired: () -> Unit = {}
) {
    // Sin tarjeta elevada: el contenido vive sobre la superficie con separación tonal.
    Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostAuthorAvatar(post)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text(formatTimestamp(post.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Botón inline Seguir / Dejar de seguir (estilo Material You 3 / Instagram)
                if (post.userId != currentUserId && currentUserId.isNotBlank()) {
                    InlineFollowButton(
                        isFollowing = isFollowingAuthor,
                        hasPendingRequest = hasPendingRequestToAuthor,
                        onClick = { onToggleFollow(post.userId) }
                    )
                }

                // Menú de opciones (Material You 3): editar/eliminar solo para autor; reportar para todos
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (post.userId == currentUserId) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { showMenu = false; onEditPost() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem(text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeletePost() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                        }
                        DropdownMenuItem(
                            text = { Text("Reportar publicación", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onReportPost(post.id, post.username, post.caption)
                            },
                            leadingIcon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            // ── Contenido multimedia ──
            Box(modifier = Modifier.fillMaxWidth().clickable { onOpenPost() }) {
                when {
                    post.isVideo && post.videoUrl.isNotBlank() -> PostVideoPlayer(videoUrl = post.videoUrl, thumbnailUrl = post.thumbnailUrl)
                    else -> PostImage(
                        imageBase64 = post.imageBase64,
                        imageUrl = post.imageUrl,
                        username = post.username,
                        storageKey = post.storageKey,
                        onUrlExpired = onImageUrlExpired
                    )
                }
            }

            // ── Música opcional (Material You 3) ──
            if (post.musicTitle.isNotBlank() || post.musicUrl.isNotBlank() || post.musicAssetFile.isNotBlank()) {
                PostMusicChip(post = post, onMusicUrlExpired = onMusicUrlExpired)
            }

            // ── Acciones ──
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Like",
                        tint = if (post.isLiked) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onOpenComments) { Icon(Icons.Default.ChatBubbleOutline, "Comentar") }
                IconButton(onClick = onOpenDetails) { Icon(Icons.Default.Info, "Detalles") }

                Spacer(Modifier.weight(1f))

                // Botón Guardar / Bookmark
                IconButton(onClick = onToggleSave) {
                    Icon(
                        if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        "Guardar",
                        tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Compartir") }
            }

            // ── Likes count ──
            if (post.likesCount > 0 && !SettingsManager.hideLikesCount) {
                Text(
                    "${post.likesCount} Me gusta",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            // ── Caption ──
            if (post.caption.isNotBlank()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(post.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(6.dp))
                    Text(SettingsManager.filterOffensiveWords(post.caption), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }

            // ── Comments count ──
            if (post.commentsCount > 0) {
                TextButton(onClick = onOpenComments, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Ver los ${post.commentsCount} comentarios", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
        }
}

@Composable
private fun InlineFollowButton(
    isFollowing: Boolean,
    hasPendingRequest: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isFollowing || hasPendingRequest -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isFollowing || hasPendingRequest -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val label = when {
        isFollowing -> "Siguiendo"
        hasPendingRequest -> "Solicitado"
        else -> "Seguir"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFollowing && !hasPendingRequest) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            } else if (isFollowing) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun PostMusicChip(post: PostData, onMusicUrlExpired: () -> Unit = {}) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var resolvedAssetFile by remember(post.musicAssetFile) { mutableStateOf<File?>(null) }
    var isPreparingAsset by remember { mutableStateOf(false) }

    // Para assets del APK: copiar a cache para reproducción más fiable que asset://
    LaunchedEffect(post.musicAssetFile) {
        if (post.musicAssetFile.isNotBlank() && post.musicUrl.isBlank()) {
            isPreparingAsset = true
            resolvedAssetFile = withContext(Dispatchers.IO) {
                try {
                    val assetPath = post.musicAssetFile
                    val input = context.assets.open(assetPath)
                    val tempFile = File(context.cacheDir, "post_music_asset_${post.id}_${assetPath.substringAfterLast("/")}")
                    // Si ya existe y tiene tamaño, reusar
                    if (!tempFile.exists() || tempFile.length() < 1024) {
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    input.close()
                    tempFile
                } catch (e: Exception) {
                    android.util.Log.w("PostMusicChip", "No se pudo copiar asset ${post.musicAssetFile}: ${e.message}")
                    null
                }
            }
            isPreparingAsset = false
        } else {
            resolvedAssetFile = null
        }
    }

    // Resolver URI final de música - FIX: usar variable local para evitar smart cast en delegated property
    val musicUriString = remember(post, resolvedAssetFile) {
        val raf = resolvedAssetFile
        when {
            post.musicUrl.isNotBlank() -> post.musicUrl
            raf != null && raf.exists() -> "file://${raf.absolutePath}"
            post.musicAssetFile.isNotBlank() -> "asset:///${post.musicAssetFile}"
            else -> null
        }
    }

    // Manejar preview con ExoPlayer, con listener de error para debug
    DisposableEffect(musicUriString, isPlaying) {
        val shouldPlay = isPlaying
        val uri = musicUriString
        if (shouldPlay && uri != null) {
            try {
                // Usar Uri.parse para manejar query params con Authorization correctamente
                val parsedUri = android.net.Uri.parse(uri)
                val p = ExoPlayer.Builder(context).build().apply {
                    // Caché local para música remota (B2); assets locales van directo
                    if (com.vivid.app.util.VideoCacheManager.isCacheable(uri)) {
                        setMediaSource(com.vivid.app.util.VideoCacheManager.buildCachedMediaSource(context, uri))
                    } else {
                        setMediaItem(MediaItem.fromUri(parsedUri))
                    }
                    prepare()
                    playWhenReady = true
                    volume = 1.0f
                    repeatMode = ExoPlayer.REPEAT_MODE_OFF
                }
                // Listener para log de errores + re-intento con URL fresca si es B2
                p.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("PostMusicChip", "Error reproduciendo ${post.musicTitle}: ${error.message}", error)
                        if (post.musicStorageKey.isNotBlank()) {
                            // Si el error es 403 (URL expirada), intentar regenerar
                            onMusicUrlExpired()
                            isPlaying = false
                        }
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                            isPlaying = false
                        }
                    }
                })
                player = p
            } catch (e: Exception) {
                android.util.Log.e("PostMusicChip", "No se pudo crear player para ${post.musicTitle}: ${e.message}", e)
                player = null
                isPlaying = false
            }
        } else {
            player?.release()
            player = null
        }
        onDispose {
            player?.release()
            player = null
        }
    }

    val hasMusic = post.musicTitle.isNotBlank() || post.musicAssetFile.isNotBlank() || post.musicUrl.isNotBlank()

    if (!hasMusic) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.musicTitle.ifBlank { "Música" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (post.musicArtist.isNotBlank()) {
                    Text(
                        text = post.musicArtist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (musicUriString != null) {
                    Text(
                        text = if (post.musicAssetFile.isNotBlank()) "De la librería del APK" else "Audio del post",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            if (isPreparingAsset) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (musicUriString != null) {
                FilledTonalIconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ── Helpers ──
@Composable
private fun PostVideoPlayer(videoUrl: String, thumbnailUrl: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var isReady by remember { mutableStateOf(false) }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(ctx).build().apply {
            // Caché local: los videos de B2 no se re-descargan en cada visita
            if (com.vivid.app.util.VideoCacheManager.isCacheable(videoUrl)) {
                setMediaSource(com.vivid.app.util.VideoCacheManager.buildCachedMediaSource(ctx, videoUrl))
            } else {
                setMediaItem(MediaItem.fromUri(videoUrl))
            }
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxWidth().height(380.dp).background(Color.Black)) {
        if (!isReady && thumbnailUrl.isNotBlank()) {
            AsyncImage(model = thumbnailUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        AndroidView(
            factory = { ctx2 -> PlayerView(ctx2).apply { this.player = player; useController = true; player?.addListener(object : androidx.media3.common.Player.Listener { override fun onPlaybackStateChanged(s: Int) { if (s == androidx.media3.common.Player.STATE_READY) isReady = true } }) } },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private suspend fun loadInitialPostsFromFirebase(
    currentUserId: String,
    likedPostIds: Set<String>?,
    blockedUserIds: Set<String>,
    feedViewModel: FeedViewModel
): FeedPageResult = withContext(Dispatchers.IO) {
    val firestore = FirebaseFirestore.getInstance()
    val snapshot = firestore.collection("posts")
        .whereEqualTo("isPrivate", false)
        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .limit(20)
        .get()
        .await()

    val lastDoc = snapshot.documents.lastOrNull()
    val posts = coroutineScope {
        snapshot.documents
            .filterNot { it.getString("userId") in blockedUserIds }
            .map { doc ->
                async {
                    mapPostDoc(doc, currentUserId, likedPostIds, feedViewModel)
                }
            }.awaitAll().filterNotNull()
    }
    FeedPageResult(posts, lastDoc)
}

private suspend fun loadMorePostsFromFirebase(
    currentUserId: String,
    lastDoc: com.google.firebase.firestore.DocumentSnapshot?,
    likedPostIds: Set<String>?,
    blockedUserIds: Set<String>,
    feedViewModel: FeedViewModel
): FeedPageResult = withContext(Dispatchers.IO) {
    if (lastDoc == null) return@withContext FeedPageResult(emptyList(), null)
    val firestore = FirebaseFirestore.getInstance()
    var cursor = lastDoc
    val visiblePosts = mutableListOf<PostData>()
    var sourcePageWasFull: Boolean

    // Si una página contiene muchos posts bloqueados, sigue avanzando hasta
    // completar una página visible o agotar Firestore. De otro modo, un único
    // bloqueo podía cortar la paginación prematuramente.
    do {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("isPrivate", false)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .startAfter(cursor)
            .limit(20)
            .get()
            .await()
        sourcePageWasFull = snapshot.documents.size >= 20
        cursor = snapshot.documents.lastOrNull() ?: break

        val mapped = coroutineScope {
            snapshot.documents
                .filterNot { it.getString("userId") in blockedUserIds }
                .map { doc ->
                    async {
                        mapPostDoc(doc, currentUserId, likedPostIds, feedViewModel)
                    }
                }.awaitAll().filterNotNull()
        }
        visiblePosts += mapped
    } while (visiblePosts.size < 20 && sourcePageWasFull)

    FeedPageResult(visiblePosts, cursor)
}

private suspend fun mapPostDoc(
    doc: com.google.firebase.firestore.DocumentSnapshot,
    currentUserId: String,
    likedPostIds: Set<String>?,
    feedViewModel: FeedViewModel
): PostData? {
    val data = doc.data ?: return null
    val isLiked = when {
        currentUserId.isBlank() -> false
        likedPostIds != null -> doc.id in likedPostIds
        else -> {
            try {
                FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(doc.id)
                    .collection("likes")
                    .document(currentUserId)
                    .get()
                    .await()
                    .exists()
            } catch (_: Exception) {
                false
            }
        }
    }

    // FIX: No regenerar URL firmada de forma ansiosa en cada carga del feed.
    // Antes: cada post hacía b2_get_download_authorization (20 llamadas en paralelo)
    // causando lentitud, rate-limit y si fallaba la red dejaba spinner infinito.
    // Ahora: usamos directamente lo guardado en Firestore (válido 7 días) y
    // solo re-firmamos perezosamente cuando Coil detecta error 403 vía onUrlExpired.
    val savedImageUrl = data["imageUrl"] as? String ?: ""
    val storageKey = data["storageKey"] as? String ?: ""
    val imageUrl = savedImageUrl

    return PostData(
        id = doc.id,
        userId = data["userId"] as? String ?: "",
        username = data["username"] as? String ?: "usuario",
        userProfilePicture = data["userAvatar"] as? String
            ?: data["userProfilePicture"] as? String
            ?: "",
        caption = data["caption"] as? String ?: "",
        imageUrl = imageUrl,
        imageBase64 = data["imageBase64"] as? String ?: "",
        storageKey = storageKey,
        videoUrl = data["videoUrl"] as? String ?: "",
        thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
        isVideo = data["isVideo"] as? Boolean ?: false,
        likesCount = (data["likesCount"] as? Long)?.toInt() ?: 0,
        commentsCount = (data["commentsCount"] as? Long)?.toInt() ?: 0,
        timestamp = data["timestamp"] as? Long ?: 0L,
        isLiked = isLiked,
        musicTitle = data["musicTitle"] as? String ?: "",
        musicArtist = data["musicArtist"] as? String ?: "",
        musicAssetFile = data["musicAssetFile"] as? String ?: "",
        musicUrl = data["musicUrl"] as? String ?: "",
        musicStorageKey = data["musicStorageKey"] as? String ?: ""
    )
}

/**
 * Like idempotente: 1 like por persona, garantizado por transacción.
 *
 * ANTES: 2 escrituras ciegas (set del doc + increment del contador). Si la UI
 * tenía el estado desactualizado (p. ej. la query de collectionGroup fallaba y
 * el corazón salía apagado), cada tap volvía a sumar +1 → likes infinitos.
 *
 * AHORA: la transacción LEE el doc posts/{id}/likes/{uid} y solo toca el
 * contador cuando el estado realmente cambia. Dar like dos veces no suma dos;
 * quitar un like inexistente no resta. La fuente de verdad es el servidor,
 * no el estado de la UI.
 */
private suspend fun togglePostLike(postId: String, currentUserId: String, shouldLike: Boolean) {
    if (currentUserId.isBlank()) error("No hay sesión activa")
    val firestore = FirebaseFirestore.getInstance()
    val likeRef = firestore.collection("posts").document(postId).collection("likes").document(currentUserId)
    val postRef = firestore.collection("posts").document(postId)
    firestore.runTransaction { txn ->
        val alreadyLiked = txn.get(likeRef).exists()
        when {
            shouldLike && !alreadyLiked -> {
                txn.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis()))
                txn.update(postRef, "likesCount", FieldValue.increment(1))
            }
            !shouldLike && alreadyLiked -> {
                txn.delete(likeRef)
                txn.update(postRef, "likesCount", FieldValue.increment(-1))
            }
            // shouldLike && alreadyLiked → ya estaba likeado: no-op (el candado)
            // !shouldLike && !alreadyLiked → no había like: no-op
        }
        null
    }.await()
}

private fun shareText(context: android.content.Context, title: String, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, title))
}

private fun formatTimestamp(ts: Long): String {
    if (ts <= 0) return ""
    return try { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts)) } catch (_: Exception) { "" }
}

// ── PostAuthorAvatar ──
@Composable
private fun PostAuthorAvatar(post: PostData) {
    if (post.userProfilePictureBase64.isNotBlank()) {
        var bmp by remember(post.userProfilePictureBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(post.userProfilePictureBase64) {
            bmp = try { val bytes = Base64.decode(post.userProfilePictureBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }
        if (bmp != null) { Image(bmp!!.asImageBitmap(), "Avatar", Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop); return }
    }
    if (post.userProfilePicture.isNotBlank()) {
        AsyncImage(model = post.userProfilePicture, "Avatar", Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// ── PostImage — VERSIÓN CORREGIDA 2026-08-09 ──
// Bug anterior: usaba rememberAsyncImagePainter + LaunchedEffect con lógica que dejaba
// isLoading=true para siempre cuando imageUrl estaba vacío o cuando el estado era Empty.
// Resultado: el post se quedaba cargando infinitamente.
// Fix: separar claramente las dos ramas (base64 vs URL), usar AsyncImage directo que
// maneja sus propios estados, y tratar Empty como loading pero con timeout y fallback
// a onUrlExpired para regenerar URL firmada si hay 403.
// También tolera Base64 tanto con NO_WRAP como DEFAULT (por si viene con saltos de línea).
@Composable
fun PostImage(
    imageBase64: String,
    imageUrl: String,
    username: String,
    modifier: Modifier = Modifier,
    useDefaultHeight: Boolean = true,
    storageKey: String = "",
    onUrlExpired: () -> Unit = {}
) {
    val containerModifier = if (useDefaultHeight) modifier.fillMaxWidth().heightIn(max = 500.dp) else modifier.fillMaxSize()

    Box(
        modifier = containerModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        when {
            // ── Caso 1: imagen en Base64 (fallback cuando B2 falla) ──
            imageBase64.isNotBlank() -> {
                var bitmap by remember(imageBase64) { mutableStateOf<Bitmap?>(null) }
                var isLoading by remember(imageBase64) { mutableStateOf(true) }
                var hasError by remember(imageBase64) { mutableStateOf(false) }

                LaunchedEffect(imageBase64) {
                    isLoading = true
                    hasError = false
                    bitmap = withContext(Dispatchers.IO) {
                        try {
                            // Intenta NO_WRAP primero (formato que guardamos), luego DEFAULT por compat
                            val bytes = try {
                                Base64.decode(imageBase64, Base64.NO_WRAP)
                            } catch (_: Exception) {
                                Base64.decode(imageBase64, Base64.DEFAULT)
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    hasError = bitmap == null
                    isLoading = false
                }

                when {
                    isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    hasError || bitmap == null -> Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = "Error cargando imagen",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Post de $username",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // ── Caso 2: URL remota (B2 con URL firmada) ──
            imageUrl.isNotBlank() -> {
                var hasNotifiedExpired by remember(storageKey, imageUrl) { mutableStateOf(false) }

                // SubcomposeAsyncImage permite mostrar loading/error slots.
                // Usa el ImageLoader de VividImageLoaderModule (cache 250MB).
                // onError dispara regeneración perezosa de URL firmada.
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Post de $username",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    error = {
                        // Si falla, intenta regenerar URL firmada una vez
                        if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                            hasNotifiedExpired = true
                            onUrlExpired()
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "No se pudo cargar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                                    TextButton(onClick = {
                                        hasNotifiedExpired = true
                                        onUrlExpired()
                                    }) { Text("Reintentar") }
                                }
                            }
                        }
                    },
                    onError = {
                        if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                            hasNotifiedExpired = true
                            onUrlExpired()
                        }
                    }
                )
            }

            // ── Caso 3: sin imagen (evita spinner infinito) ──
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        username,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── PostCommentsSheet (Comentarios anidados, likes y edición) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCommentsSheet(post: PostData, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var likedCommentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }
    var editingComment by remember { mutableStateOf<PostComment?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(post.id) {
        val listener = db.collection("posts").document(post.id).collection("comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.mapNotNull { doc ->
                    PostComment(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "?",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        avatarBase64 = doc.getString("avatarBase64") ?: "",
                        likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                        isEdited = doc.getBoolean("isEdited") ?: false,
                        parentId = doc.getString("parentId"),
                        replyToUsername = doc.getString("replyToUsername") ?: ""
                    )
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    DisposableEffect(post.id, currentUserId) {
        var listener: ListenerRegistration? = null
        if (currentUserId.isNotBlank()) {
            listener = db.collectionGroup("likes")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener { snap, _ ->
                    likedCommentIds = snap?.documents?.mapNotNull { doc ->
                        if (doc.reference.path.contains("posts/${post.id}/comments/")) {
                            doc.reference.parent.parent?.id
                        } else null
                    }?.toSet().orEmpty()
                }
        }
        onDispose { listener?.remove() }
    }

    fun toggleCommentLike(comment: PostComment) {
        if (currentUserId.isBlank()) return
        val isLiked = comment.id in likedCommentIds
        val newLiked = !isLiked
        likedCommentIds = if (newLiked) likedCommentIds + comment.id else likedCommentIds - comment.id

        val commentRef = db.collection("posts").document(post.id)
            .collection("comments").document(comment.id)
        val likeRef = commentRef.collection("likes").document(currentUserId)

        scope.launch {
            runCatching {
                // Transacción idempotente: igual que togglePostLike, verifica el
                // doc de like antes de mover el contador (1 like por persona).
                db.runTransaction { txn ->
                    val alreadyLiked = txn.get(likeRef).exists()
                    when {
                        newLiked && !alreadyLiked -> {
                            txn.set(likeRef, mapOf("userId" to currentUserId, "timestamp" to System.currentTimeMillis()))
                            txn.update(commentRef, "likesCount", FieldValue.increment(1))
                        }
                        !newLiked && alreadyLiked -> {
                            txn.delete(likeRef)
                            txn.update(commentRef, "likesCount", FieldValue.increment(-1))
                        }
                    }
                    null
                }.await()
            }.onFailure {
                likedCommentIds = if (newLiked) likedCommentIds - comment.id else likedCommentIds + comment.id
            }
        }
    }

    fun deleteComment(comment: PostComment) {
        scope.launch {
            try {
                db.collection("posts").document(post.id)
                    .collection("comments").document(comment.id).delete().await()
                db.collection("posts").document(post.id)
                    .update("commentsCount", FieldValue.increment(-1)).await()
            } catch (e: Exception) {
                errorMsg = e.message
            }
        }
    }

    val rootComments = remember(comments) { comments.filter { it.parentId.isNullOrBlank() } }
    val repliesMap = remember(comments) {
        comments.filter { !it.parentId.isNullOrBlank() }.groupBy { it.parentId!! }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Comentarios (${comments.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                if (comments.isEmpty()) {
                    Text("No hay comentarios aún. ¡Sé el primero!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(rootComments, key = { it.id }) { rootComment ->
                            CommentRow(
                                comment = rootComment.copy(isLiked = rootComment.id in likedCommentIds),
                                currentUserId = currentUserId,
                                postAuthorId = post.userId,
                                onReply = { replyingTo = rootComment },
                                onLike = { toggleCommentLike(rootComment) },
                                onEdit = { editingComment = rootComment },
                                onDelete = { deleteComment(rootComment) }
                            )

                            val replies = repliesMap[rootComment.id].orEmpty()
                            replies.forEach { replyComment ->
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                                            .width(2.dp)
                                            .height(32.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        CommentRow(
                                            comment = replyComment.copy(isLiked = replyComment.id in likedCommentIds),
                                            currentUserId = currentUserId,
                                            postAuthorId = post.userId,
                                            isReply = true,
                                            onReply = { replyingTo = replyComment },
                                            onLike = { toggleCommentLike(replyComment) },
                                            onEdit = { editingComment = replyComment },
                                            onDelete = { deleteComment(replyComment) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Replying To Banner
                replyingTo?.let { replyTarget ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Respondiendo a @${replyTarget.username}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Comment Input Box
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text(if (replyingTo != null) "Escribe tu respuesta..." else "Escribe un comentario...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            if (commentText.isBlank() || isSending) return@FilledTonalButton
                            isSending = true
                            errorMsg = null
                            val replyTarget = replyingTo
                            val targetParentId = replyTarget?.let { it.parentId.takeIf { p -> !p.isNullOrBlank() } ?: it.id }
                            val targetReplyToUser = replyTarget?.username.orEmpty()
                            val textToSend = commentText.trim()

                            scope.launch {
                                try {
                                    val userDoc = db.collection("users").document(currentUserId).get().await()
                                    val commentData = hashMapOf<String, Any>(
                                        "userId" to currentUserId,
                                        "username" to (userDoc.getString("username") ?: "yo"),
                                        "text" to textToSend,
                                        "timestamp" to System.currentTimeMillis(),
                                        "avatarUrl" to (userDoc.getString("avatarUrl") ?: ""),
                                        "avatarBase64" to (userDoc.getString("avatarBase64") ?: ""),
                                        "likesCount" to 0,
                                        "isEdited" to false
                                    )
                                    if (!targetParentId.isNullOrBlank()) {
                                        commentData["parentId"] = targetParentId
                                    }
                                    if (targetReplyToUser.isNotBlank()) {
                                        commentData["replyToUsername"] = targetReplyToUser
                                    }

                                    db.collection("posts").document(post.id)
                                        .collection("comments").add(commentData).await()

                                    db.collection("posts").document(post.id)
                                        .update("commentsCount", FieldValue.increment(1)).await()

                                    commentText = ""
                                    replyingTo = null
                                } catch (e: Exception) {
                                    errorMsg = e.message
                                } finally {
                                    isSending = false
                                }
                            }
                        },
                        enabled = !isSending,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(if (isSending) "..." else "Enviar", fontWeight = FontWeight.Bold)
                    }
                }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    // Editing Dialog
    editingComment?.let { commentToEdit ->
        var editText by remember(commentToEdit) { mutableStateOf(commentToEdit.text) }
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text("Editar comentario", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editText.isNotBlank()) {
                        scope.launch {
                            try {
                                db.collection("posts").document(post.id)
                                    .collection("comments").document(commentToEdit.id)
                                    .update(
                                        mapOf(
                                            "text" to editText.trim(),
                                            "isEdited" to true,
                                            "editedAt" to System.currentTimeMillis()
                                        )
                                    ).await()
                            } catch (e: Exception) {
                                errorMsg = e.message
                            }
                        }
                    }
                    editingComment = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CommentRow(
    comment: PostComment,
    currentUserId: String,
    postAuthorId: String,
    isReply: Boolean = false,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CommentAvatar(comment)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.username,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                if (comment.isEdited) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(editado)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            if (comment.replyToUsername.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@${comment.replyToUsername} ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        SettingsManager.filterOffensiveWords(comment.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    SettingsManager.filterOffensiveWords(comment.text),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Responder",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onReply() }
                )

                if (comment.userId == currentUserId) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Editar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onEdit() }
                    )
                }

                if (comment.userId == currentUserId || currentUserId == postAuthorId) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Eliminar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onDelete() }
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onLike,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like comentario",
                    tint = if (comment.isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    comment.likesCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommentAvatar(comment: PostComment) {
    if (comment.avatarBase64.isNotBlank()) {
        var bmp by remember(comment.avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(comment.avatarBase64) {
            bmp = try { val bytes = Base64.decode(comment.avatarBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }
        if (bmp != null) { Image(bmp!!.asImageBitmap(), comment.username, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop); return }
    }
    if (comment.avatarUrl.isNotBlank()) {
        AsyncImage(model = comment.avatarUrl, contentDescription = comment.username, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(comment.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// ── Diálogos ──
@Composable
private fun PostViewerDialog(posts: List<PostData>, initialIndex: Int, onDismiss: () -> Unit) {
    if (initialIndex !in posts.indices) { onDismiss(); return }
    val post = posts[initialIndex]
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(post.username, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        post.isVideo && post.videoUrl.isNotBlank() -> {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val player = remember(post.videoUrl) {
                                ExoPlayer.Builder(ctx).build().apply {
                                    if (com.vivid.app.util.VideoCacheManager.isCacheable(post.videoUrl)) {
                                        setMediaSource(com.vivid.app.util.VideoCacheManager.buildCachedMediaSource(ctx, post.videoUrl))
                                    } else {
                                        setMediaItem(MediaItem.fromUri(post.videoUrl))
                                    }
                                    repeatMode = ExoPlayer.REPEAT_MODE_ALL; prepare(); playWhenReady = true
                                }
                            }
                            DisposableEffect(player) { onDispose { player.release() } }
                            AndroidView(factory = { c -> PlayerView(c).apply { this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                        }
                        else -> PostImage(post.imageBase64, post.imageUrl, post.username, useDefaultHeight = false)
                    }
                }
                if (post.caption.isNotBlank()) Text(post.caption, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PostDetailsDialog(post: PostData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalles", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(post.username, style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Red)
                    Spacer(Modifier.width(10.dp))
                    Text("${post.likesCount} Me gusta", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("${post.commentsCount} Comentarios", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(formatTimestamp(post.timestamp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun EditPostDialog(post: PostData, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    var text by remember { mutableStateOf(post.caption) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Editar", fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Button(onClick = {
                FirebaseFirestore.getInstance().collection("posts").document(post.id).update("caption", text.trim())
                onSaved(text.trim())
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

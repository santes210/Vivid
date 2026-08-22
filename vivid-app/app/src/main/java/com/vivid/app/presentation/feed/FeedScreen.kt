package com.vivid.app.presentation.feed

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.vivid.app.ui.components.VividPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.vivid.app.R
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.presentation.report.ReportHelper
import com.vivid.app.presentation.stories.StoriesTray
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.components.VividWordmark
import com.vivid.app.util.CrashReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.vivid.app.theme.VividSpace

private const val TAG = "FeedScreen"

/** Si Firestore no entrega nada en este tiempo, se muestra el estado de
 *  error con reintento en vez de un spinner eterno (solo carga inicial). */
private const val FEED_INITIAL_LOAD_TIMEOUT_MS = 15_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenMessages: () -> Unit,
    onOpenRequests: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenUserProfile: (userId: String) -> Unit = {},
    onOpenStoryViewer: (storyId: String) -> Unit = {},
    onCreateStory: () -> Unit = {}
) {
    val feedViewModel: FeedViewModel = hiltViewModel()
    val blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val blockedUserIds = blockedUsersState.userIds
    val currentUserId = feedViewModel.currentUserId
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var lastVisibleDoc by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    var likedPostIds by remember { mutableStateOf<Set<String>?>(null) }
    var followingUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingFollowUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshAttemptedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var resignedImageUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resignedMusicUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resignedVideoUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stalePosts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var lastCacheWriteAt by remember { mutableLongStateOf(0L) }

    val listState = rememberLazyListState()

    var followRequestsCount by remember { mutableIntStateOf(0) }
    var selectedPostForComments by remember { mutableStateOf<PostData?>(null) }
    var selectedPostViewerId by remember { mutableStateOf<String?>(null) }
    var selectedPostForDetails by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForEdit by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForDelete by remember { mutableStateOf<PostData?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportPostId by remember { mutableStateOf("") }
    var reportPostUser by remember { mutableStateOf("") }
    var reportPostCaption by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf(context.getString(R.string.report_reason_inappropriate)) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val displayPosts = remember(posts, savedPostIds) {
        posts.map { post ->
            val saved = post.id in savedPostIds
            if (post.isSaved == saved) post else post.copy(isSaved = saved)
        }
    }

    val onOpenPost = remember { { post: PostData -> selectedPostViewerId = post.id } }
    val onOpenComments = remember { { post: PostData -> selectedPostForComments = post } }
    val onOpenDetails = remember { { post: PostData -> selectedPostForDetails = post } }
    val onEditPost = remember { { post: PostData -> selectedPostForEdit = post } }
    val onDeletePost = remember { { post: PostData -> selectedPostForDelete = post } }
    val onReportPost = remember {
        { pid: String, user: String, cap: String ->
            showReportDialog = true
            reportPostId = pid
            reportPostUser = user
            reportPostCaption = cap
        }
    }
    val onToggleFollow = remember {
        { targetUserId: String ->
            feedViewModel.toggleFollowUser(targetUserId) { action ->
                val msg = when (action) {
                    FollowActionResult.FOLLOWED -> context.getString(R.string.follow_now_following)
                    FollowActionResult.UNFOLLOWED -> context.getString(R.string.follow_unfollowed)
                    FollowActionResult.REQUESTED -> context.getString(R.string.follow_requested)
                    FollowActionResult.REQUEST_CANCELLED -> context.getString(R.string.follow_request_cancelled)
                    null -> context.getString(R.string.follow_error)
                }
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }
    val onToggleSave = remember {
        { post: PostData ->
            val shouldSave = !post.isSaved
            feedViewModel.toggleSavePost(post.id, currentUserId, shouldSave) { _, _ ->
                val msg = if (shouldSave) context.getString(R.string.feed_post_saved) else context.getString(R.string.feed_post_unsaved)
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }
    val onToggleLike = remember {
        { target: PostData ->
            val newLiked = !target.isLiked
            posts = posts.map {
                if (it.id == target.id) it.copy(isLiked = newLiked, likesCount = (it.likesCount + if (newLiked) 1 else -1).coerceAtLeast(0)) else it
            }
            likedPostIds = likedPostIds?.let { ids -> if (newLiked) ids + target.id else ids - target.id }
            feedViewModel.togglePostLike(
                target.id, currentUserId, newLiked,
                onFailure = { e ->
                    posts = posts.map { if (it.id == target.id) target else it }
                    likedPostIds = likedPostIds?.let { ids -> if (newLiked) ids - target.id else ids + target.id }
                    scope.launch { snackbarHostState.showSnackbar(e.message ?: context.getString(R.string.feed_like_error)) }
                }
            )
        }
    }
    val onImageUrlExpired = remember {
        { post: PostData ->
            val key = post.storageKey
            if (key.isNotBlank() && post.id !in refreshAttemptedIds) {
                refreshAttemptedIds = refreshAttemptedIds + post.id
                scope.launch {
                    feedViewModel.refreshSignedUrl(key)?.let { freshUrl ->
                        posts = posts.map { if (it.id == post.id) it.copy(imageUrl = freshUrl) else it }
                        resignedImageUrls = resignedImageUrls + (post.id to freshUrl)
                        feedViewModel.saveResignedImageUrl(post.id, freshUrl)
                    }
                }
            }
        }
    }
    val onMusicUrlExpired = remember {
        { post: PostData ->
            val mKey = post.musicStorageKey
            if (mKey.isNotBlank()) {
                scope.launch {
                    feedViewModel.refreshSignedUrl(mKey)?.let { freshUrl ->
                        posts = posts.map { if (it.id == post.id) it.copy(musicUrl = freshUrl) else it }
                        resignedMusicUrls = resignedMusicUrls + (post.id to freshUrl)
                        feedViewModel.saveResignedMusicUrl(post.id, freshUrl)
                    }
                }
            }
        }
    }
    val onVideoUrlExpired = remember {
        { post: PostData ->
            val vKey = post.storageKey
            if (vKey.isNotBlank()) {
                scope.launch {
                    feedViewModel.refreshSignedUrl(vKey)?.let { freshUrl ->
                        posts = posts.map { if (it.id == post.id) it.copy(videoUrl = freshUrl) else it }
                        resignedVideoUrls = resignedVideoUrls + (post.id to freshUrl)
                        feedViewModel.saveResignedVideoUrl(post.id, freshUrl)
                    }
                }
            }
        }
    }
    val onSharePost = remember {
        { post: PostData ->
            val deepLink = "vivid://post/${post.id}"
            shareText(context, context.getString(R.string.feed_share_title), buildString {
                append(context.getString(R.string.feed_share_body, post.username))
                if (post.caption.isNotBlank()) append("\n\n${post.caption}")
                append("\n\n$deepLink")
            })
        }
    }

    // ── Initial load: single likes query + real-time listener for posts ──
    LaunchedEffect(currentUserId) {
        isLoading = true
        val likedIds = feedViewModel.fetchLikedPostIds(currentUserId)
        likedPostIds = likedIds
        isLoading = false
    }

    // ── Room cache (offline / fast startup) ──
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            runCatching {
                val cached = feedViewModel.getCachedPosts()
                if (cached.isNotEmpty()) {
                    resignedImageUrls = cached.filter { it.imageUrl.isNotBlank() }.associate { it.id to it.imageUrl }
                    resignedMusicUrls = cached.filter { it.musicUrl.isNotBlank() }.associate { it.id to it.musicUrl }
                    resignedVideoUrls = cached.filter { it.videoUrl.isNotBlank() }.associate { it.id to it.videoUrl }
                    if (feedViewModel.isPostCacheFresh()) {
                        posts = feedViewModel.cachedPostsToData(cached)
                    } else {
                        stalePosts = feedViewModel.cachedPostsToData(cached)
                    }
                }
            }
        }
    }

    // Remove blocked users' posts immediately
    LaunchedEffect(blockedUserIds) {
        posts = posts.filterNot { it.userId in blockedUserIds }
    }

    // ── Firestore snapshot listeners for feed ──
    DisposableEffect(currentUserId, likedPostIds, blockedUserIds, blockedUsersState.isLoaded, followingUserIds, retryKey) {
        if (currentUserId.isBlank() || !blockedUsersState.isLoaded) {
            onDispose { }
        } else {
            isLoading = true
            isError = false
            val db = FirebaseFirestore.getInstance()
            val registrations = mutableListOf<ListenerRegistration>()
            val pages = mutableMapOf<String, List<DocumentSnapshot>>()

            // Watchdog de la carga inicial: si Firestore no entrega nada en
            // 15 s, se pasa al estado de error con reintento. Se cancela en
            // cuanto llega la primera snapshot real.
            var initialLoadTimeout: Job? = null

            fun publishVisiblePosts() {
                initialLoadTimeout?.cancel()
                val documents = pages.values.flatten().distinctBy { it.id }
                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                posts = documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val authorId = data["userId"] as? String ?: ""
                        if (authorId in blockedUserIds) return@mapNotNull null
                        PostData(
                            id = doc.id,
                            userId = authorId,
                            username = data["username"] as? String ?: "user",
                            userProfilePicture = data["userAvatar"] as? String ?: data["userProfilePicture"] as? String ?: "",
                            caption = data["caption"] as? String ?: "",
                            imageUrl = resignedImageUrls[doc.id] ?: data["imageUrl"] as? String ?: "",
                            imageBase64 = data["imageBase64"] as? String ?: "",
                            storageKey = data["storageKey"] as? String ?: "",
                            videoUrl = resignedVideoUrls[doc.id] ?: data["videoUrl"] as? String ?: "",
                            thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
                            isVideo = data["isVideo"] as? Boolean ?: false,
                            likesCount = (data["likesCount"] as? Long)?.toInt() ?: 0,
                            commentsCount = (data["commentsCount"] as? Long)?.toInt() ?: 0,
                            timestamp = data["timestamp"] as? Long ?: 0L,
                            isLiked = likedPostIds?.contains(doc.id) == true,
                            musicTitle = data["musicTitle"] as? String ?: "",
                            musicArtist = data["musicArtist"] as? String ?: "",
                            musicAssetFile = data["musicAssetFile"] as? String ?: "",
                            musicUrl = resignedMusicUrls[doc.id] ?: data["musicUrl"] as? String ?: "",
                            musicStorageKey = data["musicStorageKey"] as? String ?: ""
                        )
                    } catch (_: Exception) { null }
                }
                isLoading = false

                if (posts.isNotEmpty()) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastCacheWriteAt >= FEED_CACHE_WRITE_INTERVAL_MS) {
                        lastCacheWriteAt = nowMs
                        scope.launch {
                            feedViewModel.cachePosts(posts)
                            com.vivid.app.util.VividCacheManager.markPostsCached(context)
                        }
                    }
                }
            }

            registrations += db.collection("posts")
                .whereEqualTo("isPrivate", false)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snap, err ->
                    if (snap != null) {
                        pages["public"] = snap.documents
                        lastVisibleDoc = snap.documents.lastOrNull()
                        hasMore = snap.size() >= 20
                        publishVisiblePosts()
                        snap.documentChanges.forEach { change ->
                            if (change.type == DocumentChange.Type.REMOVED) {
                                scope.launch { feedViewModel.deleteCachedPost(change.document.id) }
                            }
                        }
                    } else if (err != null && pages.isEmpty()) {
                        CrashReporter.recordNonFatal(TAG, err, "Listener del feed falló en la carga inicial")
                        isLoading = false
                        if (posts.isEmpty() && stalePosts.isNotEmpty()) posts = stalePosts
                        isError = posts.isEmpty()
                    }
                }

            (followingUserIds + currentUserId).chunked(30).forEachIndexed { index, privateAuthors ->
                registrations += db.collection("posts")
                    .whereIn("userId", privateAuthors)
                    .whereEqualTo("isPrivate", true)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(20)
                    .addSnapshotListener { snap, _ ->
                        if (snap != null) {
                            pages["private_$index"] = snap.documents
                            publishVisiblePosts()
                            snap.documentChanges.forEach { change ->
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    scope.launch { feedViewModel.deleteCachedPost(change.document.id) }
                                }
                            }
                        }
                    }
            }

            // Arranca el watchdog DESPUÉS de registrar los listeners: solo
            // dispara si ninguna snapshot llegó en el tiempo límite.
            initialLoadTimeout = scope.launch {
                delay(FEED_INITIAL_LOAD_TIMEOUT_MS)
                if (pages.isEmpty()) {
                    isLoading = false
                    if (posts.isEmpty() && stalePosts.isNotEmpty()) posts = stalePosts
                    isError = posts.isEmpty()
                }
            }

            onDispose {
                initialLoadTimeout?.cancel()
                registrations.forEach { it.remove() }
            }
        }
    }

    // ── Pagination ──
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
                loadMorePostsFromFirebase(currentUserId, lastVisibleDoc, likedPostIds, blockedUserIds, feedViewModel)
            }.getOrElse { FeedPageResult(emptyList(), null) }
            if (result.posts.isNotEmpty()) {
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

    // ── Realtime listeners for follow requests, following, pending, saved ──
    DisposableEffect(currentUserId) {
        var regRequests: ListenerRegistration? = null
        var regFollowing: ListenerRegistration? = null
        var regPending: ListenerRegistration? = null
        var regSaved: ListenerRegistration? = null

        if (currentUserId.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            regRequests = db.collection("users").document(currentUserId)
                .collection("followRequests").addSnapshotListener { snap, _ -> followRequestsCount = snap?.size() ?: 0 }
            regFollowing = db.collection("users").document(currentUserId)
                .collection("following").addSnapshotListener { snap, _ -> followingUserIds = snap?.documents?.map { it.id }?.toSet().orEmpty() }
            regPending = db.collection("users").document(currentUserId)
                .collection("sentFollowRequests").addSnapshotListener { snap, _ -> pendingFollowUserIds = snap?.documents?.map { it.id }?.toSet().orEmpty() }
            regSaved = db.collection("users").document(currentUserId)
                .collection("savedPosts").addSnapshotListener { snap, _ -> savedPostIds = snap?.documents?.map { it.id }?.toSet().orEmpty() }
        }
        onDispose {
            regRequests?.remove(); regFollowing?.remove(); regPending?.remove(); regSaved?.remove()
        }
    }

    // ── UI ──
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { VividWordmark() },
                actions = {
                    BadgedBox(
                        badge = { if (followRequestsCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text(followRequestsCount.coerceAtMost(9).toString(), color = MaterialTheme.colorScheme.onError) } },
                        modifier = Modifier.padding(end = VividSpace.xs)
                    ) {
                        IconButton(onClick = onOpenRequests) { Icon(Icons.Default.Notifications, stringResource(R.string.feed_requests), tint = MaterialTheme.colorScheme.onSurface) }
                    }
                    IconButton(onClick = onOpenMessages) { Icon(Icons.Default.Email, stringResource(R.string.feed_messages), tint = MaterialTheme.colorScheme.onSurface) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VividOfflineBannerHost()

            VividPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        likedPostIds = feedViewModel.fetchLikedPostIds(currentUserId)
                        delay(650)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = VividSpace.xs),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item(key = "stories") {
                        StoriesTray(onStoryClick = { onOpenStoryViewer(it.id) }, onCreateStory = onCreateStory)
                    }

                    when {
                        isLoading -> { items(3, key = { "skeleton_$it" }) { FeedSkeleton() } }
                        isError -> { item(key = "error") { FeedErrorState(onRetry = { retryKey++ }) } }
                        displayPosts.isEmpty() -> { item(key = "empty") { FeedEmptyState() } }
                        else -> {
                            items(displayPosts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    currentUserId = currentUserId,
                                    isFollowingAuthor = post.userId in followingUserIds,
                                    hasPendingRequestToAuthor = post.userId in pendingFollowUserIds,
                                    onOpenPost = onOpenPost,
                                    onOpenAuthorProfile = onOpenUserProfile,
                                    onOpenComments = onOpenComments,
                                    onOpenDetails = onOpenDetails,
                                    onEditPost = onEditPost,
                                    onDeletePost = onDeletePost,
                                    onToggleFollow = onToggleFollow,
                                    onToggleSave = onToggleSave,
                                    onToggleLike = onToggleLike,
                                    onShare = onSharePost,
                                    onReportPost = onReportPost,
                                    onImageUrlExpired = onImageUrlExpired,
                                    onMusicUrlExpired = onMusicUrlExpired,
                                    onVideoUrlExpired = onVideoUrlExpired
                                )
                            }
                            if (isLoadingMore) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(VividSpace.m), contentAlignment = Alignment.Center) {
                                        LoadingIndicator(modifier = Modifier.size(38.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──
    selectedPostForComments?.let { post -> PostCommentsSheet(post = post, viewModel = feedViewModel, onDismiss = { selectedPostForComments = null }) }
    selectedPostViewerId?.let { viewerId ->
        val viewerIndex = posts.indexOfFirst { it.id == viewerId }
        if (viewerIndex >= 0) {
            PostViewerDialog(posts = posts, initialIndex = viewerIndex, onDismiss = { selectedPostViewerId = null })
        }
    }
    selectedPostForDetails?.let { post -> PostDetailsDialog(post = post, onDismiss = { selectedPostForDetails = null }) }
    selectedPostForEdit?.let { post ->
        EditPostDialog(post = post, viewModel = feedViewModel, onDismiss = { selectedPostForEdit = null }, onSaved = { cap ->
            selectedPostForEdit = null; posts = posts.map { if (it.id == post.id) it.copy(caption = cap) else it }
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.feed_post_updated)) }
        })
    }
    selectedPostForDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedPostForDelete = null },
            title = { Text(stringResource(R.string.feed_delete_confirm_title)) },
            text = { Text(stringResource(R.string.feed_delete_confirm_body)) },
            confirmButton = {
                Button(onClick = {
                    feedViewModel.deletePost(post.id, post.storageKey,
                        onSuccess = {
                            posts = posts.filter { it.id != post.id }
                            likedPostIds = likedPostIds?.let { ids -> ids - post.id }
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.feed_post_deleted)) }
                            selectedPostForDelete = null
                        },
                        onFailure = { e ->
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.feed_error_prefix, e.message ?: "")) }
                            selectedPostForDelete = null
                        }
                    )
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.feed_delete)) }
            },
            dismissButton = { TextButton(onClick = { selectedPostForDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // ── Report dialog ──
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text(stringResource(R.string.report_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.report_reason_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(VividSpace.xxs))
                    val reasons = listOf(
                        stringResource(R.string.report_reason_spam),
                        stringResource(R.string.report_reason_inappropriate),
                        stringResource(R.string.report_reason_harassment),
                        stringResource(R.string.report_reason_other)
                    )
                    reasons.forEach { reason ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason })
                            Text(reason, modifier = Modifier.padding(start = VividSpace.xxs))
                        }
                    }
                    Spacer(Modifier.height(VividSpace.xs))
                    Text(stringResource(R.string.report_post_preview, reportPostUser, reportPostCaption.take(60)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val opened = ReportHelper.sendPostReport(context, reportPostId, reportPostUser, reportPostCaption, reportReason)
                        showReportDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (opened) context.getString(R.string.report_email_opened) else context.getString(R.string.report_no_email_app)
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.report_send)) }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

// ── Pagination helpers ──

private suspend fun loadMorePostsFromFirebase(
    currentUserId: String,
    lastDoc: DocumentSnapshot?,
    likedPostIds: Set<String>?,
    blockedUserIds: Set<String>,
    feedViewModel: FeedViewModel
): FeedPageResult = withContext(Dispatchers.IO) {
    if (lastDoc == null) return@withContext FeedPageResult(emptyList(), null)
    val firestore = FirebaseFirestore.getInstance()
    var cursor = lastDoc
    val visiblePosts = mutableListOf<PostData>()
    var sourcePageWasFull: Boolean

    do {
        val snapshot = firestore.collection("posts")
            .whereEqualTo("isPrivate", false)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(cursor)
            .limit(20)
            .get()
            .await()
        sourcePageWasFull = snapshot.documents.size >= 20
        cursor = snapshot.documents.lastOrNull() ?: break

        val mapped = coroutineScope {
            snapshot.documents
                .filterNot { it.getString("userId") in blockedUserIds }
                .map { doc -> async { mapPostDoc(doc, currentUserId, likedPostIds) } }
                .awaitAll().filterNotNull()
        }
        visiblePosts += mapped
    } while (visiblePosts.size < 20 && sourcePageWasFull)

    FeedPageResult(visiblePosts, cursor)
}

private suspend fun mapPostDoc(
    doc: DocumentSnapshot,
    currentUserId: String,
    likedPostIds: Set<String>?
): PostData? {
    val data = doc.data ?: return null
    val isLiked = when {
        currentUserId.isBlank() -> false
        likedPostIds != null -> doc.id in likedPostIds
        else -> {
            try {
                FirebaseFirestore.getInstance()
                    .collection("posts").document(doc.id)
                    .collection("likes").document(currentUserId)
                    .get().await().exists()
            } catch (_: Exception) { false }
        }
    }

    return PostData(
        id = doc.id,
        userId = data["userId"] as? String ?: "",
        username = data["username"] as? String ?: "user",
        userProfilePicture = data["userAvatar"] as? String ?: data["userProfilePicture"] as? String ?: "",
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
        isLiked = isLiked,
        musicTitle = data["musicTitle"] as? String ?: "",
        musicArtist = data["musicArtist"] as? String ?: "",
        musicAssetFile = data["musicAssetFile"] as? String ?: "",
        musicUrl = data["musicUrl"] as? String ?: "",
        musicStorageKey = data["musicStorageKey"] as? String ?: ""
    )
}

private fun shareText(context: android.content.Context, title: String, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, title))
}

package com.vivid.app.presentation.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.vivid.app.ui.components.VividPullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.ui.components.VividAlertDialog
import com.vivid.app.ui.components.VividDialogTone
import com.vivid.app.ui.components.VividSnackbarHost

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
    /** Tocar un #hashtag del caption abre Explorar filtrado por ese tag. */
    onOpenHashtag: (tag: String) -> Unit = {},
    onOpenStoryViewer: (storyId: String) -> Unit = {},
    onCreateStory: () -> Unit = {}
) {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var followingOnly by rememberSaveable(uid) { mutableStateOf(false) }
    val stateHolder = rememberSaveableStateHolder()
    key(uid, followingOnly) {
        stateHolder.SaveableStateProvider("$uid/$followingOnly") {
            FeedContent(onOpenMessages, onOpenRequests, onOpenProfile, onOpenUserProfile,
                onOpenHashtag, onOpenStoryViewer, onCreateStory, followingOnly,
                onSelectFollowing = {
                    com.vivid.app.util.ExoPlayerPool.pauseAll()
                    followingOnly = it
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    onOpenMessages: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenStoryViewer: (String) -> Unit,
    onCreateStory: () -> Unit,
    followingOnly: Boolean,
    onSelectFollowing: (Boolean) -> Unit
) {
    val feedViewModel: FeedViewModel = hiltViewModel()
    val blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val blockedUserIds = blockedUsersState.userIds
    val currentUserId = feedViewModel.currentUserId
    val context = LocalContext.current
    val (discovery, discoveryStore) = rememberFeedPreferences(currentUserId)
    var showResetDiscovery by remember(currentUserId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember(currentUserId) { SnackbarHostState() }

    val returning = remember(currentUserId) { feedViewModel.returnStates[followingOnly]?.takeIf { it.userId == currentUserId } }
    var posts by remember(currentUserId) { mutableStateOf(returning?.posts.orEmpty()) }
    var isLoading by remember(currentUserId) { mutableStateOf(true) }
    var isError by remember(currentUserId) { mutableStateOf(false) }
    var isRefreshing by remember(currentUserId) { mutableStateOf(false) }
    var retryKey by remember(currentUserId) { mutableStateOf(0) }
    var lastVisibleDoc by remember(currentUserId) { mutableStateOf(returning?.cursor) }
    var isLoadingMore by remember(currentUserId) { mutableStateOf(false) }
    var paginationFailed by remember(currentUserId) { mutableStateOf(false) }
    var paginationRetry by remember(currentUserId) { mutableIntStateOf(0) }
    var paginatedIds by remember(currentUserId) { mutableStateOf(returning?.paginatedIds.orEmpty()) }
    var hasMore by remember(currentUserId) { mutableStateOf(returning?.hasMore ?: true) }

    var likedPostIds by remember(currentUserId) { mutableStateOf<Set<String>?>(null) }
    var followingUserIds by remember(currentUserId) { mutableStateOf(returning?.following.orEmpty()) }
    var pendingFollowUserIds by remember(currentUserId) { mutableStateOf<Set<String>>(emptySet()) }
    var savedPostIds by remember(currentUserId) { mutableStateOf<Set<String>>(emptySet()) }
    var refreshAttemptedIds by remember(currentUserId) { mutableStateOf<Set<String>>(emptySet()) }
    var resignedImageUrls by remember(currentUserId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resignedMusicUrls by remember(currentUserId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resignedVideoUrls by remember(currentUserId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stalePosts by remember(currentUserId) { mutableStateOf<List<PostData>>(emptyList()) }
    var lastCacheWriteAt by remember(currentUserId) { mutableLongStateOf(0L) }

    var queryAudience by remember(currentUserId) { mutableStateOf(returning?.let { it.following to it.blocked }) }
    SideEffect {
        if (blockedUsersState.isLoaded) feedViewModel.returnStates[followingOnly] = FeedReturnState(
            currentUserId, posts, paginatedIds, lastVisibleDoc, hasMore, followingUserIds, blockedUserIds
        )
    }
    val savedScroll = feedViewModel.returnScrolls[followingOnly]?.takeIf { it.first == currentUserId }
    val listState = rememberLazyListState(savedScroll?.second ?: 0, savedScroll?.third ?: 0)
    DisposableEffect(currentUserId, listState) {
        onDispose {
            feedViewModel.returnScrolls[followingOnly] = Triple(currentUserId, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    val postIds = remember(posts) { posts.mapTo(hashSetOf()) { it.id } }
    val firstPostVisible by remember(postIds, listState) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key in postIds } }
    }
    com.vivid.app.util.MeasureFirstFeedPost(firstPostVisible)

    var followRequestsCount by remember(currentUserId) { mutableIntStateOf(0) }
    var selectedPostForComments by remember(currentUserId) { mutableStateOf<PostData?>(null) }
    var selectedPostViewerId by remember(currentUserId) { mutableStateOf<String?>(null) }
    var selectedPostForDetails by remember(currentUserId) { mutableStateOf<PostData?>(null) }
    var selectedPostForEdit by remember(currentUserId) { mutableStateOf<PostData?>(null) }
    var selectedPostForDelete by remember(currentUserId) { mutableStateOf<PostData?>(null) }
    var showReportDialog by remember(currentUserId) { mutableStateOf(false) }
    var reportPostId by remember(currentUserId) { mutableStateOf("") }
    var reportPostUser by remember(currentUserId) { mutableStateOf("") }
    var reportPostCaption by remember(currentUserId) { mutableStateOf("") }
    var reportReason by remember(currentUserId) { mutableStateOf(context.getString(R.string.report_reason_inappropriate)) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val displayPosts = remember(posts, savedPostIds, likedPostIds, discovery, followingUserIds, followingOnly, blockedUserIds) {
        posts.filter { discovery.allows(it) && it.userId !in blockedUserIds && (!followingOnly || it.userId in followingUserIds) }.map { post ->
            val saved = post.id in savedPostIds
            post.copy(isSaved = saved, isLiked = likedPostIds?.contains(post.id) ?: post.isLiked)
        }
    }

    val onOpenPost = remember(currentUserId) { { post: PostData -> com.vivid.app.util.ExoPlayerPool.pauseAll(); selectedPostViewerId = post.id } }
    val onOpenComments = remember(currentUserId) { { post: PostData -> com.vivid.app.util.ExoPlayerPool.pauseAll(); selectedPostForComments = post } }
    val onOpenDetails = remember(currentUserId) { { post: PostData -> selectedPostForDetails = post } }
    val onEditPost = remember(currentUserId) { { post: PostData -> selectedPostForEdit = post } }
    val onDeletePost = remember(currentUserId) { { post: PostData -> selectedPostForDelete = post } }
    val onReportPost = remember(currentUserId) {
        { pid: String, user: String, cap: String ->
            showReportDialog = true
            reportPostId = pid
            reportPostUser = user
            reportPostCaption = cap
        }
    }
    val onToggleFollow = remember(currentUserId) {
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
    val onToggleSave = remember(currentUserId) {
        { post: PostData ->
            val shouldSave = !post.isSaved
            feedViewModel.toggleSavePost(post.id, currentUserId, shouldSave) { success, _ ->
                val msg = if (!success) context.getString(R.string.feed_save_failed) else if (shouldSave) context.getString(R.string.feed_post_saved) else context.getString(R.string.feed_post_unsaved)
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }
    val onToggleLike = remember(currentUserId) {
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
    val onImageUrlExpired = remember(currentUserId) {
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
    val onMusicUrlExpired = remember(currentUserId) {
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
    val onVideoUrlExpired = remember(currentUserId) {
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
    var sharedPost by remember(currentUserId) { mutableStateOf<PostData?>(null) }
    val onSharePost: (PostData) -> Unit = {
        com.vivid.app.util.ExoPlayerPool.pauseAll()
        sharedPost = it
    }
    sharedPost?.let { post -> PostShareSheet(post, onDismiss = { sharedPost = null }) }

    // ── Initial load: single likes query + real-time listener for posts ──
    LaunchedEffect(currentUserId) {
        val likedIds = feedViewModel.fetchLikedPostIds(currentUserId)
        likedPostIds = likedIds
    }

    // ── Room cache (offline / fast startup) ──
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank() && !followingOnly) {
            runCatching {
                val cached = feedViewModel.getCachedPosts()
                if (cached.isNotEmpty()) {
                    resignedImageUrls = cached.filter { it.imageUrl.isNotBlank() }.associate { it.id to it.imageUrl }
                    resignedMusicUrls = cached.filter { it.musicUrl.isNotBlank() }.associate { it.id to it.musicUrl }
                    resignedVideoUrls = cached.filter { it.videoUrl.isNotBlank() }.associate { it.id to it.videoUrl }
                    if (feedViewModel.isPostCacheFresh() && posts.isEmpty()) {
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
    DisposableEffect(currentUserId, blockedUserIds, blockedUsersState.isLoaded, followingUserIds, retryKey) {
        if (currentUserId.isBlank() || !blockedUsersState.isLoaded) {
            onDispose { }
        } else if (followingOnly) {
            val audience = followingUserIds to blockedUserIds
            // Retain an unchanged tab's pages on return; refresh explicitly or on audience changes.
            if (queryAudience == audience && returning != null && (returning.cursor != null || !returning.hasMore) && retryKey == 0) {
                isLoading = false
                onDispose { }
            } else {
                queryAudience = audience
                isLoading = true
                isError = false
                paginationFailed = false
                val job = scope.launch {
                    try {
                        val result = kotlinx.coroutines.withTimeout(20_000) {
                            loadFollowingPage(currentUserId, null, likedPostIds, followingUserIds - blockedUserIds)
                        }
                        posts = result.posts
                        lastVisibleDoc = result.lastDoc
                        hasMore = result.hasMore
                        paginatedIds = result.posts.map { it.id }.toSet()
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        isError = true
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        isError = true
                    } finally { isLoading = false; isRefreshing = false }
                }
                onDispose { job.cancel() }
            }
        } else {
            isLoading = posts.isEmpty()
            isError = false
            val audience = followingUserIds to blockedUserIds
            if (queryAudience != audience || retryKey > 0) {
                paginatedIds = emptySet()
                lastVisibleDoc = null
                hasMore = true
            }
            queryAudience = audience
            paginationFailed = false
            val db = FirebaseFirestore.getInstance()
            val registrations = mutableListOf<ListenerRegistration>()
            val pages = mutableMapOf<String, List<DocumentSnapshot>>()
            val privateGroups = (followingUserIds + currentUserId).chunked(30)
            val pendingSources = (listOf("public") + privateGroups.indices.map { "private_$it" }).toMutableSet()
            val failedSources = mutableSetOf<String>()
            var active = true

            // Watchdog de la carga inicial: si Firestore no entrega nada en
            // 15 s, se pasa al estado de error con reintento. Se cancela en
            // cuanto llega la primera snapshot real.
            var initialLoadTimeout: Job? = null

            fun publishVisiblePosts() {
                if (pendingSources.isEmpty()) initialLoadTimeout?.cancel()
                val documents = pages.values.flatten().distinctBy { it.id }
                    .sortedByDescending { it.getLong("timestamp") ?: 0L }
                val headPosts = documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val authorId = data["userId"] as? String ?: ""
                        if (authorId in blockedUserIds) return@mapNotNull null
                        val visibility = data["visibility"] as? String ?: "public"
                        // "Solo amigos": visible para el autor y sus seguidores.
                        // El listener público global también devuelve estos
                        // documentos, así que aquí se descartan los de autores
                        // que el usuario no sigue.
                        if (visibility == "friends" &&
                            authorId != currentUserId &&
                            authorId !in followingUserIds
                        ) return@mapNotNull null
                        PostData(
                            id = doc.id,
                            userId = authorId,
                            username = data["username"] as? String ?: "user",
                            userProfilePicture = data["userAvatar"] as? String ?: data["userProfilePicture"] as? String ?: "",
                            caption = data["caption"] as? String ?: "",
                            altText = data["altText"] as? String ?: "",
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
                            musicStorageKey = data["musicStorageKey"] as? String ?: "",
                            hashtags = (data["hashtags"] as? List<*>)?.mapNotNull { it as? String }?.map { it.lowercase() }.orEmpty(),
                            visibility = visibility
                        )
                    } catch (_: Exception) { null }
                }
                posts = mergeFeedPages(headPosts, posts.filter { it.id in paginatedIds })
                isLoading = pendingSources.isNotEmpty() && posts.isEmpty()
                isRefreshing = isRefreshing && pendingSources.isNotEmpty()
                isError = failedSources.isNotEmpty()

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
                    if (!active) return@addSnapshotListener
                    pendingSources.remove("public")
                    if (snap != null) {
                        failedSources.remove("public")
                        pages["public"] = snap.documents
                        if (paginatedIds.isEmpty()) {
                            lastVisibleDoc = snap.documents.lastOrNull()
                            hasMore = snap.size() >= 20
                        }
                        publishVisiblePosts()
                        snap.documentChanges.forEach { change ->
                            if (change.type == DocumentChange.Type.REMOVED) {
                                scope.launch { feedViewModel.deleteCachedPost(change.document.id) }
                            }
                        }
                    } else if (err != null) {
                        failedSources.add("public")
                        CrashReporter.recordNonFatal(TAG, err, "Feed public query failed")
                        isLoading = pendingSources.isNotEmpty() && posts.isEmpty()
                        isRefreshing = isRefreshing && pendingSources.isNotEmpty()
                        isError = true
                    }
                }

            privateGroups.forEachIndexed { index, privateAuthors ->
                registrations += db.collection("posts")
                    .whereIn("userId", privateAuthors)
                    .whereEqualTo("isPrivate", true)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(20)
                    .addSnapshotListener { snap, error ->
                        if (!active) return@addSnapshotListener
                        val source = "private_$index"
                        pendingSources.remove(source)
                        if (snap != null) {
                            failedSources.remove(source)
                            pages[source] = snap.documents
                            publishVisiblePosts()
                            snap.documentChanges.forEach { change ->
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    scope.launch { feedViewModel.deleteCachedPost(change.document.id) }
                                }
                            }
                        } else if (error != null) {
                            failedSources.add(source)
                            isLoading = pendingSources.isNotEmpty() && posts.isEmpty()
                            isRefreshing = isRefreshing && pendingSources.isNotEmpty()
                            isError = true
                        }
                    }
            }

            // Arranca el watchdog DESPUÉS de registrar los listeners: solo
            // dispara si ninguna snapshot llegó en el tiempo límite.
            initialLoadTimeout = scope.launch {
                delay(FEED_INITIAL_LOAD_TIMEOUT_MS)
                if (pendingSources.isNotEmpty()) {
                    failedSources.addAll(pendingSources)
                    pendingSources.clear()
                    isLoading = false
                    isRefreshing = false
                    if (posts.isEmpty() && stalePosts.isNotEmpty()) posts = stalePosts
                    isError = true
                }
            }

            onDispose {
                active = false
                initialLoadTimeout?.cancel()
                registrations.forEach { it.remove() }
            }
        }
    }

    // ── Pagination ──
    val shouldLoadMore = remember(currentUserId) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value, paginationRetry, lastVisibleDoc, isLoading,
        currentUserId, retryKey, blockedUserIds, followingUserIds) {
        if (shouldLoadMore.value && !isLoading && !paginationFailed && hasMore && lastVisibleDoc != null) {
            isLoadingMore = true
            try {
                val result = kotlinx.coroutines.withTimeout(15_000) {
                    if (followingOnly) loadFollowingPage(currentUserId, lastVisibleDoc, likedPostIds, followingUserIds - blockedUserIds)
                    else loadMorePostsFromFirebase(currentUserId, lastVisibleDoc, likedPostIds, blockedUserIds, followingUserIds)
                }
                val existingIds = posts.mapTo(hashSetOf()) { it.id }
                paginatedIds = paginatedIds + result.posts.filterNot { it.id in existingIds }.map { it.id }
                posts = mergeFeedPages(posts, result.posts)
                lastVisibleDoc = result.lastDoc
                hasMore = result.hasMore
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                paginationFailed = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                paginationFailed = true
            } finally {
                isLoadingMore = false
            }
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
                .collection("followRequests").addSnapshotListener { snap, error -> if (error == null && snap != null) followRequestsCount = snap.size() }
            regFollowing = db.collection("users").document(currentUserId)
                .collection("following").addSnapshotListener { snap, error -> if (error == null && snap != null) followingUserIds = snap.documents.map { it.id }.toSet() }
            regPending = db.collection("users").document(currentUserId)
                .collection("sentFollowRequests").addSnapshotListener { snap, error -> if (error == null && snap != null) pendingFollowUserIds = snap.documents.map { it.id }.toSet() }
            regSaved = db.collection("users").document(currentUserId)
                .collection("savedPosts").addSnapshotListener { snap, error -> if (error == null && snap != null) savedPostIds = snap.documents.map { it.id }.toSet() }
        }
        onDispose {
            regRequests?.remove(); regFollowing?.remove(); regPending?.remove(); regSaved?.remove()
        }
    }

    // ── UI ──
    Scaffold(
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { VividWordmark() },
                actions = {
                    BadgedBox(
                        badge = { if (followRequestsCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text(if (followRequestsCount > 9) "9+" else followRequestsCount.toString(), color = MaterialTheme.colorScheme.onError) } },
                        modifier = Modifier.padding(end = VividSpace.xs)
                    ) {
                        IconButton(onClick = onOpenRequests) { Icon(Icons.Default.Notifications, stringResource(R.string.feed_requests), tint = if (followRequestsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    IconButton(onClick = onOpenMessages) { Icon(Icons.Default.Email, stringResource(R.string.feed_messages), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                scrollBehavior = scrollBehavior,
                // El Scaffold de navegación ya aplica safeDrawing (task 35):
                // la barra no re-consume los top insets o la status bar se
                // pagaría dos veces.
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (followingOnly) 1 else 0) {
                Tab(selected = !followingOnly, onClick = { onSelectFollowing(false) },
                    text = { Text(stringResource(R.string.feed_for_you_tab)) })
                Tab(selected = followingOnly, onClick = { onSelectFollowing(true) },
                    text = { Text(stringResource(R.string.feed_following_tab)) })
            }
            VividOfflineBannerHost()
            if (discovery.hiddenPosts.isNotEmpty() || discovery.mutedAuthors.isNotEmpty()) {
                TextButton(onClick = { showResetDiscovery = true }) {
                    Text(stringResource(R.string.discovery_manage))
                }
            }

            VividPullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        likedPostIds = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                            feedViewModel.fetchLikedPostIds(currentUserId)
                        } ?: likedPostIds
                        retryKey++
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        horizontal = VividSpace.xs,
                        vertical = VividSpace.s
                    ),
                    verticalArrangement = Arrangement.spacedBy(VividSpace.s)
                ) {
                    item(key = "stories") {
                        StoriesTray(onStoryClick = { onOpenStoryViewer(it.id) }, onCreateStory = onCreateStory)
                    }

                    when {
                        isLoading && displayPosts.isEmpty() -> {
                            items(3, key = { "skeleton_$it" }) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = VividExpressiveShapes.MediumCard,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    FeedSkeleton()
                                }
                            }
                        }
                        isError && displayPosts.isEmpty() -> { item(key = "error") { FeedErrorState(onRetry = { retryKey++ }) } }
                        displayPosts.isEmpty() -> { item(key = "empty") {
                            if (followingOnly) Text(stringResource(R.string.feed_following_empty), modifier = Modifier.padding(24.dp))
                            else FeedEmptyState()
                            if (paginationFailed) FeedErrorState(onRetry = { paginationFailed = false; paginationRetry++ })
                        } }
                        else -> {
                            if (isError) {
                                item(key = "refresh_error") {
                                    TextButton(onClick = { retryKey++ }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.feed_refresh_failed))
                                    }
                                }
                            }
                            items(displayPosts, key = { it.id }) { post ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = VividExpressiveShapes.MediumCard,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {
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
                                        onNotInterested = { discoveryStore.hide(it.id) },
                                        onMuteAuthor = { discoveryStore.mute(it.userId) },
                                        onImageUrlExpired = onImageUrlExpired,
                                        onMusicUrlExpired = onMusicUrlExpired,
                                        onVideoUrlExpired = onVideoUrlExpired,
                                        onHashtagClick = onOpenHashtag
                                    )
                                }
                            }
                            if (paginationFailed) {
                                item(key = "pagination_error") {
                                    FeedErrorState(onRetry = { paginationFailed = false; paginationRetry++ })
                                }
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

    if (showResetDiscovery) AlertDialog(
        onDismissRequest = { showResetDiscovery = false },
        title = { Text(stringResource(R.string.discovery_manage)) },
        text = { Text(stringResource(R.string.discovery_reset_explanation)) },
        confirmButton = { TextButton(onClick = { discoveryStore.reset(); showResetDiscovery = false }) {
            Text(stringResource(R.string.discovery_reset))
        } },
        dismissButton = { TextButton(onClick = { showResetDiscovery = false }) { Text(stringResource(R.string.action_cancel)) } }
    )

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
        VividAlertDialog(
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
            dismissButton = { TextButton(onClick = { selectedPostForDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
            tone = VividDialogTone.Destructive
        )
    }

    // ── Report dialog ──
    if (showReportDialog) {
        VividAlertDialog(
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
    followingUserIds: Set<String> = emptySet()
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
                .map { doc ->
                    async { mapPostDoc(doc, currentUserId, likedPostIds, followingUserIds) }
                }
                .awaitAll().filterNotNull()
        }
        visiblePosts += mapped
    } while (visiblePosts.size < 20 && sourcePageWasFull)

    FeedPageResult(visiblePosts, cursor, sourcePageWasFull)
}

internal suspend fun mapPostDoc(
    doc: DocumentSnapshot,
    currentUserId: String,
    likedPostIds: Set<String>?,
    /** Ids de autores que el usuario sigue (audiencia "friends"). */
    followingUserIds: Set<String> = emptySet()
): PostData? {
    val data = doc.data ?: return null
    val paginationAuthorId = data["userId"] as? String ?: ""
    val paginationVisibility = data["visibility"] as? String ?: "public"
    if (paginationVisibility == "friends" &&
        paginationAuthorId != currentUserId &&
        paginationAuthorId !in followingUserIds
    ) return null
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
        altText = data["altText"] as? String ?: "",
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
        hashtags = (data["hashtags"] as? List<*>)?.mapNotNull { it as? String }?.map { it.lowercase() }.orEmpty(),
        visibility = paginationVisibility,
        musicTitle = data["musicTitle"] as? String ?: "",
        musicArtist = data["musicArtist"] as? String ?: "",
        musicAssetFile = data["musicAssetFile"] as? String ?: "",
        musicUrl = data["musicUrl"] as? String ?: "",
        musicStorageKey = data["musicStorageKey"] as? String ?: ""
    )
}

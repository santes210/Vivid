package com.vivid.app.presentation.profile

import com.vivid.app.presentation.report.ReportHelper
import com.vivid.app.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.util.CrashReporter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.vivid.app.ui.components.VividSnackbarHost

private const val TAG = "ProfileScreen"


// Data models moved to ProfileModels.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNavigateToChat: (chatId: String, receiverId: String, name: String) -> Unit = { _, _, _ -> },
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()
    val isOwnProfile = userId == currentUserId
    val db = FirebaseFirestore.getInstance()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var profile by remember(userId, currentUserId) { mutableStateOf(ProfileUiState(uid = userId, isCurrentUser = isOwnProfile)) }
    var photoPosts by remember(userId, currentUserId) { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var reelPosts by remember(userId, currentUserId) { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var savedCollections by remember(userId, currentUserId) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedCollection by remember(userId, currentUserId) { mutableStateOf<String?>(null) }
    var organizePost by remember(userId, currentUserId) { mutableStateOf<ProfilePost?>(null) }
    var showShareProfile by remember(userId) { mutableStateOf(false) }
    var savedPosts by remember(userId, currentUserId) { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var selectedPost by remember(userId, currentUserId) { mutableStateOf<ProfilePost?>(null) }
    var showPins by remember(userId) { mutableStateOf(false) }
    var showProfileMenu by remember(userId, currentUserId) { mutableStateOf(false) }
    var selectedTabIndex by remember(userId, currentUserId) { mutableIntStateOf(0) } // 0: Posts, 1: Reels, 2: Guardados

    // Skeleton: solo se muestran placeholders hasta que llega la primera snapshot real
    var isProfileLoaded by remember(userId, currentUserId) { mutableStateOf(false) }
    var postsLoaded by remember(userId, currentUserId) { mutableStateOf(false) }
    var reelsLoaded by remember(userId, currentUserId) { mutableStateOf(false) }
    var savedLoaded by remember(userId, currentUserId) { mutableStateOf(false) }

    // Error de carga (red / Firestore): muestra un estado con reintento en
    // lugar de un skeleton eterno o un perfil vacío engañoso.
    var loadFailed by remember(userId, currentUserId) { mutableStateOf(false) }
    var retryKey by remember(userId, currentUserId) { mutableIntStateOf(0) }

    val pinBusy by viewModel.pinBusy.collectAsState()
    val relationshipState by viewModel.relationshipState.collectAsState()
    val isFollowActionLoading by viewModel.isFollowActionLoading.collectAsState()
    val followActionError by viewModel.followActionError.collectAsState()
    val followActionMessage by viewModel.followActionMessage.collectAsState()

    var postsFailed by remember(userId, currentUserId) { mutableStateOf(false) }
    var savedFailed by remember(userId, currentUserId) { mutableStateOf(false) }
    var reelsFailed by remember(userId, currentUserId) { mutableStateOf(false) }
    val canViewContent = isProfileLoaded && !loadFailed &&
        (isOwnProfile || !profile.isPrivate || profile.isFollowedByCurrentUser)

    LaunchedEffect(userId) {
        if (!isOwnProfile) viewModel.checkFollowStatus(userId)
    }
    LaunchedEffect(followActionError) {
        followActionError?.let { snackbarHostState.showSnackbar(it); viewModel.clearFollowActionError() }
    }
    LaunchedEffect(followActionMessage) {
        followActionMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearFollowActionMessage() }
    }

    DisposableEffect(userId, currentUserId, retryKey, profile.isPrivate, profile.isFollowedByCurrentUser) {
        postsLoaded = false
        reelsLoaded = false
        postsFailed = false
        reelsFailed = false
        var active = true
        var savedGeneration = 0
        var previousSavedIds: Set<String>? = null
        val mediaJobs = mutableListOf<kotlinx.coroutines.Job>()
        var profileListener: ListenerRegistration? = null
        var followListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null
        var reelsListener: ListenerRegistration? = null
        var savedListener: ListenerRegistration? = null

        if (userId.isNotBlank()) {
            // ── Perfil ──
            profileListener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (!active) return@addSnapshotListener
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.profile")
                        isProfileLoaded = true
                        loadFailed = true
                        return@addSnapshotListener
                    }
                    loadFailed = false
                    if (snapshot?.exists() != true) {
                        isProfileLoaded = true
                        loadFailed = true
                        return@addSnapshotListener
                    }
                    val data = snapshot.data.orEmpty()
                    profile = ProfileUiState(
                        uid = userId,
                        pinnedContent = (data["pinnedContent"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                        username = data["username"] as? String ?: "vivid_user",
                        displayName = data["displayName"] as? String ?: "Usuario Vivid",
                        bio = data["bio"] as? String ?: "",
                        website = data["website"] as? String ?: "",
                        avatarUrl = data["avatarUrl"] as? String ?: "",
                        avatarBase64 = data["avatarBase64"] as? String ?: "",
                        followersCount = (data["followersCount"] as? Long)?.toInt() ?: 0,
                        followingCount = (data["followingCount"] as? Long)?.toInt() ?: 0,
                        isPrivate = data["isPrivate"] as? Boolean ?: false,
                        isFollowedByCurrentUser = profile.isFollowedByCurrentUser,
                        isCurrentUser = isOwnProfile,
                        isVerified = data["isVerified"] as? Boolean ?: false
                    )
                    isProfileLoaded = true
                }

            if (!isOwnProfile) {
                followListener = db.collection("users").document(userId)
                    .collection("followers").document(currentUserId)
                    .addSnapshotListener { snap, _ ->
                        if (!active) return@addSnapshotListener
                        val followed = snap?.exists() == true
                        profile = profile.copy(isFollowedByCurrentUser = followed)
                        db.collection("users").document(userId)
                            .collection("followRequests").document(currentUserId)
                            .get().addOnSuccessListener { reqSnap ->
                                if (!active) return@addOnSuccessListener
                                profile = profile.copy(isFollowRequestPending = reqSnap.exists())
                            }
                    }
            }

            // ── Posts y Reels del usuario ──
            postsListener = db.collection("posts")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (!active) return@addSnapshotListener
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.posts")
                        postsLoaded = true
                        postsFailed = true
                        photoPosts = emptyList()
                        return@addSnapshotListener
                    }
                    postsFailed = false
                    photoPosts = snapshot?.documents.orEmpty().map { doc ->
                        ProfilePost(
                            id = doc.id,
                            durationMs = doc.getLong("durationMs") ?: 0L,
                            altText = doc.getString("altText").orEmpty(),
                            visibility = doc.getString("visibility") ?: "public",
                            videoUrl = doc.getString("videoUrl").orEmpty(),
                            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                            isVideo = doc.getBoolean("isVideo") == true,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageBase64 = doc.getString("imageBase64").orEmpty(),
                            storageKey = doc.getString("storageKey").orEmpty(),
                            caption = doc.getString("caption").orEmpty(),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            username = doc.getString("username").orEmpty()
                        )
                    }.sortedByDescending { it.timestamp }
                    postsLoaded = true

                    snapshot?.documents.orEmpty().forEach { doc ->
                        val key = doc.getString("storageKey").orEmpty()
                        if (key.isNotBlank() && doc.getBoolean("isVideo") != true) {
                            mediaJobs += scope.launch {
                                viewModel.refreshSignedUrl(key)?.let { freshUrl ->
                                    if (!active) return@launch
                                    photoPosts = photoPosts.map {
                                        if (it.id == doc.id && it.imageUrl != freshUrl) it.copy(imageUrl = freshUrl) else it
                                    }
                                }
                            }
                        }
                    }
                }

            reelsListener = db.collection("reels")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (!active) return@addSnapshotListener
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "ProfileScreen.reels")
                        reelsLoaded = true
                        reelsFailed = true
                        reelPosts = emptyList()
                        return@addSnapshotListener
                    }
                    reelsFailed = false
                    reelPosts = snapshot?.documents.orEmpty().map { doc ->
                        val videoUrl = doc.getString("videoUrl").orEmpty()
                        ProfilePost(
                            id = doc.id,
                            durationMs = doc.getLong("durationMs") ?: 0L,
                            altText = doc.getString("altText").orEmpty(),
                            collection = "reels",
                            storageKey = doc.getString("storageKey").orEmpty(),
                            imageUrl = doc.getString("thumbnailUrl").orEmpty(),
                            videoUrl = videoUrl,
                            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                            isVideo = true,
                            caption = doc.getString("caption").orEmpty(),
                            timestamp = doc.getLong("timestamp") ?: 0L,
                            username = doc.getString("username").orEmpty()
                        )
                    }.sortedByDescending { it.timestamp }
                    reelsLoaded = true
                    snapshot?.documents.orEmpty().forEach { doc ->
                        val thumbnailKey = doc.getString("thumbnailStorageKey").orEmpty()
                        if (thumbnailKey.isNotBlank()) mediaJobs += scope.launch {
                            viewModel.refreshSignedUrl(thumbnailKey)?.let { url ->
                                if (active) reelPosts = reelPosts.map {
                                    if (it.id == doc.id) it.copy(thumbnailUrl = url, imageUrl = url) else it
                                }
                            }
                        }
                    }
                }

            // ── Pestaña Guardados (solo en el perfil propio) ──
            if (isOwnProfile) {
                savedListener = db.collection("users").document(userId)
                    .collection("savedPosts")
                    .addSnapshotListener { snap, error ->
                        if (!active) return@addSnapshotListener
                        val generation = ++savedGeneration
                        if (error != null) {
                            savedFailed = true
                            savedLoaded = true
                            savedPosts = emptyList()
                            return@addSnapshotListener
                        }
                        savedFailed = false
                        val savedIds = snap?.documents.orEmpty().map { it.id }
                        savedCollections = snap?.documents.orEmpty().associate { it.id to it.getString("collectionName").orEmpty() }
                        // Moving between collections changes only saved metadata, not the posts.
                        // Avoid re-reading every saved post for each organization action.
                        if (savedLoaded && savedIds.toSet() == previousSavedIds && savedPosts.size == savedIds.size) return@addSnapshotListener
                        previousSavedIds = savedIds.toSet()
                        if (savedIds.isEmpty()) {
                            savedPosts = emptyList()
                            savedLoaded = true
                        } else {
                            mediaJobs += scope.launch {
                                var anyFailed = false
                                val list = mutableListOf<ProfilePost>()
                                for (savedId in savedIds) {
                                    runCatching {
                                        val postDoc = db.collection("posts").document(savedId).get().await()
                                        if (postDoc.exists()) {
                                            list.add(
                                                ProfilePost(
                                                    id = postDoc.id,
                                                    durationMs = postDoc.getLong("durationMs") ?: 0L,
                                                    altText = postDoc.getString("altText").orEmpty(),
                                                    imageUrl = postDoc.getString("imageUrl").orEmpty(),
                                                    imageBase64 = postDoc.getString("imageBase64").orEmpty(),
                                                    storageKey = postDoc.getString("storageKey").orEmpty(),
                                                    videoUrl = postDoc.getString("videoUrl").orEmpty(),
                                                    thumbnailUrl = postDoc.getString("thumbnailUrl").orEmpty(),
                                                    isVideo = postDoc.getBoolean("isVideo") ?: false,
                                                    caption = postDoc.getString("caption").orEmpty(),
                                                    timestamp = postDoc.getLong("timestamp") ?: 0L,
                                                    username = postDoc.getString("username").orEmpty(),
                                                    isSaved = true
                                                )
                                            )
                                        }
                                    }.onFailure { error ->
                                        if (error is kotlinx.coroutines.CancellationException) throw error
                                        anyFailed = true
                                    }
                                }
                                if (!active || generation != savedGeneration) return@launch
                                savedFailed = anyFailed
                                savedPosts = list.sortedByDescending { it.timestamp }
                                savedLoaded = true
                            }
                        }
                    }
            }
        }

        // Bound every initial load, including saved-document fetches.
        mediaJobs += scope.launch {
            kotlinx.coroutines.delay(15_000)
            if (!active) return@launch
            if (!isProfileLoaded) { isProfileLoaded = true; loadFailed = true }
            if (!postsLoaded) { postsLoaded = true; postsFailed = true }
            if (!reelsLoaded) { reelsLoaded = true; reelsFailed = true }
            if (isOwnProfile && !savedLoaded) { savedLoaded = true; savedFailed = true }
        }
        onDispose {
            active = false
            mediaJobs.forEach { it.cancel() }
            profileListener?.remove()
            followListener?.remove()
            postsListener?.remove()
            reelsListener?.remove()
            savedListener?.remove()
        }
    }

    val visiblePosts = photoPosts.filter {
        it.visibility != com.vivid.app.domain.model.PostVisibility.FRIENDS.value ||
            isOwnProfile || profile.isFollowedByCurrentUser
    }
    val countedProfile = profile.copy(
        postsCount = profileContentCount(visiblePosts, postsLoaded, postsFailed, canViewContent),
        reelsCount = profileContentCount(reelPosts, reelsLoaded, reelsFailed, canViewContent)
    )
    val activeTabFailed = when (selectedTabIndex) {
        0 -> postsFailed
        1 -> reelsFailed
        else -> savedFailed
    }
    val collectionNames = savedCollections.values.filter { it.isNotBlank() }.distinct().sorted()
    val currentDisplayList = when (selectedTabIndex) {
        0 -> orderProfileContent(visiblePosts, profile.pinnedContent)
        1 -> orderProfileContent(reelPosts, profile.pinnedContent)
        2 -> savedPosts.filter { selectedCollection == null || savedCollections[it.id].orEmpty() == selectedCollection }
        else -> visiblePosts
    }

    val activeTabLoading = when (selectedTabIndex) {
        0 -> !postsLoaded
        1 -> !reelsLoaded
        2 -> !savedLoaded
        else -> false
    }

    Scaffold(
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            if (isOwnProfile) profile.displayName else "@${profile.username}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (profile.isPrivate && !isOwnProfile) {
                            Text(
                                stringResource(R.string.account_private),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isOwnProfile) {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        // Acciones secundarias agrupadas en un menú
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones de perfil")
                            }
                            DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.settings_title)) },
                                    onClick = {
                                        showProfileMenu = false
                                        onSettings()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_logout), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showProfileMenu = false
                                        com.vivid.app.util.PushNotificationHelper.unregisterToken()
                                        // Credential Manager cachea la cuenta usada: sin
                                        // limpiarla, el próximo login la reutilizaría.
                                        com.vivid.app.presentation.auth.GoogleCredentialSignIn
                                            .clearCredentialState(context)
                                        auth.signOut(); onLogout()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ExitToApp, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showProfileMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (relationshipState.isBlocked) "Desbloquear" else "Bloquear") },
                                    onClick = {
                                        if (relationshipState.isBlocked) viewModel.unblockUser(userId)
                                        else viewModel.blockUser(userId)
                                        showProfileMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Block, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Enviar mensaje") },
                                    onClick = {
                                        val chatId = com.vivid.app.domain.repository.ChatRepository.buildChatId(currentUserId, userId)
                                        onNavigateToChat(chatId, userId, profile.username)
                                        showProfileMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Email, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reportar usuario", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showProfileMenu = false
                                        val opened = ReportHelper.sendUserReport(
                                            context = context,
                                            userId = userId,
                                            username = profile.username,
                                            reason = "Contenido o comportamiento inapropiado"
                                        )
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (opened) "Redactando reporte en tu correo."
                                                else "No se encontró una app de correo instalada."
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                // El Scaffold de navegación ya aplica safeDrawing: no
                // re-consumir los top insets (doble padding de status bar).
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyVerticalGrid(
            // Adaptive en vez de Fixed(3): en una tablet o en horizontal,
            // tres columnas daban miniaturas de medio palmo. Con minSize el
            // grid crece a 5-7 columnas manteniendo el tamaño de la celda,
            // y en un teléfono estrecho sigue cayendo en 3.
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = VividSpace.s, vertical = VividSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Header del perfil (skeleton mientras carga) ──
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                if (isProfileLoaded && !loadFailed) {
                    ProfileHeader(
                        profile = countedProfile, isOwnProfile = isOwnProfile,
                        relationshipState = relationshipState,
                        isFollowActionLoading = isFollowActionLoading,
                        isVerified = profile.isVerified,
                        onToggleFollow = { viewModel.toggleFollow(userId) },
                        onEditProfile = onEditProfile
                    )
                } else if (!isProfileLoaded) {
                    ProfileHeaderSkeleton()
                }
            }

            if (isProfileLoaded && !loadFailed) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showShareProfile = true }) { Text(stringResource(R.string.profile_share_title)) }
                }
            }

            if (isOwnProfile && isProfileLoaded && profile.pinnedContent.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { showPins = true }) { Text(stringResource(R.string.profile_manage_pins)) }
                }
            }

            // ── Pestañas primarias M3 (Posts / Reels / Guardados) ──
            if (canViewContent) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    // Pestañas como grupo de selección única con ButtonGroup
                    // (M3 Expressive): son 3-4 FIJAS, justo su caso de uso, y se
                    // anima con el MotionScheme (el activo se ensancha y comprime
                    // a sus vecinos). Para una lista que crece no sirve y por eso
                    // los filtros de Explorar siguen siendo FilterChip en LazyRow.
                    ButtonGroup(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                        overflowIndicator = {}
                    ) {
                        toggleableItem(
                            weight = 1f,
                            checked = selectedTabIndex == 0,
                            onCheckedChange = { selectedTabIndex = 0 },
                            label = "Posts",
                            icon = { Icon(Icons.Default.GridView, contentDescription = "Posts") }
                        )
                        toggleableItem(
                            weight = 1f,
                            checked = selectedTabIndex == 1,
                            onCheckedChange = { selectedTabIndex = 1 },
                            label = "Reels",
                            icon = { Icon(Icons.Default.Movie, contentDescription = "Reels") }
                        )
                        if (isOwnProfile) {
                            toggleableItem(
                                weight = 1f,
                                checked = selectedTabIndex == 2,
                                onCheckedChange = { selectedTabIndex = 2 },
                                label = "Guardados",
                                icon = { Icon(Icons.Default.Bookmark, contentDescription = "Guardados") }
                            )
                        }
                    }
                }
            }

            if (isOwnProfile && selectedTabIndex == 2 && canViewContent) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = selectedCollection == null, onClick = { selectedCollection = null },
                            label = { Text(stringResource(R.string.saved_all)) }) }
                        item { FilterChip(selected = selectedCollection == "", onClick = { selectedCollection = "" },
                            label = { Text(stringResource(R.string.saved_unfiled)) }) }
                        items(collectionNames.size) { index ->
                            val name = collectionNames[index]
                            FilterChip(selected = selectedCollection == name, onClick = { selectedCollection = name }, label = { Text(name) })
                        }
                    }
                }
            }

            // ── Contenido de la pestaña activa ──
            if (isProfileLoaded && !loadFailed && !canViewContent) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    PrivateProfileLock(
                        username = profile.username,
                        hasPendingRequest = profile.isFollowRequestPending
                    )
                }
            } else if (loadFailed || activeTabFailed) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    VividErrorState(
                        title = stringResource(R.string.profile_load_failed),
                        onRetry = {
                            loadFailed = false
                            isProfileLoaded = false
                            postsFailed = false
                            reelsFailed = false
                            savedFailed = false
                            postsLoaded = false
                            reelsLoaded = false
                            savedLoaded = false
                            retryKey++
                        }
                    )
                }
            } else if (!isProfileLoaded || activeTabLoading) {
                items(9) { ProfileGridSkeletonCell() }
            } else if (selectedTabIndex == 2 && savedPosts.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    EmptySavedPostsPlaceholder()
                }
            } else if (currentDisplayList.isEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    if (selectedTabIndex == 2) Text(stringResource(R.string.saved_collection_empty), Modifier.padding(24.dp))
                    else EmptyPostsPlaceholder()
                }
            } else {
                items(currentDisplayList, key = { "${it.collection}/${it.id}" }) { post ->
                    ProfilePostThumbnail(post = post, onClick = { selectedPost = post },
                        onOrganize = if (isOwnProfile && selectedTabIndex == 2) ({ organizePost = post }) else null,
                        pinned = post.contentKey() in profile.pinnedContent,
                        pinEnabled = !pinBusy,
                        onTogglePin = if (isOwnProfile && selectedTabIndex != 2) ({
                            viewModel.togglePinned(post) { error ->
                                val message = when (error) {
                                    null -> R.string.profile_pins_updated
                                    "limit" -> R.string.profile_pin_limit
                                    else -> R.string.profile_pin_error
                                }
                                scope.launch { snackbarHostState.showSnackbar(context.getString(message)) }
                            }
                        }) else null)
                }
            }
        }
    }

    if (showShareProfile) ProfileShareDialog(userId, profile.username) { showShareProfile = false }
    organizePost?.let { post ->
        SavedCollectionDialog(initialName = savedCollections[post.id].orEmpty(),
            names = collectionNames,
            onDismiss = { organizePost = null },
            onSave = { name, completed -> viewModel.organizeSaved(post.id, name, completed) })
    }

    if (showPins) {
        AlertDialog(onDismissRequest = { showPins = false },
            title = { Text(stringResource(R.string.profile_manage_pins)) },
            text = {
                Column {
                    profile.pinnedContent.forEach { key ->
                        val item = (photoPosts + reelPosts).find { it.contentKey() == key }
                        TextButton(enabled = !pinBusy, onClick = {
                            viewModel.removePinned(key) { success ->
                                if (!success) scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profile_pin_error)) }
                            }
                        }) {
                            Text(stringResource(R.string.profile_unpin) + ": " +
                                (item?.caption?.takeIf { it.isNotBlank() } ?: key), maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPins = false }) { Text(stringResource(R.string.viewer_close)) } })
    }

    // ── Visor de post ──
    selectedPost?.let { post ->
        ProfilePostViewerDialog(
            post = post,
            onUnsave = {
                scope.launch {
                    runCatching {
                        db.collection("users").document(currentUserId)
                            .collection("savedPosts").document(post.id).delete().await()
                        savedPosts = savedPosts.filter { it.id != post.id }
                        snackbarHostState.showSnackbar("Publicación eliminada de guardados")
                    }.onFailure { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        snackbarHostState.showSnackbar("No se pudo eliminar de guardados. Inténtalo de nuevo.")
                    }
                }
                selectedPost = null
            },
            onDismiss = { selectedPost = null }
        )
    }
}

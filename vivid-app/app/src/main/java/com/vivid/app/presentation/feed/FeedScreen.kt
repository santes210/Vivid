package com.vivid.app.presentation.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vivid.app.presentation.stories.StoriesTray
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PostData(
    val id: String,
    val userId: String,
    val username: String,
    val userProfilePicture: String,
    val userProfilePictureBase64: String = "",
    val imageUrl: String = "",
    val imageBase64: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val caption: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val timestamp: Long,
    val isLiked: Boolean = false
)

data class PostComment(
    val id: String,
    val userId: String,
    val username: String,
    val text: String,
    val timestamp: Long,
    val avatarUrl: String = "",
    val avatarBase64: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onOpenMessages: () -> Unit,
    onOpenRequests: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenStoryViewer: (storyId: String) -> Unit = {}
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var followRequestsCount by remember { mutableIntStateOf(0) }
    var selectedPostForComments by remember { mutableStateOf<PostData?>(null) }
    var selectedPostViewerIndex by remember { mutableStateOf<Int?>(null) }
    var selectedPostForDetails by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForEdit by remember { mutableStateOf<PostData?>(null) }
    var selectedPostForDelete by remember { mutableStateOf<PostData?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        loadPostsFromFirebase(
            onSuccess = { loadedPosts ->
                posts = loadedPosts
                isLoading = false
            },
            onFallback = {
                posts = emptyList()
                isLoading = false
            }
        )
    }

    DisposableEffect(currentUserId) {
        var registration: ListenerRegistration? = null
        if (currentUserId.isNotBlank()) {
            registration = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("followRequests")
                .addSnapshotListener { snapshot, _ ->
                    followRequestsCount = snapshot?.size() ?: 0
                }
        }
        onDispose { registration?.remove() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Vivid", 
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    ) 
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (followRequestsCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error) { 
                                    Text(followRequestsCount.coerceAtMost(9).toString(), color = MaterialTheme.colorScheme.onError) 
                                }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = onOpenRequests) {
                            Icon(Icons.Default.Notifications, contentDescription = "Solicitudes", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = onOpenMessages) {
                        Icon(Icons.Default.Email, contentDescription = "Mensajes", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            StoriesTray(onStoryClick = { story -> onOpenStoryViewer(story.id) })

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay publicaciones aún",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        PostItem(
                            post = post,
                            currentUserId = currentUserId,
                            onOpenPost = { selectedPostViewerIndex = index },
                            onOpenComments = { selectedPostForComments = post },
                            onOpenDetails = { selectedPostForDetails = post },
                            onEditPost = { selectedPostForEdit = post },
                            onDeletePost = { selectedPostForDelete = post }
                        )
                    }
                }
            }
        }
    }

    selectedPostForComments?.let { post ->
        PostCommentsSheet(
            post = post,
            onDismiss = { selectedPostForComments = null }
        )
    }

    selectedPostViewerIndex?.let { initialIndex ->
        PostViewerDialog(
            posts = posts,
            initialIndex = initialIndex,
            onDismiss = { selectedPostViewerIndex = null }
        )
    }

    selectedPostForDetails?.let { post ->
        PostDetailsDialog(
            post = post,
            onDismiss = { selectedPostForDetails = null }
        )
    }

    selectedPostForEdit?.let { post ->
        EditPostDialog(
            post = post,
            onDismiss = { selectedPostForEdit = null },
            onSaved = { updatedCaption ->
                selectedPostForEdit = null
                posts = posts.map {
                    if (it.id == post.id) it.copy(caption = updatedCaption) else it
                }
                scope.launch {
                    snackbarHostState.showSnackbar("Publicación actualizada")
                }
            }
        )
    }

    selectedPostForDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedPostForDelete = null },
            title = { Text("Eliminar publicación", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("Esta acción borrará la publicación y sus comentarios de forma permanente.") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPostForDelete = null
                        scope.launch {
                            val success = deletePostFromFirebase(post)
                            if (success) {
                                posts = posts.filterNot { it.id == post.id }
                                snackbarHostState.showSnackbar("Publicación eliminada")
                            } else {
                                snackbarHostState.showSnackbar("No se pudo eliminar la publicación")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPostForDelete = null }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        )
    }
}

private fun loadPostsFromFirebase(
    onSuccess: (List<PostData>) -> Unit,
    onFallback: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    db.collection("posts")
        .orderBy("timestamp")
        .limit(50)
        .get()
        .addOnSuccessListener { postDocs ->
            val posts = postDocs.map { doc ->
                PostData(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "usuario",
                    userProfilePicture = doc.getString("userProfilePicture") ?: "",
                    userProfilePictureBase64 = doc.getString("userProfilePictureBase64") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    imageBase64 = doc.getString("imageBase64") ?: "",
                    caption = doc.getString("caption") ?: "",
                    likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
                    commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    isLiked = false
                )
            }

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val loadReels = {
                db.collection("reels")
                    .orderBy("timestamp")
                    .limit(50)
                    .get()
                    .addOnSuccessListener { reelDocs ->
                        val reels = reelDocs.mapNotNull { doc ->
                            val videoUrl = doc.getString("videoUrl").orEmpty()
                            if (videoUrl.isBlank()) return@mapNotNull null
                            PostData(
                                id = "reel_${doc.id}",
                                userId = doc.getString("userId") ?: "",
                                username = doc.getString("username") ?: "usuario",
                                userProfilePicture = doc.getString("userAvatar") ?: "",
                                videoUrl = videoUrl,
                                thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                                imageUrl = doc.getString("thumbnailUrl").orEmpty(),
                                isVideo = true,
                                caption = doc.getString("caption").orEmpty(),
                                likesCount = doc.getLong("likes")?.toInt() ?: 0,
                                commentsCount = doc.getLong("comments")?.toInt() ?: 0,
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                isLiked = false
                            )
                        }
                        onSuccess((posts + reels).sortedByDescending { it.timestamp })
                    }
                    .addOnFailureListener {
                        onSuccess(posts.sortedByDescending { it.timestamp })
                    }
            }

            if (!com.vivid.app.util.SettingsManager.showReelsInFeed) {
                onSuccess(posts.sortedByDescending { it.timestamp })
            } else {
                loadReels()
            }
        }
        .addOnFailureListener {
            onFallback()
        }
}

private fun updateLikeInFirebase(postId: String, isLiked: Boolean) {
    val db = FirebaseFirestore.getInstance()
    val increment = if (isLiked) 1L else -1L
    if (postId.startsWith("reel_")) {
        db.collection("reels")
            .document(postId.removePrefix("reel_"))
            .update("likes", FieldValue.increment(increment), "updatedAt", System.currentTimeMillis())
    } else {
        db.collection("posts")
            .document(postId)
            .update("likesCount", FieldValue.increment(increment))
    }
}

private suspend fun deletePostFromFirebase(post: PostData): Boolean {
    return try {
        val db = FirebaseFirestore.getInstance()
        val postRef = db.collection("posts").document(post.id)
        val commentsSnapshot = postRef.collection("comments").get().await()
        val batch = db.batch()

        commentsSnapshot.documents.forEach { batch.delete(it.reference) }
        batch.delete(postRef)
        if (post.userId.isNotBlank()) {
            batch.set(
                db.collection("users").document(post.userId),
                mapOf(
                    "postsCount" to FieldValue.increment(-1),
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
        batch.commit().await()
        true
    } catch (_: Exception) {
        false
    }
}

private suspend fun updatePostCaptionInFirebase(postId: String, newCaption: String): Boolean {
    return try {
        FirebaseFirestore.getInstance()
            .collection("posts")
            .document(postId)
            .update(
                mapOf(
                    "caption" to newCaption,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
        true
    } catch (_: Exception) {
        false
    }
}

private fun formatPostDate(timestamp: Long): String {
    return runCatching {
        SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(timestamp))
    }.getOrDefault("")
}

@Composable
private fun PostDetailsDialog(
    post: PostData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detalles de la publicación", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Autor: @${post.username}", style = MaterialTheme.typography.titleMedium)
                Text("Fecha: ${formatPostDate(post.timestamp)}", style = MaterialTheme.typography.bodyMedium)
                Text("Likes: ${if (com.vivid.app.util.SettingsManager.hideLikesCount) "Ocultos" else post.likesCount.toString()}", style = MaterialTheme.typography.bodyMedium)
                Text("Comentarios: ${post.commentsCount}", style = MaterialTheme.typography.bodyMedium)
                if (post.caption.isNotBlank()) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text("Descripción:", style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.primary))
                    Text(post.caption, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PostViewerDialog(
    posts: List<PostData>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (posts.lastIndex).coerceAtLeast(0)),
        pageCount = { posts.size }
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val currentPost = posts.getOrNull(pagerState.currentPage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(currentPost?.username.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            currentPost?.timestamp?.let { formatPostDate(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    val post = posts[page]
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PostImage(
                            imageBase64 = post.imageBase64,
                            imageUrl = post.imageUrl,
                            username = post.username,
                            modifier = Modifier.fillMaxSize(),
                            useDefaultHeight = false
                        )
                    }
                }

                if (posts.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(posts.size) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }
                }

                currentPost?.takeIf { it.caption.isNotBlank() }?.let { post ->
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("@${post.username}", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(post.caption, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPostDialog(
    post: PostData,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var caption by remember(post.id) { mutableStateOf(post.caption) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Editar publicación", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descripción") },
                    maxLines = 5,
                    shape = RoundedCornerShape(16.dp)
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    scope.launch {
                        isSaving = true
                        val success = updatePostCaptionInFirebase(post.id, caption.trim())
                        if (success) {
                            onSaved(caption.trim())
                        } else {
                            errorMessage = "No se pudo actualizar la publicación"
                        }
                        isSaving = false
                    }
                }
            ) {
                Text(if (isSaving) "Guardando..." else "Guardar")
            }
        },
        dismissButton = {
            TextButton(enabled = !isSaving, onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PostItem(
    post: PostData,
    currentUserId: String,
    onOpenPost: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenDetails: () -> Unit,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit
) {
    var isLiked by remember { mutableStateOf(post.isLiked) }
    var likeCount by remember { mutableStateOf(post.likesCount) }
    var commentCount by remember { mutableStateOf(post.commentsCount) }
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PostAuthorAvatar(post = post)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.username, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        formatPostDate(post.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ver detalles", style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onOpenDetails()
                            }
                        )
                        if (post.userId == currentUserId) {
                            DropdownMenuItem(
                                text = { Text("Editar publicación", style = MaterialTheme.typography.bodyLarge) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    onEditPost()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar publicación", style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.error)) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeletePost()
                                }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onClick = { onOpenPost() },
                        onDoubleClick = {
                            if (!isLiked) {
                                isLiked = true
                                likeCount += 1
                                updateLikeInFirebase(post.id, true)
                            }
                        }
                    )
            ) {
                if (post.isVideo && post.videoUrl.isNotBlank()) {
                    FeedVideoPlayer(videoUrl = post.videoUrl, thumbnailUrl = post.thumbnailUrl)
                } else {
                    PostImage(
                        imageBase64 = post.imageBase64,
                        imageUrl = post.imageUrl,
                        username = post.username
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isLiked = !isLiked
                    likeCount += if (isLiked) 1 else -1
                    updateLikeInFirebase(post.id, isLiked)
                }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(if (com.vivid.app.util.SettingsManager.hideLikesCount) "—" else "$likeCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.width(24.dp))
                IconButton(onClick = {
                    commentCount = maxOf(commentCount, post.commentsCount)
                    onOpenComments()
                }) {
                    Icon(Icons.Default.Email, contentDescription = "Comentarios", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
                }
                Text("$commentCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                if (post.caption.isNotBlank()) {
                    Text(text = post.caption, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = if (commentCount > 0) "Ver los $commentCount comentarios" else "Sé la primera persona en comentar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenComments() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeedVideoPlayer(videoUrl: String, thumbnailUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )
        AssistChip(
            onClick = {},
            label = { Text("Reel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCommentsSheet(
    post: PostData,
    onDismiss: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUser = auth.currentUser
    val scope = rememberCoroutineScope()

    var comments by remember(post.id) { mutableStateOf<List<PostComment>>(emptyList()) }
    var commentText by remember(post.id) { mutableStateOf("") }
    var isSending by remember(post.id) { mutableStateOf(false) }
    var errorMessage by remember(post.id) { mutableStateOf<String?>(null) }

    DisposableEffect(post.id) {
        var registration: ListenerRegistration? = null
        registration = db.collection("posts")
            .document(post.id)
            .collection("comments")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    errorMessage = error.message ?: "No se pudieron cargar los comentarios."
                    return@addSnapshotListener
                }

                comments = snapshot?.documents.orEmpty().map { doc ->
                    PostComment(
                        id = doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        username = doc.getString("username") ?: "usuario",
                        text = doc.getString("text").orEmpty(),
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        avatarUrl = doc.getString("avatarUrl").orEmpty(),
                        avatarBase64 = doc.getString("avatarBase64").orEmpty()
                    )
                }
                errorMessage = null
            }

        onDispose { registration?.remove() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text("Comentarios", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (post.caption.isBlank()) "Publicación de @${post.username}" else post.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            when {
                errorMessage != null -> {
                    Text(
                        errorMessage ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                comments.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Todavía no hay comentarios. ¡Sé el primero!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            CommentRow(comment = comment)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un comentario...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    enabled = commentText.isNotBlank() && !isSending && currentUser != null,
                    onClick = {
                        val cleanComment = commentText.trim()
                        if (cleanComment.isBlank()) return@Button

                        scope.launch {
                            isSending = true
                            errorMessage = null
                            try {
                                val userDoc = db.collection("users")
                                    .document(currentUser?.uid.orEmpty())
                                    .get()
                                    .await()

                                val username = userDoc.getString("username")
                                    ?: currentUser?.displayName
                                    ?: currentUser?.email?.substringBefore("@")
                                    ?: "usuario"

                                val avatarUrl = userDoc.getString("avatarUrl").orEmpty()
                                val avatarBase64 = userDoc.getString("avatarBase64").orEmpty()

                                db.collection("posts")
                                    .document(post.id)
                                    .collection("comments")
                                    .add(
                                        mapOf(
                                            "userId" to currentUser?.uid.orEmpty(),
                                            "username" to username,
                                            "text" to cleanComment,
                                            "avatarUrl" to avatarUrl,
                                            "avatarBase64" to avatarBase64,
                                            "timestamp" to System.currentTimeMillis()
                                        )
                                    )
                                    .await()

                                db.collection("posts")
                                    .document(post.id)
                                    .update("commentsCount", FieldValue.increment(1))
                                    .await()

                                commentText = ""
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "No se pudo enviar el comentario."
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(if (isSending) "..." else "Enviar", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PostAuthorAvatar(post: PostData) {
    if (post.userProfilePictureBase64.isNotBlank()) {
        var bitmap by remember(post.userProfilePictureBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(post.userProfilePictureBase64) {
            bitmap = try {
                val bytes = Base64.decode(post.userProfilePictureBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    if (post.userProfilePicture.isNotBlank()) {
        AsyncImage(
            model = post.userProfilePicture,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun CommentRow(comment: PostComment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        CommentAvatar(comment = comment)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(comment.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(2.dp))
            Text(com.vivid.app.util.SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CommentAvatar(comment: PostComment) {
    if (comment.avatarBase64.isNotBlank()) {
        var bitmap by remember(comment.avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(comment.avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(comment.avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = comment.username,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    if (comment.avatarUrl.isNotBlank()) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = comment.username,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                comment.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun PostImage(
    imageBase64: String,
    imageUrl: String,
    username: String,
    modifier: Modifier = Modifier,
    useDefaultHeight: Boolean = true
) {
    var bitmap by remember(imageBase64) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageBase64, imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageBase64, imageUrl) { mutableStateOf(false) }

    val urlPainter = rememberAsyncImagePainter(model = imageUrl)
    val urlState = urlPainter.state

    LaunchedEffect(imageBase64, imageUrl) {
        isLoading = true
        hasError = false

        if (imageBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(imageBase64, Base64.NO_WRAP)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                hasError = bitmap == null
            } catch (_: Exception) {
                bitmap = null
                hasError = true
            }
            isLoading = false
        } else {
            bitmap = null
            hasError = false
        }
    }

    LaunchedEffect(urlState, imageBase64) {
        if (imageBase64.isBlank() && imageUrl.isNotBlank()) {
            when (urlState) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Empty -> {
                    isLoading = true
                    hasError = false
                }
                is AsyncImagePainter.State.Success -> {
                    isLoading = false
                    hasError = false
                }
                is AsyncImagePainter.State.Error -> {
                    isLoading = false
                    hasError = true
                }
            }
        }
    }

    val imageAspectRatio = when {
        bitmap != null && bitmap!!.height > 0 -> bitmap!!.width.toFloat() / bitmap!!.height.toFloat()
        urlState is AsyncImagePainter.State.Success -> {
            val drawable = urlState.result.drawable
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
            } else {
                null
            }
        }
        else -> null
    }

    val containerModifier = when {
        imageAspectRatio != null -> modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
        useDefaultHeight -> modifier
            .fillMaxWidth()
            .height(380.dp)
        else -> modifier.fillMaxSize()
    }

    Box(
        modifier = containerModifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
            }
            hasError -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = "Error",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Post image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            imageUrl.isNotBlank() -> {
                Image(
                    painter = urlPainter,
                    contentDescription = "Post image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📷 $username",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

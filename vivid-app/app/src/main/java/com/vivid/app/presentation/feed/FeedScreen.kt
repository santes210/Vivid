package com.vivid.app.presentation.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PostData(
    val id: String, val userId: String, val username: String,
    val userProfilePicture: String, val userProfilePictureBase64: String = "",
    val imageUrl: String = "", val imageBase64: String = "",
    val videoUrl: String = "", val thumbnailUrl: String = "",
    val isVideo: Boolean = false, val caption: String,
    val likesCount: Int = 0, val commentsCount: Int = 0,
    val timestamp: Long, val isLiked: Boolean = false
)

data class PostComment(
    val id: String, val userId: String, val username: String,
    val text: String, val timestamp: Long,
    val avatarUrl: String = "", val avatarBase64: String = ""
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        isLoading = true
        loadPostsFromFirebase(
            onSuccess = { posts = it; isLoading = false },
            onFallback = { posts = emptyList(); isLoading = false }
        )
    }

    DisposableEffect(currentUserId) {
        var reg: ListenerRegistration? = null
        if (currentUserId.isNotBlank()) {
            reg = FirebaseFirestore.getInstance().collection("users").document(currentUserId)
                .collection("followRequests").addSnapshotListener { snap, _ -> followRequestsCount = snap?.size() ?: 0 }
        }
        onDispose { reg?.remove() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StoriesTray(onStoryClick = { onOpenStoryViewer(it.id) })
            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (posts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No hay publicaciones aún", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("¡Crea la primera!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        PostCard(
                            post = post, currentUserId = currentUserId,
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
                            FirebaseFirestore.getInstance().collection("posts").document(post.id).delete().await()
                            posts = posts.filter { it.id != post.id }
                            snackbarHostState.showSnackbar("Publicación eliminada")
                        } catch (e: Exception) { snackbarHostState.showSnackbar("Error: ${e.message}") }
                        selectedPostForDelete = null
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { selectedPostForDelete = null }) { Text("Cancelar") } }
        )
    }
}

// ── PostCard (Material You 3 Card) ──
@Composable
private fun PostCard(
    post: PostData, currentUserId: String,
    onOpenPost: () -> Unit, onOpenComments: () -> Unit,
    onOpenDetails: () -> Unit, onEditPost: () -> Unit, onDeletePost: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
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
                if (post.userId == currentUserId) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Editar") }, onClick = { showMenu = false; onEditPost() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                            DropdownMenuItem(text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeletePost() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            }

            // ── Contenido multimedia ──
            Box(modifier = Modifier.fillMaxWidth().clickable { onOpenPost() }) {
                when {
                    post.isVideo && post.videoUrl.isNotBlank() -> PostVideoPlayer(videoUrl = post.videoUrl, thumbnailUrl = post.thumbnailUrl)
                    else -> PostImage(imageBase64 = post.imageBase64, imageUrl = post.imageUrl, username = post.username)
                }
            }

            // ── Acciones ──
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { /* toggle like via VM */ }) {
                    Icon(if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like",
                        tint = if (post.isLiked) Color.Red else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onOpenComments) { Icon(Icons.Default.ChatBubbleOutline, "Comentar") }
                IconButton(onClick = onOpenDetails) { Icon(Icons.Default.Info, "Detalles") }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { /* share */ }) { Icon(Icons.Default.Share, "Compartir") }
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

            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Helpers ──
@Composable
private fun PostVideoPlayer(videoUrl: String, thumbnailUrl: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var isReady by remember { mutableStateOf(false) }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(videoUrl)); prepare() }
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

private fun loadPostsFromFirebase(onSuccess: (List<PostData>) -> Unit, onFallback: () -> Unit) {
    FirebaseFirestore.getInstance().collection("posts")
        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(40).get()
        .addOnSuccessListener { snap ->
            onSuccess(snap.documents.mapNotNull { doc ->
                PostData(
                    id = doc.id, userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "usuario",
                    userProfilePicture = doc.getString("userAvatar") ?: "", caption = doc.getString("caption") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "", imageBase64 = doc.getString("imageBase64") ?: "",
                    videoUrl = doc.getString("videoUrl") ?: "", thumbnailUrl = doc.getString("thumbnailUrl") ?: "",
                    isVideo = doc.getBoolean("isVideo") ?: false,
                    likesCount = (doc.getLong("likesCount") ?: 0).toInt(),
                    commentsCount = (doc.getLong("commentsCount") ?: 0).toInt(),
                    timestamp = doc.getLong("timestamp") ?: 0L
                )
            })
        }.addOnFailureListener { onFallback() }
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

// ── PostImage ──
@Composable
fun PostImage(imageBase64: String, imageUrl: String, username: String, modifier: Modifier = Modifier, useDefaultHeight: Boolean = true) {
    var bmp by remember(imageBase64) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageBase64, imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageBase64, imageUrl) { mutableStateOf(false) }
    val urlPainter = rememberAsyncImagePainter(model = imageUrl)
    val urlState = urlPainter.state

    LaunchedEffect(imageBase64) {
        if (imageBase64.isNotBlank()) {
            try { bmp = BitmapFactory.decodeByteArray(Base64.decode(imageBase64, Base64.NO_WRAP), 0, Base64.decode(imageBase64, Base64.NO_WRAP).size); hasError = bmp == null } catch (_: Exception) { hasError = true }
            isLoading = false
        }
    }
    LaunchedEffect(urlState) {
        if (imageBase64.isBlank() && imageUrl.isNotBlank()) {
            when (urlState) {
                is AsyncImagePainter.State.Loading -> { isLoading = true; hasError = false }
                is AsyncImagePainter.State.Success -> { isLoading = false; hasError = false }
                is AsyncImagePainter.State.Error -> { isLoading = false; hasError = true }
                else -> {}
            }
        }
    }

    val containerModifier = if (useDefaultHeight) modifier.fillMaxWidth().heightIn(max = 500.dp) else modifier.fillMaxSize()
    Box(modifier = containerModifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            hasError -> Icon(Icons.Default.BrokenImage, "Error", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            bmp != null -> Image(bmp!!.asImageBitmap(), "Post", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            imageUrl.isNotBlank() -> Image(painter = urlPainter, "Post", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> Text("📷 $username", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── PostCommentsSheet (BottomSheet simplificado) ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostCommentsSheet(post: PostData, onDismiss: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var commentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(post.id) {
        db.collection("posts").document(post.id).collection("comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.mapNotNull { doc ->
                    PostComment(id = doc.id, userId = doc.getString("userId") ?: "", username = doc.getString("username") ?: "?", text = doc.getString("text") ?: "", timestamp = doc.getLong("timestamp") ?: 0L, avatarUrl = doc.getString("avatarUrl") ?: "", avatarBase64 = doc.getString("avatarBase64") ?: "")
                } ?: emptyList()
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comentarios", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (comments.isEmpty()) { Text("No hay comentarios aún.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(comments) { _, comment -> CommentRow(comment); Spacer(Modifier.height(10.dp)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText, onValueChange = { commentText = it }, placeholder = { Text("Escribe un comentario...") },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        if (commentText.isBlank()) return@FilledTonalButton
                        isSending = true; errorMsg = null
                        scope.launch {
                            try {
                                val userDoc = db.collection("users").document(currentUserId).get().await()
                                db.collection("posts").document(post.id).collection("comments").add(mapOf(
                                    "userId" to currentUserId, "username" to (userDoc.getString("username") ?: "yo"),
                                    "text" to commentText.trim(), "timestamp" to System.currentTimeMillis(),
                                    "avatarUrl" to (userDoc.getString("avatarUrl") ?: ""), "avatarBase64" to (userDoc.getString("avatarBase64") ?: "")
                                )).await()
                                db.collection("posts").document(post.id).update("commentsCount", FieldValue.increment(1)).await()
                                commentText = ""
                            } catch (e: Exception) { errorMsg = e.message }
                            isSending = false
                        }
                    }, enabled = !isSending, shape = RoundedCornerShape(20.dp)) {
                        Text(if (isSending) "..." else "Enviar", fontWeight = FontWeight.Bold)
                    }
                }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

@Composable
private fun CommentRow(comment: PostComment) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        CommentAvatar(comment)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(comment.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text(SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
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

// ── Diálogos placeholder (los originales se mantienen, aquí versiones reducidas) ──
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
                            val player = remember(post.videoUrl) { ExoPlayer.Builder(ctx).build().apply { setMediaItem(MediaItem.fromUri(post.videoUrl)); repeatMode = ExoPlayer.REPEAT_MODE_ALL; prepare(); playWhenReady = true } }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Detalles", fontWeight = FontWeight.Bold) },
        text = { Column { Text("👤 ${post.username}"); Text("❤️ ${post.likesCount} Me gusta"); Text("💬 ${post.commentsCount} Comentarios"); Text("🕐 ${formatTimestamp(post.timestamp)}") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
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

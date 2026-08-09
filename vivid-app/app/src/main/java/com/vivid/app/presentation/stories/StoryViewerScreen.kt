package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.vivid.app.domain.repository.ChatRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Viewer model for screen
private data class ViewerStory(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val avatarBase64: String,
    val mediaUrl: String,
    val mediaBase64: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val caption: String,
    val type: String,
    val expiresAt: Long
)

data class StoryViewer(
    val uid: String,
    val username: String,
    val avatarUrl: String,
    val avatarBase64: String,
    val viewedAt: Long
)

@UnstableApi
@Composable
fun StoryViewerRoute(
    initialStoryId: String,
    onClose: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var stories by remember { mutableStateOf<List<ViewerStory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentIndex by remember { mutableStateOf(0) }
    var showViewersSheet by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }
    var viewers by remember { mutableStateOf<List<StoryViewer>>(emptyList()) }
    var viewersCount by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(initialStoryId, currentUserId) {
        var registration: ListenerRegistration? = null
        // Limpieza con B2 si es posible (fix 2026-08-09)
        try {
            val storyVM = androidx.hilt.navigation.compose.hiltViewModel<CreateStoryViewModel>()
            if (currentUserId.isNotBlank()) storyVM.cleanExpiredStories(currentUserId)
        } catch (_: Exception) {
            scope.launch {
                if (currentUserId.isNotBlank()) {
                    runCatching { deleteExpiredStoriesForCurrentUser(db, currentUserId) }
                }
            }
        }
        registration = db.collection("stories")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty()
                scope.launch {
                    val visibleStories = buildVisibleStories(
                        firestore = db,
                        currentUserId = currentUserId,
                        storyDocs = docs
                    )
                    val docsById = docs.associateBy { it.id }
                    val mappedStories = visibleStories.map { story ->
                        val raw = docsById[story.id]
                        ViewerStory(
                            id = story.id,
                            userId = story.userId,
                            username = story.username,
                            avatarUrl = story.avatarUrl.ifBlank { raw?.getString("userAvatar").orEmpty() },
                            avatarBase64 = story.avatarBase64,
                            mediaUrl = story.mediaUrl.ifBlank { raw?.getString("thumbnailUrl").orEmpty() },
                            mediaBase64 = story.mediaBase64,
                            videoUrl = raw?.getString("videoUrl").orEmpty(),
                            thumbnailUrl = raw?.getString("thumbnailUrl").orEmpty(),
                            caption = story.caption,
                            type = raw?.getString("type") ?: if (raw?.getString("videoUrl").isNullOrBlank()) "photo" else "video",
                            expiresAt = story.expiresAt
                        )
                    }
                    stories = mappedStories
                    currentIndex = mappedStories.indexOfFirst { it.id == initialStoryId }
                        .takeIf { it >= 0 }
                        ?: currentIndex.coerceIn(0, (mappedStories.size - 1).coerceAtLeast(0))
                    isLoading = false
                }
            }
        onDispose { registration?.remove() }
    }

    val currentStory = stories.getOrNull(currentIndex)
    val isOwner = currentStory?.userId == currentUserId

    // Mark viewed & listen viewers when story changes
    LaunchedEffect(currentStory?.id, currentUserId) {
        val story = currentStory ?: return@LaunchedEffect
        if (story.userId != currentUserId && currentUserId.isNotBlank()) {
            // Record view
            runCatching {
                val viewerRef = db.collection("stories").document(story.id).collection("viewers").document(currentUserId)
                val userSnap = db.collection("users").document(currentUserId).get().await()
                val username = userSnap.getString("username") ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "usuario"
                val avatarUrl = userSnap.getString("avatarUrl").orEmpty()
                val avatarBase64 = userSnap.getString("avatarBase64").orEmpty()
                val exists = viewerRef.get().await().exists()
                viewerRef.set(mapOf(
                    "uid" to currentUserId,
                    "username" to username,
                    "avatarUrl" to avatarUrl,
                    "avatarBase64" to avatarBase64,
                    "viewedAt" to System.currentTimeMillis()
                )).await()
                if (!exists) {
                    db.collection("stories").document(story.id).update("viewersCount", FieldValue.increment(1)).await()
                }
            }
        }
    }

    // Listen viewers for current story (owner)
    DisposableEffect(currentStory?.id, isOwner) {
        var reg: ListenerRegistration? = null
        if (isOwner && currentStory != null) {
            reg = db.collection("stories").document(currentStory.id).collection("viewers")
                .orderBy("viewedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.mapNotNull { d ->
                        StoryViewer(
                            uid = d.getString("uid") ?: d.id,
                            username = d.getString("username") ?: "usuario",
                            avatarUrl = d.getString("avatarUrl").orEmpty(),
                            avatarBase64 = d.getString("avatarBase64").orEmpty(),
                            viewedAt = d.getLong("viewedAt") ?: 0L
                        )
                    } ?: emptyList()
                    viewers = list
                    viewersCount = list.size
                }
            // Also fetch count from doc field as fallback
            scope.launch {
                runCatching {
                    val snap = db.collection("stories").document(currentStory.id).get().await()
                    val c = snap.getLong("viewersCount")?.toInt() ?: 0
                    if (c > viewersCount) viewersCount = c
                }
            }
        } else {
            viewers = emptyList()
            viewersCount = 0
        }
        onDispose { reg?.remove() }
    }

    LaunchedEffect(currentStory?.id, currentStory?.type, stories.size) {
        val story = currentStory ?: return@LaunchedEffect
        if (story.type != "video") {
            delay(5_500)
            if (currentIndex >= stories.lastIndex) onClose() else currentIndex++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(stories, currentIndex) {
                detectTapGestures(
                    onTap = { offset ->
                        if (offset.x < size.width / 3f) {
                            currentIndex = (currentIndex - 1).coerceAtLeast(0)
                        } else if (offset.x > 2 * size.width / 3f) {
                            if (currentIndex >= stories.lastIndex) onClose() else currentIndex++
                        } else {
                            // center tap handled by overlay close, do nothing
                        }
                    }
                )
            }
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }

            currentStory == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay stories", color = Color.White)
            }

            else -> {
                when (currentStory.type) {
                    "video" -> VideoStoryPlayer(
                        story = currentStory,
                        onCompleted = {
                            if (currentIndex >= stories.lastIndex) onClose() else currentIndex++
                        }
                    )
                    else -> PhotoStoryView(currentStory)
                }

                // Top overlay
                StoryViewerOverlay(
                    stories = stories,
                    currentIndex = currentIndex,
                    story = currentStory,
                    viewersCount = viewersCount,
                    isOwner = isOwner,
                    onClose = onClose,
                    onViewersClick = { showViewersSheet = true }
                )

                // Bottom interaction bar — Material You 3
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)) {
                    if (isOwner) {
                        // Owner: viewers pill + caption
                        if (viewersCount > 0 || true) {
                            FilledTonalButton(
                                onClick = { showViewersSheet = true },
                                modifier = Modifier.align(Alignment.Center),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.14f), contentColor = Color.White),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (viewersCount == 0) "Sin vistas aún" else if (viewersCount == 1) "1 vista" else "$viewersCount vistas",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    } else {
                        // Viewer: reply bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.14f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Responder a ${currentStory.username}…", color = Color.White.copy(alpha = 0.7f)) },
                                    maxLines = 1,
                                    singleLine = true,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                                        unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = Color.White
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                )
                                Spacer(Modifier.width(8.dp))
                                FilledIconButton(
                                    onClick = {
                                        val txt = replyText.trim()
                                        if (txt.isBlank() || isSendingReply) return@FilledIconButton
                                        isSendingReply = true
                                        scope.launch {
                                            try {
                                                // Send as chat message to story owner
                                                val chatId = ChatRepository.buildChatId(currentUserId, currentStory.userId)
                                                val db2 = FirebaseFirestore.getInstance()
                                                // Ensure chat exists minimal
                                                val meName = db2.collection("users").document(currentUserId).get().await().getString("username") ?: "Usuario"
                                                // Use repository directly via Firestore write (avoid Hilt in this screen)
                                                val now = System.currentTimeMillis()
                                                val msgId = db2.collection("chats").document(chatId).collection("messages").document().id
                                                val otherName = currentStory.username
                                                // Create chat doc if needed (merge)
                                                db2.collection("chats").document(chatId).set(
                                                    mapOf(
                                                        "participants" to listOf(currentUserId, currentStory.userId),
                                                        "participantNames" to mapOf(currentUserId to meName, currentStory.userId to otherName),
                                                        "updatedAt" to now,
                                                        "lastMessage" to "↳ Respondió a tu story: $txt",
                                                        "lastMessageType" to "story_reply",
                                                        "lastSenderId" to currentUserId,
                                                        "lastTimestamp" to now,
                                                        "unreadCounts.${currentStory.userId}" to FieldValue.increment(1)
                                                    ),
                                                    com.google.firebase.firestore.SetOptions.merge()
                                                ).await()
                                                db2.collection("chats").document(chatId).collection("messages").document(msgId).set(
                                                    mapOf(
                                                        "text" to txt,
                                                        "senderId" to currentUserId,
                                                        "receiverId" to currentStory.userId,
                                                        "timestamp" to now,
                                                        "type" to "story_reply",
                                                        "isRead" to false,
                                                        "isDelivered" to false,
                                                        "replyToStoryId" to currentStory.id
                                                    )
                                                ).await()
                                                replyText = ""
                                                snackbarHostState.showSnackbar("Respuesta enviada ✓")
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(e.message ?: "No se pudo enviar")
                                            } finally {
                                                isSendingReply = false
                                            }
                                        }
                                    },
                                    enabled = replyText.isNotBlank() && !isSendingReply,
                                    modifier = Modifier.size(46.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White)
                                ) {
                                    if (isSendingReply) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Icon(Icons.Default.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
            }
        }
    }

    if (showViewersSheet) {
        ViewersBottomSheet(
            viewers = viewers,
            viewersCount = viewersCount,
            onDismiss = { showViewersSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewersBottomSheet(viewers: List<StoryViewer>, viewersCount: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Vistas", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (viewersCount == 0) "Aún nadie vio tu story" else if (viewersCount == 1) "1 persona vio tu story" else "$viewersCount personas vieron tu story",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (viewers.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Sin vistas aún", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text("Cuando alguien vea tu story aparecerá aquí", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(viewers, key = { it.uid }) { v ->
                        ViewersRow(v)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ViewersRow(v: StoryViewer) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {}.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Avatar
        if (v.avatarBase64.isNotBlank()) {
            val bmp = remember(v.avatarBase64) { decodeBase64Bitmap(v.avatarBase64) }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = v.username, modifier = Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                PlaceholderAvatar(v.username)
            }
        } else if (v.avatarUrl.isNotBlank()) {
            AsyncImage(model = v.avatarUrl, contentDescription = v.username, modifier = Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            PlaceholderAvatar(v.username)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(v.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(timeAgo(v.viewedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AssistChip(onClick = {}, label = { Text("Ver perfil") }, leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(14.dp)) })
    }
}

@Composable
private fun PlaceholderAvatar(username: String) {
    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(username.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

private fun timeAgo(ts: Long): String {
    if (ts <= 0) return "hace un momento"
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "hace segundos"
        d < 3_600_000 -> "hace ${d / 60_000}m"
        d < 86_400_000 -> "hace ${d / 3_600_000}h"
        else -> "hace ${d / 86_400_000}d"
    }
}

@Composable
private fun StoryViewerOverlay(
    stories: List<ViewerStory>,
    currentIndex: Int,
    story: ViewerStory,
    viewersCount: Int,
    isOwner: Boolean,
    onClose: () -> Unit,
    onViewersClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stories.forEachIndexed { idx, _ ->
                LinearProgressIndicator(
                    progress = { if (idx < currentIndex) 1f else if (idx == currentIndex) 0.55f else 0f },
                    modifier = Modifier.weight(1f).height(2.dp).clip(MaterialTheme.shapes.extraSmall),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoryHeaderAvatar(story)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(story.username, color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                if (isOwner && viewersCount > 0) {
                    Text("$viewersCount vistas", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            if (isOwner) {
                FilledTonalIconButton(onClick = onViewersClick, modifier = Modifier.size(36.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Vistas", modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White) }
        }
        if (story.caption.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.35f)) {
                Text(story.caption, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun StoryHeaderAvatar(story: ViewerStory) {
    if (story.avatarBase64.isNotBlank()) {
        val bitmap = remember(story.avatarBase64) { decodeBase64Bitmap(story.avatarBase64) }
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = story.username, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            return
        }
    }
    if (story.avatarUrl.isNotBlank()) {
        AsyncImage(model = story.avatarUrl, contentDescription = story.username, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(story.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@UnstableApi
@Composable
private fun VideoStoryPlayer(story: ViewerStory, onCompleted: () -> Unit) {
    val context = LocalContext.current
    val player = remember(story.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(story.videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onCompleted()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PhotoStoryView(story: ViewerStory) {
    val bitmap = remember(story.mediaBase64) { decodeBase64Bitmap(story.mediaBase64) }
    when {
        bitmap != null -> Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        story.mediaUrl.isNotBlank() -> AsyncImage(model = story.mediaUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        story.thumbnailUrl.isNotBlank() -> AsyncImage(model = story.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Story", color = Color.White) }
    }
}

private fun decodeBase64Bitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }
}

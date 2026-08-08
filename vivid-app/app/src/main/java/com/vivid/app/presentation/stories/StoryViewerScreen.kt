package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Modelo de vista para el viewer
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

    DisposableEffect(initialStoryId, currentUserId) {
        var registration: ListenerRegistration? = null
        scope.launch {
            if (currentUserId.isNotBlank()) {
                runCatching { deleteExpiredStoriesForCurrentUser(db, currentUserId) }
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
                            avatarUrl = story.avatarUrl.ifBlank {
                                raw?.getString("userAvatar").orEmpty()
                            },
                            avatarBase64 = story.avatarBase64,
                            mediaUrl = story.mediaUrl.ifBlank {
                                raw?.getString("thumbnailUrl").orEmpty()
                            },
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

    LaunchedEffect(currentStory?.id, currentStory?.type, stories.size) {
        val story = currentStory ?: return@LaunchedEffect
        if (story.type != "video") {
            delay(5_000)
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
                            onClose()
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

                StoryViewerOverlay(
                    stories = stories,
                    currentIndex = currentIndex,
                    story = currentStory,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
private fun StoryViewerOverlay(
    stories: List<ViewerStory>,
    currentIndex: Int,
    story: ViewerStory,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { idx, _ ->
                LinearProgressIndicator(
                    progress = { if (idx < currentIndex) 1f else if (idx == currentIndex) 0.55f else 0f },
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StoryHeaderAvatar(story)
            Spacer(Modifier.width(8.dp))
            Text(
                story.username,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }

        if (story.caption.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                story.caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun StoryHeaderAvatar(story: ViewerStory) {
    if (story.avatarBase64.isNotBlank()) {
        val bitmap = remember(story.avatarBase64) {
            decodeBase64Bitmap(story.avatarBase64)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = story.username,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    if (story.avatarUrl.isNotBlank()) {
        AsyncImage(
            model = story.avatarUrl,
            contentDescription = story.username,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                story.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoStoryPlayer(
    story: ViewerStory,
    onCompleted: () -> Unit
) {
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
                if (playbackState == Player.STATE_ENDED) {
                    onCompleted()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun PhotoStoryView(story: ViewerStory) {
    val bitmap = remember(story.mediaBase64) {
        decodeBase64Bitmap(story.mediaBase64)
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        story.mediaUrl.isNotBlank() -> AsyncImage(
            model = story.mediaUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        story.thumbnailUrl.isNotBlank() -> AsyncImage(
            model = story.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Story", color = Color.White)
        }
    }
}

private fun decodeBase64Bitmap(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

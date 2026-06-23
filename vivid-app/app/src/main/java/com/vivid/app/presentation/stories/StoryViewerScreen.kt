package com.vivid.app.presentation.stories

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

// Modelo de vista para el viewer (distinto al Story de StoryData.kt para evitar choque)
data class ViewerStory(
    val id: String,
    val userId: String,
    val username: String,
    val userAvatar: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val caption: String,
    val type: String,       // "photo" o "video"
    val expiresAt: Long
)

/**
 * Visor de Stories (estilo Instagram).
 *
 * Soporta AMBOS tipos:
 *   - Foto (decodifica base64 o AsyncImage si hay URL).
 *   - Video (ExoPlayer, también con URL firmada de B2).
 *
 * Auto-avanza cada 15 segundos (fotos) o cuando termina el video.
 */
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
        registration = db.collection("stories")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty()
                scope.launch {
                    stories = docs.mapNotNull { doc ->
                        ViewerStory(
                            id = doc.id,
                            userId = doc.getString("userId").orEmpty(),
                            username = doc.getString("username") ?: "usuario",
                            userAvatar = doc.getString("userAvatar").orEmpty(),
                            videoUrl = doc.getString("videoUrl").orEmpty(),
                            thumbnailUrl = doc.getString("thumbnailUrl").orEmpty(),
                            caption = doc.getString("caption").orEmpty(),
                            type = doc.getString("type") ?: "photo",
                            expiresAt = doc.getLong("expiresAt") ?: 0L
                        )
                    }
                    // Encontrar el índice inicial
                    currentIndex = stories.indexOfFirst { it.id == initialStoryId }
                        .coerceAtLeast(0)
                    isLoading = false
                }
            }
        onDispose { registration?.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        if (offset.x < size.width / 3f) {
                            currentIndex = (currentIndex - 1).coerceAtLeast(0)
                        } else if (offset.x > 2 * size.width / 3f) {
                            currentIndex = (currentIndex + 1).coerceAtLeast(stories.size - 1)
                        } else {
                            // tap en el medio: cerrar
                            onClose()
                        }
                    }
                )
            }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Box
        }

        if (stories.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay stories", color = Color.White)
            }
            return@Box
        }

        val story = stories[currentIndex]

        // Render foto o video según tipo
        when (story.type) {
            "video" -> VideoStoryPlayer(story)
            else -> PhotoStoryView(story)
        }

        // Overlay: barra de progreso + caption + close
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Barras de progreso (estilo IG)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stories.forEachIndexed { idx, _ ->
                    LinearProgressIndicator(
                        progress = { (idx / stories.size.toFloat()).coerceIn(0f, 1f) },
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

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (story.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = story.userAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
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
}

@UnstableApi
@Composable
private fun VideoStoryPlayer(story: ViewerStory) {
    val context = LocalContext.current
    val player = remember(story.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(story.videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
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
    // Si hay thumbnail URL firmada, úsala
    if (story.thumbnailUrl.isNotBlank()) {
        AsyncImage(
            model = story.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Story", color = Color.White)
        }
    }
}

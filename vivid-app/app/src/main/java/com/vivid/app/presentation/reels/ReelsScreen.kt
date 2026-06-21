package com.vivid.app.presentation.reels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class Reel(
    val id: String,
    val videoUrl: String,
    val username: String,
    val caption: String,
    val likes: Int,
    val userAvatar: String = ""
)

@Composable
fun ReelsScreen() {
    val db = FirebaseFirestore.getInstance()
    var reels by remember { mutableStateOf<List<Reel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        var registration: ListenerRegistration? = null
        registration = db.collection("reels")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, _ ->
                reels = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val videoUrl = doc.getString("videoUrl").orEmpty()
                    if (videoUrl.isBlank()) return@mapNotNull null
                    Reel(
                        id = doc.id,
                        videoUrl = videoUrl,
                        username = doc.getString("username") ?: "usuario",
                        caption = doc.getString("caption").orEmpty(),
                        likes = doc.getLong("likes")?.toInt() ?: 0,
                        userAvatar = doc.getString("userAvatar").orEmpty()
                    )
                }
                isLoading = false
            }
        onDispose { registration?.remove() }
    }

    val listState = rememberLazyListState()
    var currentPlayingIndex by remember { mutableStateOf(0) }

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        reels.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No hay reels todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(reels, key = { it.id }) { reel ->
                ReelItem(
                    reel = reel,
                    isPlaying = reels.indexOf(reel) == currentPlayingIndex,
                    onLike = { }
                )
            }
        }
    }
}

@Composable
fun ReelItem(reel: Reel, isPlaying: Boolean, onLike: () -> Unit) {
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(reel.likes) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(680.dp)
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = ExoPlayer.Builder(ctx).build().also { exoPlayer ->
                        val mediaItem = MediaItem.fromUri(reel.videoUrl)
                        exoPlayer.setMediaItem(mediaItem)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = isPlaying
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reel.userAvatar.isNotBlank()) {
                    AsyncImage(
                        model = reel.userAvatar,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(reel.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(reel.username, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(reel.caption, color = Color.White)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = {
                isLiked = !isLiked
                likeCount += if (isLiked) 1 else -1
                onLike()
            }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Red else Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text("$likeCount", color = Color.White)

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(onClick = { }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

data class Reel(
    val id: String,
    val videoUrl: String,
    val username: String,
    val caption: String,
    val likes: Int,
    val userAvatar: String = "https://picsum.photos/id/1011/48/48"
)

@Composable
fun ReelsScreen() {
    val reels = remember {
        listOf(
            Reel("1", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "ana_vivid", "Día increíble en la playa 🌊", 12400),
            Reel("2", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "carlos_vivid", "Mi setup de edición 🔥", 8900),
            Reel("3", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4", "lucia_vivid", "Aventura en la montaña 🏔️", 23100)
        )
    }

    val listState = rememberLazyListState()
    var currentPlayingIndex by remember { mutableStateOf(0) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(reels) { reel ->
            ReelItem(
                reel = reel,
                isPlaying = reels.indexOf(reel) == currentPlayingIndex,
                onLike = { /* Handle like */ }
            )
        }
    }
}

@Composable
fun ReelItem(reel: Reel, isPlaying: Boolean, onLike: () -> Unit) {
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(reel.likes) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(680.dp)
            .background(Color.Black)
    ) {
        // Video Player
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

        // Overlay UI
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = reel.userAvatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(reel.username, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(reel.caption, color = Color.White)
        }

        // Actions
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

            IconButton(onClick = { /* Comment */ }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Comment", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
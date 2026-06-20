package com.vivid.app.presentation.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vivid.app.presentation.stories.StoriesRow
import com.vivid.app.presentation.stories.demoStories

@Composable
fun FeedScreen(
    onOpenMessages: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val posts = remember {
        listOf(
            PostDemo("1", "ana_vivid", "https://picsum.photos/id/1011/600/600", "Hermoso atardecer 🌅", 1240, false),
            PostDemo("2", "carlos_vivid", "https://picsum.photos/id/1005/600/600", "Mi nuevo setup 💻", 890, true),
            PostDemo("3", "lucia_vivid", "https://picsum.photos/id/1016/600/600", "Viaje a la montaña 🏔️", 2100, false)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text("Vivid", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                IconButton(onClick = onOpenMessages) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Mensajes")
                }
            }
        )

        // Stories row
        StoriesRow(
            stories = demoStories,
            onStoryClick = { /* Open story viewer */ }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Feed posts
        LazyColumn {
            items(posts) { post ->
                PostItem(post = post)
            }
        }
    }
}



data class PostDemo(
    val id: String,
    val username: String,
    val imageUrl: String,
    val caption: String,
    val likes: Int,
    var isLiked: Boolean
)

@Composable
fun PostItem(post: PostDemo) {
    var isLiked by remember { mutableStateOf(post.isLiked) }
    var likeCount by remember { mutableStateOf(post.likes) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = "https://picsum.photos/id/${post.username.hashCode() % 40 + 10}/36/36",
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(post.username, style = MaterialTheme.typography.titleMedium)
        }

        // Image
        AsyncImage(
            model = post.imageUrl,
            contentDescription = "Post image",
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            contentScale = ContentScale.Crop
        )

        // Actions
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                isLiked = !isLiked
                likeCount += if (isLiked) 1 else -1
            }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Text("$likeCount", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(onClick = { /* Open comments */ }) {
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comment")
            }
        }

        // Caption
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = post.caption,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Ver todos los comentarios",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
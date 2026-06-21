package com.vivid.app.presentation.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class Story(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val mediaUrl: String = "",
    val hasUnseenStory: Boolean = true
)

@Composable
fun StoriesTray(onStoryClick: (Story) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(currentUserId) {
        var registration: ListenerRegistration? = null
        registration = db.collection("stories")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                stories = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val userId = doc.getString("userId").orEmpty()
                    if (userId.isBlank()) return@mapNotNull null
                    Story(
                        id = doc.id,
                        username = doc.getString("username") ?: "usuario",
                        avatarUrl = doc.getString("avatarUrl").orEmpty(),
                        mediaUrl = doc.getString("mediaUrl").orEmpty(),
                        hasUnseenStory = true
                    )
                }
                isLoading = false
            }
        onDispose { registration?.remove() }
    }

    if (isLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        StoriesRow(stories = stories, onStoryClick = onStoryClick)
    }
}

@Composable
fun StoriesRow(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit
) {
    if (stories.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "No hay stories todavía",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(stories, key = { it.id }) { story ->
            StoryItem(story = story, onClick = { onStoryClick(story) })
        }
    }
}

@Composable
fun StoryItem(story: Story, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            modifier = Modifier.size(68.dp),
            color = if (story.hasUnseenStory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ) {
            if (story.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = story.avatarUrl,
                    contentDescription = story.username,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .padding(3.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        story.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            story.username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

// Ya no hay stories demo. Se deja vacío para que las rutas antiguas sigan compilando.
val demoStories = emptyList<Story>()

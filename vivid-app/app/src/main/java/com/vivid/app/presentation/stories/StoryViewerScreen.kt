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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoryViewerRoute(
    initialStoryId: String,
    onClose: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(initialStoryId, currentUserId) {
        var registration: ListenerRegistration? = null
        registration = db.collection("stories")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .orderBy("expiresAt", Query.Direction.ASCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty()
                scope.launch {
                    stories = buildVisibleStories(
                        firestore = db,
                        currentUserId = currentUserId,
                        storyDocs = docs
                    )
                    isLoading = false
                }
            }
        onDispose { registration?.remove() }
    }

    when {
        isLoading -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        stories.isEmpty() -> LaunchedEffect(Unit) { onClose() }
        else -> {
            val initialIndex = stories.indexOfFirst { it.id == initialStoryId }
                .takeIf { it >= 0 }
                ?: 0
            key(initialStoryId, stories.size) {
                StoryViewerScreen(
                    stories = stories,
                    initialIndex = initialIndex,
                    onClose = onClose
                )
            }
        }
    }
}

@Composable
fun StoryViewerScreen(
    stories: List<Story>,
    initialIndex: Int = 0,
    onClose: () -> Unit
) {
    var currentIndex by remember(initialIndex) { mutableStateOf(initialIndex) }
    var progress by remember(currentIndex) { mutableStateOf(0f) }

    val currentStory = stories.getOrNull(currentIndex) ?: return

    LaunchedEffect(currentIndex, stories.size) {
        progress = 0f
        while (progress < 1f) {
            delay(50)
            progress += 0.02f
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(currentIndex, stories.size) {
                detectTapGestures(
                    onTap = { offset ->
                        if (offset.x < size.width / 2) {
                            if (currentIndex > 0) currentIndex--
                        } else {
                            if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
                        }
                    }
                )
            }
    ) {
        StoryMedia(story = currentStory)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                LinearProgressIndicator(
                    progress = {
                        when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoryAvatar(
                username = currentStory.username,
                avatarUrl = currentStory.avatarUrl,
                avatarBase64 = currentStory.avatarBase64,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(currentStory.username, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(16.dp)
        ) {
            if (currentStory.caption.isNotBlank()) {
                Text(currentStory.caption, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text("Story de @${currentStory.username}", color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun StoryMedia(story: Story) {
    if (story.mediaBase64.isNotBlank()) {
        var bitmap by remember(story.mediaBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(story.mediaBase64) {
            bitmap = try {
                val bytes = Base64.decode(story.mediaBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Story",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    AsyncImage(
        model = story.mediaUrl.ifBlank { story.avatarUrl },
        contentDescription = "Story",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}

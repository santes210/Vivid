package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

@Composable
fun StoriesTray(
    onStoryClick: (Story) -> Unit,
    onCreateStory: () -> Unit = {}
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()

    var stories by remember { mutableStateOf<List<Story>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // ViewModel para limpieza con borrado de B2 (fix 2026-08-09) - sin try-catch porque hiltViewModel es @Composable
    val storyViewModel: CreateStoryViewModel = androidx.hilt.navigation.compose.hiltViewModel()

    DisposableEffect(currentUserId) {
        var registration: ListenerRegistration? = null
        if (currentUserId.isNotBlank()) {
            // Limpieza con B2 (borra Firestore + archivos de B2)
            storyViewModel.cleanExpiredStories(currentUserId)
        }
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

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        StoriesRow(stories = stories, onStoryClick = onStoryClick, onCreateStory = onCreateStory)
    }
}

@Composable
fun StoriesRow(
    stories: List<Story>,
    onStoryClick: (Story) -> Unit,
    onCreateStory: () -> Unit = {}
) {
    val groups = remember(stories) { groupStoriesByUser(stories) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Acción principal "Tu historia"
        item(key = "create_story") {
            CreateStoryItem(onClick = onCreateStory)
        }
        items(groups, key = { it.userId }) { group ->
            StoryGroupItem(group = group, onClick = { onStoryClick(group.stories.first()) })
        }
    }
}

@Composable
private fun CreateStoryItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            // Anillo "crear" diferenciado (dashed look de "Tu historia")
            Canvas(modifier = Modifier.size(64.dp)) {
                val strokeWidth = 2.dp.toPx()
                drawCircle(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    radius = size.minDimension / 2 - strokeWidth,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Crear historia",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Tu historia",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StoryGroupItem(group: StoryGroup, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            StorySegmentsRing(
                segments = group.stories.size.coerceAtLeast(1),
                modifier = Modifier.size(68.dp)
            )
            StoryAvatar(
                username = group.username,
                avatarUrl = group.avatarUrl,
                avatarBase64 = group.avatarBase64,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            group.username,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun StorySegmentsRing(
    segments: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeftX = strokeWidth / 2
        val topLeftY = strokeWidth / 2
        val gap = 6f
        val segmentSweep = ((360f - gap * segments) / segments).coerceAtLeast(12f)

        for (index in 0 until segments) {
            val start = -90f + index * (segmentSweep + gap)
            drawArc(
                color = trackColor,
                startAngle = start,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = start,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftX, topLeftY),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun StoryAvatar(
    username: String,
    avatarUrl: String,
    avatarBase64: String,
    modifier: Modifier = Modifier
) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = username,
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = username,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

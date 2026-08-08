package com.vivid.app.presentation.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.presentation.feed.PostData
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onPostClick: (String) -> Unit = {},
    onProfileClick: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedTag by remember { mutableStateOf("vivid") }
    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val tags = remember { listOf("vivid", "arte", "musica", "viaje", "comida", "tecnologia", "moda", "deporte") }

    fun loadPosts(tag: String) {
        scope.launch {
            loading = true
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("posts")
                    .whereArrayContains("hashtags", tag)
                    .limit(20)
                    .get()
                    .await()
                posts = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val caption = doc.getString("caption") ?: ""
                    val username = doc.getString("username") ?: ""
                    val userId = doc.getString("userId") ?: ""
                    val imageUrl = doc.getString("imageUrl") ?: ""
                    val videoUrl = doc.getString("videoUrl") ?: ""
                    val isVideo = doc.getBoolean("isVideo") ?: false
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
                    val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
                    val storageKey = doc.getString("storageKey") ?: ""
                    PostData(
                        id = id,
                        userId = userId,
                        username = username,
                        userProfilePicture = "",
                        userProfilePictureBase64 = "",
                        imageUrl = imageUrl,
                        videoUrl = videoUrl,
                        isVideo = isVideo,
                        caption = caption,
                        likesCount = likesCount,
                        commentsCount = commentsCount,
                        timestamp = timestamp,
                        isLiked = false,
                        isSaved = false,
                        storageKey = storageKey
                    )
                }
            } catch (_: Exception) { posts = emptyList() }
            loading = false
        }
    }

    LaunchedEffect(selectedTag) { loadPosts(selectedTag) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explorar", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Hashtags chips Material You 3
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tags) { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = tag },
                        label = { Text("#${tag}") },
                        leadingIcon = if (selectedTag == tag) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) } } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay publicaciones para #$selectedTag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
                        Card(
                            modifier = Modifier.aspectRatio(1f).clickable { onPostClick(post.id) },
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            if (post.imageUrl.isNotBlank()) {
                                coil.compose.AsyncImage(
                                    model = post.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("#${post.caption.take(10)}", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

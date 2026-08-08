package com.vivid.app.presentation.feed

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import coil.compose.AsyncImage

@Composable
fun PostDetailScreen(postId: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    var post by remember { mutableStateOf<PostData?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(postId) {
        loading = true
        post = try {
            val doc = FirebaseFirestore.getInstance().collection("posts").document(postId).get().await()
            val p = doc.toObject(PostData::class.java) // This won't work directly because PostData isn't a Firestore model with default constructor
            // Instead, construct manually from fields
            val caption = doc.getString("caption") ?: ""
            val username = doc.getString("username") ?: ""
            val imageUrl = doc.getString("imageUrl") ?: ""
            val videoUrl = doc.getString("videoUrl") ?: ""
            val isVideo = doc.getBoolean("isVideo") ?: false
            val timestamp = doc.getLong("timestamp") ?: 0L
            val userId = doc.getString("userId") ?: ""
            val likesCount = doc.getLong("likesCount")?.toInt() ?: 0
            val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0
            val storageKey = doc.getString("storageKey") ?: ""
            PostData(
                id = postId,
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
        } catch (e: Exception) { null }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publicación") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                post == null -> Text("No se encontró la publicación")
                else -> Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("@${post!!.username}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(post!!.caption, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    if (post!!.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = post!!.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Enlace: vivid://post/${post!!.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

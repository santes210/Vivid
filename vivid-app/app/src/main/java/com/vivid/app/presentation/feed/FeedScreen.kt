package com.vivid.app.presentation.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.presentation.stories.StoriesRow
import com.vivid.app.presentation.stories.demoStories
import kotlinx.coroutines.launch

data class PostData(
    val id: String,
    val userId: String,
    val username: String,
    val userProfilePicture: String,
    val imageUrl: String = "",
    val imageBase64: String = "",
    val caption: String,
    val likesCount: Int = 0,
    val timestamp: Long,
    val isLiked: Boolean = false
)

@Composable
fun FeedScreen(
    onOpenMessages: () -> Unit,
    onOpenProfile: () -> Unit
) {
    var posts by remember { mutableStateOf<List<PostData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        loadPostsFromFirebase(
            onSuccess = { loadedPosts ->
                posts = loadedPosts
                isLoading = false
            },
            onFallback = {
                posts = getDemoPosts()
                isLoading = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Vivid", style = MaterialTheme.typography.headlineMedium) },
            actions = {
                IconButton(onClick = onOpenMessages) {
                    Icon(Icons.Default.Email, contentDescription = "Mensajes")
                }
            }
        )

        StoriesRow(stories = demoStories, onStoryClick = {})

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay publicaciones aún")
            }
        } else {
            LazyColumn {
                items(posts) { post ->
                    PostItem(post = post)
                }
            }
        }
    }
}

private fun loadPostsFromFirebase(
    onSuccess: (List<PostData>) -> Unit,
    onFallback: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    db.collection("posts")
        .orderBy("timestamp")
        .limit(50)
        .get()
        .addOnSuccessListener { documents ->
            val posts = documents.map { doc ->
                PostData(
                    id = doc.id,
                    userId = doc.getString("userId") ?: "",
                    username = doc.getString("username") ?: "usuario",
                    userProfilePicture = doc.getString("userProfilePicture") ?: "",
                    imageUrl = doc.getString("imageUrl") ?: "",
                    imageBase64 = doc.getString("imageBase64") ?: "",
                    caption = doc.getString("caption") ?: "",
                    likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                    isLiked = false
                )
            }
            onSuccess(posts.sortedByDescending { it.timestamp })
        }
        .addOnFailureListener {
            onFallback()
        }
}

private fun getDemoPosts(): List<PostData> {
    return listOf(
        PostData("1", "demo1", "ana_vivid", "", "", "", "Hermoso atardecer 🌅", 1240, System.currentTimeMillis() - 100000, false),
        PostData("2", "demo2", "carlos_vivid", "", "", "", "Mi nuevo setup 💻", 890, System.currentTimeMillis() - 200000, false),
        PostData("3", "demo3", "lucia_vivid", "", "", "", "Viaje a la montaña 🏔️", 2100, System.currentTimeMillis() - 300000, false)
    )
}

private fun updateLikeInFirebase(postId: String, isLiked: Boolean) {
    val db = FirebaseFirestore.getInstance()
    val postRef = db.collection("posts").document(postId)
    
    postRef.get().addOnSuccessListener { document ->
        if (document.exists()) {
            val currentLikes = document.getLong("likesCount")?.toInt() ?: 0
            val newLikes = if (isLiked) currentLikes + 1 else currentLikes - 1
            postRef.update("likesCount", newLikes)
        }
    }
}

@Composable
fun PostItem(post: PostData) {
    var isLiked by remember { mutableStateOf(post.isLiked) }
    var likeCount by remember { mutableStateOf(post.likesCount) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header del post
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (post.userProfilePicture.isNotBlank()) {
                AsyncImage(
                    model = post.userProfilePicture,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.username.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(post.username, style = MaterialTheme.typography.titleMedium)
        }

        // Imagen del post
        PostImage(
            imageBase64 = post.imageBase64,
            imageUrl = post.imageUrl,
            username = post.username
        )

        // Acciones
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                isLiked = !isLiked
                likeCount += if (isLiked) 1 else -1
                updateLikeInFirebase(post.id, isLiked)
            }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
            }
            Text("$likeCount", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = { }) {
                Icon(Icons.Default.Email, contentDescription = "Comment")
            }
        }

        // Caption
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(text = post.caption, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Ver todos los comentarios",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PostImage(
    imageBase64: String,
    imageUrl: String,
    username: String
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(imageBase64, imageUrl) {
        isLoading = true
        hasError = false
        
        if (imageBase64.isNotBlank()) {
            // Cargar desde Base64 comprimido
            try {
                val bytes = Base64.decode(imageBase64, Base64.NO_WRAP)
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                hasError = bitmap == null
            } catch (e: Exception) {
                hasError = true
            }
        } else if (imageUrl.isNotBlank()) {
            // Cargar desde URL (para compatibilidad con posts antiguos)
            bitmap = null
            hasError = false
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Error",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (bitmap != null) {
            // Mostrar imagen comprimida desde Base64
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Post image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (imageUrl.isNotBlank()) {
            // Fallback: cargar desde URL
            AsyncImage(
                model = imageUrl,
                contentDescription = "Post image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📷 ${username}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

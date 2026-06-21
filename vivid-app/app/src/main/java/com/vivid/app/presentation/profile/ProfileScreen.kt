package com.vivid.app.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
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
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.domain.repository.ChatRepository

data class ProfileUiState(
    val uid: String = "",
    val username: String = "vivid_user",
    val displayName: String = "Usuario Vivid",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val postsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val isPrivate: Boolean = false,
    val isFollowing: Boolean = false
)

data class ProfilePost(
    val id: String,
    val imageUrl: String = "",
    val imageBase64: String = ""
)

@Composable
fun ProfileScreen(
    userId: String,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNavigateToChat: (chatId: String, receiverId: String, name: String) -> Unit = { _, _, _ -> }
) {
    val auth = FirebaseAuth.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()
    val isOwnProfile = userId == currentUserId
    val db = FirebaseFirestore.getInstance()

    var profile by remember { mutableStateOf(ProfileUiState(uid = userId)) }
    var posts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(userId) {
        var profileListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null

        if (userId.isNotBlank()) {
            profileListener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.data.orEmpty()
                    profile = ProfileUiState(
                        uid = userId,
                        username = data["username"] as? String ?: "vivid_user",
                        displayName = data["displayName"] as? String ?: "Usuario Vivid",
                        avatarUrl = data["avatarUrl"] as? String ?: "",
                        avatarBase64 = data["avatarBase64"] as? String ?: "",
                        postsCount = (data["postsCount"] as? Long)?.toInt() ?: 0,
                        followersCount = (data["followersCount"] as? Long)?.toInt() ?: 0,
                        followingCount = (data["followingCount"] as? Long)?.toInt() ?: 0,
                        isPrivate = data["isPrivate"] as? Boolean ?: false,
                        isFollowing = false // Logic for following could be added here
                    )
                    isLoading = false
                }

            // Only load posts if profile is public OR it's own profile
            // (Simplification: In a real app we'd check follow status)
            postsListener = db.collection("posts")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, _ ->
                    val loadedPosts = snapshot?.documents.orEmpty().map { doc ->
                        ProfilePost(
                            id = doc.id,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageBase64 = doc.getString("imageBase64").orEmpty()
                        )
                    }
                    posts = loadedPosts
                }
        }

        onDispose {
            profileListener?.remove()
            postsListener?.remove()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isOwnProfile) "Mi Perfil" else "@${profile.username}") },
            navigationIcon = {
                if (!isOwnProfile) {
                    IconButton(onClick = onLogout) { // Using onLogout as generic back for other profile for now
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            },
            actions = {
                if (isOwnProfile) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                    IconButton(onClick = {
                        auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileAvatar(profile.displayName, profile.avatarUrl, profile.avatarBase64)

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = profile.displayName,
                style = MaterialTheme.typography.headlineSmall
            )
            Text("@${profile.username}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileStat(profile.postsCount.toString(), "Posts")
                ProfileStat(profile.followersCount.toString(), "Seguidores")
                ProfileStat(profile.followingCount.toString(), "Siguiendo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isOwnProfile) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Button(onClick = onEditProfile, modifier = Modifier.weight(1f)) {
                        Text("Editar perfil")
                    }
                    OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                        Text("Ajustes")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Button(onClick = { /* Follow logic */ }, modifier = Modifier.weight(1f)) {
                        Text(if (profile.isFollowing) "Siguiendo" else "Seguir")
                    }
                    
                    // Solo permitir mensajes si NO es privada O si ya lo sigues (Simplificado: Permitir si no es propia)
                    // El usuario pidió: "cuando entras a su perfil si es privado no puedes mandar mensajes"
                    if (!profile.isPrivate || profile.isFollowing) {
                        OutlinedButton(
                            onClick = {
                                val chatId = ChatRepository.buildChatId(currentUserId, userId)
                                onNavigateToChat(chatId, userId, profile.displayName)
                            }, 
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mensaje")
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        if (profile.isPrivate && !isOwnProfile && !profile.isFollowing) {
            PrivateAccountOverlay()
        } else {
            ProfilePostsGrid(posts)
        }
    }
}

@Composable
private fun PrivateAccountOverlay() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(16.dp))
        Text("Esta cuenta es privada", style = MaterialTheme.typography.titleMedium)
        Text(
            "Síguela para ver sus fotos y videos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ProfilePostsGrid(posts: List<ProfilePost>) {
    Text(
        "Publicaciones",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(16.dp)
    )

    if (posts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Aún no hay publicaciones.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(posts, key = { it.id }) { post ->
                ProfilePostThumbnail(post)
            }
        }
    }
}

@Composable
private fun ProfileAvatar(displayName: String, avatarUrl: String, avatarBase64: String) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            fallbackAvatar(displayName)
        }
    } else if (avatarUrl.isNotBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Foto de perfil",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        fallbackAvatar(displayName)
    }
}

@Composable
private fun fallbackAvatar(displayName: String) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ProfilePostThumbnail(post: ProfilePost) {
    var bitmap by remember(post.imageBase64) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(post.imageBase64) {
        bitmap = if (post.imageBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp),
            contentScale = ContentScale.Crop
        )
        post.imageUrl.isNotBlank() -> AsyncImage(
            model = post.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp),
            contentScale = ContentScale.Crop
        )
        else -> Box(
            modifier = Modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Vivid", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProfileStat(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

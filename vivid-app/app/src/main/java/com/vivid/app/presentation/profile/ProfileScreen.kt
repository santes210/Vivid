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
import androidx.compose.material.icons.filled.ExitToApp
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

data class ProfileUiState(
    val username: String = "vivid_user",
    val displayName: String = "Usuario Vivid",
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val postsCount: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0
)

data class ProfilePost(
    val id: String,
    val imageUrl: String = "",
    val imageBase64: String = ""
)

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {},
    onSettings: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val db = FirebaseFirestore.getInstance()
    val uid = user?.uid.orEmpty()

    var profile by remember {
        mutableStateOf(
            ProfileUiState(
                username = user?.email?.substringBefore("@") ?: "vivid_user",
                displayName = user?.displayName ?: user?.email?.substringBefore("@") ?: "Usuario Vivid",
                avatarUrl = user?.photoUrl?.toString().orEmpty()
            )
        )
    }
    var posts by remember { mutableStateOf<List<ProfilePost>>(emptyList()) }

    DisposableEffect(uid) {
        var profileListener: ListenerRegistration? = null
        var postsListener: ListenerRegistration? = null

        if (uid.isNotBlank()) {
            profileListener = db.collection("users").document(uid)
                .addSnapshotListener { snapshot, _ ->
                    val data = snapshot?.data.orEmpty()
                    profile = ProfileUiState(
                        username = data["username"] as? String ?: user?.email?.substringBefore("@") ?: "vivid_user",
                        displayName = data["displayName"] as? String ?: user?.displayName ?: "Usuario Vivid",
                        avatarUrl = data["avatarUrl"] as? String ?: user?.photoUrl?.toString().orEmpty(),
                        avatarBase64 = data["avatarBase64"] as? String ?: "",
                        postsCount = (data["postsCount"] as? Long)?.toInt() ?: (data["postsCount"] as? Int ?: 0),
                        followersCount = (data["followersCount"] as? Long)?.toInt() ?: (data["followersCount"] as? Int ?: 0),
                        followingCount = (data["followingCount"] as? Long)?.toInt() ?: (data["followingCount"] as? Int ?: 0)
                    )
                }

            postsListener = db.collection("posts")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    val loadedPosts = snapshot?.documents.orEmpty().map { doc ->
                        ProfilePost(
                            id = doc.id,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageBase64 = doc.getString("imageBase64").orEmpty()
                        )
                    }
                    posts = loadedPosts
                    if (profile.postsCount != loadedPosts.size) {
                        db.collection("users").document(uid).set(
                            mapOf("postsCount" to loadedPosts.size, "updatedAt" to System.currentTimeMillis()),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    }
                }
        }

        onDispose {
            profileListener?.remove()
            postsListener?.remove()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Perfil") },
            actions = {
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
        }

        HorizontalDivider()

        Text(
            "Publicaciones",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Aún no tienes publicaciones.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(posts, key = { it.id }) { post ->
                    ProfilePostThumbnail(post)
                }
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

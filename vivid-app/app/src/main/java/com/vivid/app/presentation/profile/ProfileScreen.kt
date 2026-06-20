package com.vivid.app.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onEditProfile: () -> Unit = {}
) {
    val user = FirebaseAuth.getInstance().currentUser

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Perfil") },
            actions = {
                IconButton(onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                }) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                }
            }
        )

        // Profile header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = "https://picsum.photos/id/1011/120/120",
                contentDescription = "Foto de perfil",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = user?.displayName ?: user?.email?.substringBefore("@") ?: "Usuario Vivid",
                style = MaterialTheme.typography.headlineSmall
            )
            Text("@${user?.email?.substringBefore("@") ?: "vivid_user"}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileStat("245", "Posts")
                ProfileStat("12.4k", "Seguidores")
                ProfileStat("890", "Siguiendo")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Editar perfil")
            }
        }

        Divider()

        // Grid de posts (demo)
        Text(
            "Publicaciones",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        val demoPosts = List(9) { "https://picsum.photos/id/${(it % 30) + 10}/300/300" }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp)
        ) {
            items(demoPosts) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp),
                    contentScale = ContentScale.Crop
                )
            }
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
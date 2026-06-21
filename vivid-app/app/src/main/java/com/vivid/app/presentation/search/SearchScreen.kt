package com.vivid.app.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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

data class SearchUser(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String,
    val isFollowing: Boolean = false
)

@Composable
fun SearchScreen(
    onUserClick: (SearchUser) -> Unit,
    onFollowClick: (SearchUser) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val demoUsers = listOf(
        SearchUser("u1", "ana_vivid", "Ana García", "https://picsum.photos/id/1009/64/64"),
        SearchUser("u2", "carlos_vivid", "Carlos López", "https://picsum.photos/id/1012/64/64"),
        SearchUser("u3", "lucia_vivid", "Lucía Martínez", "https://picsum.photos/id/1006/64/64"),
        SearchUser("u4", "diego_vivid", "Diego Ruiz", "https://picsum.photos/id/160/64/64"),
        SearchUser("u5", "maria_vivid", "María Fernández", "https://picsum.photos/id/1016/64/64")
    )

    LaunchedEffect(query) {
        if (query.length > 2) {
            isLoading = true
            // Simulate Firestore search
            kotlinx.coroutines.delay(300)
            users = demoUsers.filter {
                it.username.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
            }
            isLoading = false
        } else {
            users = emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar usuarios") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(users) { user ->
                    UserSearchItem(
                        user = user,
                        onClick = { onUserClick(user) },
                        onFollowClick = { onFollowClick(user) }
                    )
                }
            }
        }
    }
}

@Composable
fun UserSearchItem(
    user: SearchUser,
    onClick: () -> Unit,
    onFollowClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = onFollowClick,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (user.isFollowing) "Siguiendo" else "Seguir")
        }
    }
}
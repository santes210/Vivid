package com.vivid.app.presentation.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.presentation.common.BlockedUsersViewModel
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.util.CrashReporter
import com.vivid.app.util.toUserFacingMessage
import com.vivid.app.util.withNetworkTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

private const val TAG = "SearchScreen"

@androidx.compose.runtime.Immutable
data class SearchUser(
    val uid: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val isFollowing: Boolean = false
)

@Composable
fun SearchScreen(
    onUserClick: (SearchUser) -> Unit,
    onMessageClick: (SearchUser) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<SearchUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    val blockedUsersViewModel: BlockedUsersViewModel = hiltViewModel()
    val blockedUsersState by blockedUsersViewModel.state.collectAsState()
    val blockedUserIds = blockedUsersState.userIds

    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(query, currentUserId, blockedUserIds, blockedUsersState.isLoaded, retryKey) {
        val cleanQuery = query.trim().lowercase()
        if (!blockedUsersState.isLoaded || cleanQuery.length < 2 || currentUserId.isBlank()) {
            users = emptyList()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        delay(250)
        isLoading = true
        errorMessage = null
        try {
            val snapshot = withNetworkTimeout("search.users") {
                db.collection("users")
                    .orderBy("usernameLower")
                    .startAt(cleanQuery)
                    .endAt(cleanQuery + "\uf8ff")
                    .limit(25)
                    .get()
                    .await()
            }

            users = snapshot.documents.mapNotNull { doc ->
                val uid = doc.getString("uid") ?: doc.id
                if (uid == currentUserId || uid in blockedUserIds) return@mapNotNull null
                SearchUser(
                    uid = uid,
                    username = doc.getString("username") ?: "usuario",
                    displayName = doc.getString("displayName") ?: doc.getString("username") ?: "Usuario",
                    avatarUrl = doc.getString("avatarUrl").orEmpty(),
                    avatarBase64 = doc.getString("avatarBase64").orEmpty(),
                    isFollowing = false
                )
            }
        } catch (e: Exception) {
            CrashReporter.recordNonFatal(TAG, e, "Búsqueda de usuarios falló para '$cleanQuery'")
            users = emptyList()
            errorMessage = e.toUserFacingMessage("No se pudieron buscar usuarios.")
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VividOfflineBannerHost()

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar usuarios reales") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            singleLine = true
        )

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                VividErrorState(
                    message = errorMessage ?: "Error",
                    onRetry = { retryKey++ }
                )
            }
            query.trim().length < 2 -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Escribe al menos 2 letras para buscar personas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            users.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No encontré usuarios con ese nombre.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn {
                    items(users, key = { it.uid }) { user ->
                        UserSearchItem(
                            user = user,
                            onClick = { onUserClick(user) },
                            onMessageClick = { onMessageClick(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchItem(
    user: SearchUser,
    onClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarForSearch(user)

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Button(
            onClick = onMessageClick,
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Mensaje")
        }
    }
}

@Composable
private fun AvatarForSearch(user: SearchUser) {
    com.vivid.app.ui.components.UserAvatar(
        imageUrl = user.avatarUrl,
        name = user.displayName,
        size = 52.dp
    )
}

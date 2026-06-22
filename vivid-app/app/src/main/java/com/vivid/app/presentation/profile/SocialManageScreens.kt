package com.vivid.app.presentation.profile

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
import androidx.compose.material.icons.filled.ArrowBack
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
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.domain.repository.SocialUserPreview
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowRequestsScreen(onBack: () -> Unit) {
    val repository = remember { FollowRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var requests by remember { mutableStateOf<List<SocialUserPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        requests = repository.getIncomingFollowRequests()
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Solicitudes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            requests.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tienes solicitudes pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests, key = { it.uid }) { user ->
                    SocialUserCard(
                        user = user,
                        primaryActionLabel = "Aceptar",
                        secondaryActionLabel = "Rechazar",
                        onPrimaryAction = {
                            scope.launch {
                                repository.acceptFollowRequest(user.uid)
                                reload()
                                snackbarHostState.showSnackbar("Solicitud aceptada")
                            }
                        },
                        onSecondaryAction = {
                            scope.launch {
                                repository.rejectFollowRequest(user.uid)
                                reload()
                                snackbarHostState.showSnackbar("Solicitud rechazada")
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseFriendsScreen(onBack: () -> Unit) {
    val repository = remember { FollowRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var followingUsers by remember { mutableStateOf<List<SocialUserPreview>>(emptyList()) }
    var closeFriends by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        followingUsers = repository.getFollowingUsers()
        closeFriends = repository.getCloseFriends().map { it.uid }.toSet()
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mejores amigos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            followingUsers.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Sigue a alguien para agregarlo a Mejores amigos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(followingUsers, key = { it.uid }) { user ->
                    SocialUserCard(
                        user = user,
                        primaryActionLabel = if (closeFriends.contains(user.uid)) "Quitar" else "Agregar",
                        onPrimaryAction = {
                            scope.launch {
                                if (closeFriends.contains(user.uid)) {
                                    repository.removeCloseFriend(user.uid)
                                } else {
                                    repository.addCloseFriend(user.uid)
                                }
                                reload()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(onBack: () -> Unit) {
    val repository = remember { FollowRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance()) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var blockedUsers by remember { mutableStateOf<List<SocialUserPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reload() {
        isLoading = true
        blockedUsers = repository.getBlockedUsers()
        isLoading = false
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Bloqueados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            blockedUsers.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tienes cuentas bloqueadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(blockedUsers, key = { it.uid }) { user ->
                    SocialUserCard(
                        user = user,
                        primaryActionLabel = "Desbloquear",
                        onPrimaryAction = {
                            scope.launch {
                                repository.unblockUser(user.uid)
                                reload()
                                snackbarHostState.showSnackbar("Cuenta desbloqueada")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialUserCard(
    user: SocialUserPreview,
    primaryActionLabel: String,
    secondaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(user = user)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onPrimaryAction, modifier = Modifier.weight(1f)) {
                    Text(primaryActionLabel)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                        Text(secondaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialAvatar(user: SocialUserPreview) {
    if (user.avatarBase64.isNotBlank()) {
        var bitmap by remember(user.avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(user.avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(user.avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = user.displayName,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }

    if (user.avatarUrl.isNotBlank()) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.displayName,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

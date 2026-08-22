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
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowRepository
import com.vivid.app.domain.repository.SocialUserPreview
import kotlinx.coroutines.launch
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.VividSnackbarHost

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
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
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
                LoadingIndicator(polygons = VividMaterialShapes.LoadingSequence)
            }
            requests.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tienes solicitudes pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(VividSpace.s),
                verticalArrangement = Arrangement.spacedBy(VividSpace.s)
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
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
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
                LoadingIndicator(polygons = VividMaterialShapes.LoadingSequence)
            }
            followingUsers.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Sigue a alguien para agregarlo a Mejores amigos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(VividSpace.s),
                verticalArrangement = Arrangement.spacedBy(VividSpace.s)
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
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
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
                LoadingIndicator(polygons = VividMaterialShapes.LoadingSequence)
            }
            blockedUsers.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No tienes cuentas bloqueadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(VividSpace.s),
                verticalArrangement = Arrangement.spacedBy(VividSpace.s)
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(VividSpace.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(user = user)
                Spacer(Modifier.width(VividSpace.s))
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(VividSpace.s))
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
    com.vivid.app.ui.components.UserAvatar(
        imageUrl = user.avatarUrl,
        name = user.displayName,
        size = 56.dp
    )
}

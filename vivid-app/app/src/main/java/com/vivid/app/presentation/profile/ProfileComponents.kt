package com.vivid.app.presentation.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.theme.LocalVividAnimationsEnabled

@Composable
internal fun ProfileHeader(
    profile: ProfileUiState,
    isOwnProfile: Boolean,
    relationshipState: com.vivid.app.domain.repository.FollowRelationshipState,
    isFollowActionLoading: Boolean,
    onToggleFollow: () -> Unit,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar como elemento hero con anillo degradado ──
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatar(profile.displayName, profile.avatarUrl, profile.avatarBase64)
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Nombre y bio ──
        Text(
            profile.displayName,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            "@${profile.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (profile.bio.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // ── Badge privado ──
        if (profile.isPrivate) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(4.dp))
                    Text("Privada", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Estadísticas en un grupo coherente (contenedor tonal) ──
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileStat(profile.postsCount.toString(), "Publicaciones", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.reelsCount.toString(), "Reels", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.followersCount.toString(), "Seguidores", Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(34.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                ProfileStat(profile.followingCount.toString(), "Siguiendo", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Acción principal ──
        if (isOwnProfile) {
            FilledTonalButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Editar perfil")
            }
        } else {
            Button(
                onClick = onToggleFollow,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = !isFollowActionLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (relationshipState.isFollowing || relationshipState.hasPendingRequest)
                        MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primary,
                    contentColor = if (relationshipState.isFollowing || relationshipState.hasPendingRequest)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isFollowActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val text = when {
                        relationshipState.isBlocked -> "Bloqueado"
                        relationshipState.isFollowing -> "Siguiendo"
                        relationshipState.hasPendingRequest -> "Solicitado"
                        else -> "Seguir"
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/**
 * Skeleton del header mientras carga la primera snapshot de Firestore.
 * Pulsa suavemente cuando las animaciones están activadas.
 */
@Composable
internal fun ProfileHeaderSkeleton() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val alpha = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "profileSkeleton")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
        animatedAlpha
    } else {
        0.45f
    }
    val blockColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
        Box(Modifier.size(116.dp).clip(CircleShape).background(blockColor))
        Spacer(Modifier.height(16.dp))
        // Nombre
        Box(Modifier.width(190.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(blockColor))
        Spacer(Modifier.height(8.dp))
        // @usuario
        Box(Modifier.width(130.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(blockColor))
        Spacer(Modifier.height(20.dp))
        // Grupo de estadísticas
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    if (index > 0) {
                        Spacer(Modifier.width(1.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.width(48.dp).height(18.dp).clip(RoundedCornerShape(9.dp)).background(blockColor))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(70.dp).height(11.dp).clip(RoundedCornerShape(5.dp)).background(blockColor))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        // Botón de acción
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(blockColor))
    }
}

@Composable
internal fun ProfileGridSkeletonCell() {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
    )
}

@Composable
internal fun PrivateProfileLock(username: String, hasPendingRequest: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Esta cuenta es privada",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sigue a @$username para ver sus publicaciones y reels.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasPendingRequest) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Solicitud enviada",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
internal fun EmptyPostsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.GridView, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Aún no hay publicaciones.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EmptySavedPostsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Aún no has guardado publicaciones",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Toca el ícono de marcador en cualquier publicación del feed para guardarla aquí. Solo tú puedes ver tus elementos guardados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Componentes reutilizables ──

@Composable
internal fun ProfileAvatar(displayName: String, avatarUrl: String, avatarBase64: String, size: Dp = 100.dp) {
    if (avatarBase64.isNotBlank()) {
        var bitmap by remember(avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(avatarBase64) {
            bitmap = try { val bytes = Base64.decode(avatarBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Avatar",
                modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
            return
        }
    }
    if (avatarUrl.isNotBlank()) {
        AsyncImage(model = avatarUrl, contentDescription = "Avatar",
            modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "V",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
internal fun ProfilePostThumbnail(post: ProfilePost, onClick: () -> Unit) {
    var bitmap by remember(post.imageBase64) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(post.imageBase64) {
        bitmap = if (post.imageBase64.isNotBlank()) try { val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null } else null
    }
    Box(
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).clickable { onClick() }.background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            post.imageUrl.isNotBlank() -> AsyncImage(model = post.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        if (post.isVideo) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayArrow, "Reel", tint = Color.White, modifier = Modifier.padding(8.dp).size(24.dp))
            }
        }
    }
}

@Composable
internal fun ProfilePostViewerDialog(
    post: ProfilePost,
    currentUserId: String = "",
    onUnsave: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(post.username.ifBlank { "Publicación" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        if (post.timestamp > 0)
                            Text(java.text.SimpleDateFormat("dd MMM yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(post.timestamp)),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (post.isSaved) {
                            IconButton(onClick = onUnsave) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Eliminar de guardados", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        TextButton(onClick = onDismiss) { Text("Cerrar", fontWeight = FontWeight.Bold) }
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        post.isVideo && post.videoUrl.isNotBlank() -> {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val player = remember(post.videoUrl) {
                                ExoPlayer.Builder(ctx).build().apply {
                                    if (com.vivid.app.util.VideoCacheManager.isCacheable(post.videoUrl)) {
                                        setMediaSource(com.vivid.app.util.VideoCacheManager.buildCachedMediaSource(ctx, post.videoUrl))
                                    } else {
                                        setMediaItem(MediaItem.fromUri(post.videoUrl))
                                    }
                                    repeatMode = ExoPlayer.REPEAT_MODE_ALL; prepare(); playWhenReady = true
                                }
                            }
                            DisposableEffect(player) { onDispose { player.release() } }
                            AndroidView(factory = { ctx2 -> PlayerView(ctx2).apply { this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                        }
                        post.imageBase64.isNotBlank() -> {
                            val bmp = remember(post.imageBase64) { try { val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null } }
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                        post.imageUrl.isNotBlank() -> AsyncImage(model = post.imageUrl, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                if (post.caption.isNotBlank()) Text(post.caption, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ProfileStat(count: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


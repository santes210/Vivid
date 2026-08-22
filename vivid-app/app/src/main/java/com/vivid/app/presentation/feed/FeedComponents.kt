package com.vivid.app.presentation.feed

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.vivid.app.R
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.DoubleTapLikeBox
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.ui.components.VividLikeButton
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Skeleton loading state (M3) ──

@Composable
internal fun FeedSkeleton() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val alpha = if (animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
        infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.7f,
            animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "skeletonAlpha"
        ).value
    } else 0.5f

    Column(Modifier.fillMaxWidth().padding(VividSpace.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)))
            Spacer(Modifier.width(VividSpace.s))
            Column {
                Box(Modifier.width(120.dp).height(14.dp).clip(VividShapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)))
                Spacer(Modifier.height(VividSpace.xxs))
                Box(Modifier.width(80.dp).height(10.dp).clip(VividShapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)))
            }
        }
        Spacer(Modifier.height(VividSpace.s))
        Box(Modifier.fillMaxWidth().height(300.dp).clip(VividExpressiveShapes.MediumCard).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)))
        Spacer(Modifier.height(VividSpace.s))
        Box(Modifier.width(200.dp).height(14.dp).clip(VividShapes.extraSmall).background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)))
    }
}

// ── Error state ──

@Composable
internal fun FeedErrorState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(VividSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(VividSpace.m))
        Text(
            stringResource(R.string.feed_error_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(VividSpace.xs))
        Text(
            stringResource(R.string.feed_error_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(VividSpace.m))
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(VividSpace.xs))
            Text(stringResource(R.string.feed_error_retry))
        }
    }
}

// ── Empty state ──

@Composable
internal fun FeedEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(VividSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(VividSpace.m))
        Text(
            stringResource(R.string.feed_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(VividSpace.xs))
        Text(
            stringResource(R.string.feed_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── InlineFollowButton ──

@Composable
internal fun InlineFollowButton(
    isFollowing: Boolean,
    hasPendingRequest: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        isFollowing -> stringResource(R.string.follow_button_following)
        hasPendingRequest -> stringResource(R.string.follow_button_requested)
        else -> stringResource(R.string.follow_button_follow)
    }
    val containerColor = when {
        isFollowing -> MaterialTheme.colorScheme.surfaceContainerHighest
        hasPendingRequest -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isFollowing -> MaterialTheme.colorScheme.onSurface
        hasPendingRequest -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = 2.dp),
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp)
    ) { Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
}

// ── Post author avatar ──

@Composable
internal fun PostAuthorAvatar(
    post: PostData,
    onClick: (() -> Unit)? = null
) {
    UserAvatar(
        imageUrl = post.userProfilePicture,
        name = post.username,
        // El avatar es el ancla de la transición compartida hacia el perfil:
        // el mismo círculo crece hasta la cabecera en vez de cortar a negro.
        userId = post.userId,
        size = 44.dp,
        contentDescription = stringResource(R.string.avatar_description),
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier.clip(CircleShape).clickable(onClick = onClick)
        }
    )
}

// ── Post image ──

@Composable
fun PostImage(
    imageBase64: String,
    imageUrl: String,
    username: String,
    modifier: Modifier = Modifier,
    useDefaultHeight: Boolean = true,
    storageKey: String = "",
    onUrlExpired: () -> Unit = {}
) {
    val containerModifier = if (useDefaultHeight) modifier.fillMaxWidth().heightIn(max = 500.dp) else modifier.fillMaxSize()

    Box(
        modifier = containerModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Case 1: Base64 image (fallback when B2 fails)
            imageBase64.isNotBlank() -> {
                var bitmap by remember(imageBase64) { mutableStateOf<Bitmap?>(null) }
                var isLoading by remember(imageBase64) { mutableStateOf(true) }
                var hasError by remember(imageBase64) { mutableStateOf(false) }

                LaunchedEffect(imageBase64) {
                    isLoading = true
                    hasError = false
                    bitmap = withContext(Dispatchers.IO) {
                        try {
                            val bytes = try {
                                Base64.decode(imageBase64, Base64.NO_WRAP)
                            } catch (_: Exception) {
                                Base64.decode(imageBase64, Base64.DEFAULT)
                            }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) { null }
                    }
                    hasError = bitmap == null
                    isLoading = false
                }

                when {
                    isLoading -> LoadingIndicator()
                    hasError || bitmap == null -> Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = stringResource(R.string.feed_loading_image_error),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Post de $username",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // Case 2: Remote URL (B2 with signed URL)
            imageUrl.isNotBlank() -> {
                var hasNotifiedExpired by remember(storageKey, imageUrl) { mutableStateOf(false) }

                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Post de $username",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                        }
                    },
                    error = {
                        if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                            hasNotifiedExpired = true
                            onUrlExpired()
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = "Error",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(VividSpace.xxs))
                                Text(
                                    stringResource(R.string.feed_loading_image_error),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                                    TextButton(onClick = {
                                        hasNotifiedExpired = true
                                        onUrlExpired()
                                    }) { Text(stringResource(R.string.feed_retry)) }
                                }
                            }
                        }
                    },
                    onError = {
                        if (storageKey.isNotBlank() && !hasNotifiedExpired) {
                            hasNotifiedExpired = true
                            onUrlExpired()
                        }
                    }
                )
            }

            // Case 3: no image (avoid infinite spinner)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(VividSpace.xs))
                    Text(
                        username,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── PostCard ──

@Composable
internal fun PostCard(
    post: PostData,
    currentUserId: String,
    isFollowingAuthor: Boolean,
    hasPendingRequestToAuthor: Boolean,
    onOpenPost: (PostData) -> Unit,
    onOpenAuthorProfile: (String) -> Unit = {},
    onOpenComments: (PostData) -> Unit,
    onOpenDetails: (PostData) -> Unit,
    onEditPost: (PostData) -> Unit,
    onDeletePost: (PostData) -> Unit,
    onToggleFollow: (String) -> Unit,
    onToggleSave: (PostData) -> Unit,
    onToggleLike: (PostData) -> Unit,
    onShare: (PostData) -> Unit,
    onReportPost: (String, String, String) -> Unit = { _, _, _ -> },
    onImageUrlExpired: (PostData) -> Unit = {},
    onMusicUrlExpired: (PostData) -> Unit = {},
    onVideoUrlExpired: (PostData) -> Unit = {}
) {
    val haptics = rememberVividHaptics()

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(VividSpace.s),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostAuthorAvatar(post) { onOpenAuthorProfile(post.userId) }
            Spacer(Modifier.width(VividSpace.s))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenAuthorProfile(post.userId) }
            ) {
                Text(post.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(formatTimestamp(post.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (post.userId != currentUserId && currentUserId.isNotBlank()) {
                InlineFollowButton(
                    isFollowing = isFollowingAuthor,
                    hasPendingRequest = hasPendingRequestToAuthor,
                    onClick = {
                        haptics.toggle(!isFollowingAuthor)
                        onToggleFollow(post.userId)
                    }
                )
            }

            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = {
                        haptics.longPress()
                        showMenu = true
                    }
                ) { Icon(Icons.Default.MoreVert, stringResource(R.string.feed_more_options)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (post.userId == currentUserId) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.feed_edit)) }, onClick = { showMenu = false; onEditPost(post) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.feed_delete), color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDeletePost(post) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.feed_report_post), color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onReportPost(post.id, post.username, post.caption) },
                        leadingIcon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }

        // ── Media content ──
        // Doble toque = like (nunca deslike) + corazón; un toque abre el post.
        DoubleTapLikeBox(
            isLiked = post.isLiked,
            onLike = { onToggleLike(post) },
            onTap = { onOpenPost(post) },
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                post.isVideo && post.videoUrl.isNotBlank() -> PostVideoPlayer(
                    videoUrl = post.videoUrl,
                    thumbnailUrl = post.thumbnailUrl,
                    onUrlExpired = { onVideoUrlExpired(post) }
                )
                else -> PostImage(
                    imageBase64 = post.imageBase64,
                    imageUrl = post.imageUrl,
                    username = post.username,
                    storageKey = post.storageKey,
                    onUrlExpired = { onImageUrlExpired(post) }
                )
            }
        }

        // ── Optional music (Material You 3) ──
        if (post.musicTitle.isNotBlank() || post.musicUrl.isNotBlank() || post.musicAssetFile.isNotBlank()) {
            PostMusicChip(post = post, onMusicUrlExpired = { onMusicUrlExpired(post) })
        }

        // ── Actions ──
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.xs, vertical = VividSpace.xxs), verticalAlignment = Alignment.CenterVertically) {
            VividLikeButton(
                isLiked = post.isLiked,
                onToggle = { onToggleLike(post) }
            )
            IconButton(onClick = { onOpenComments(post) }) { Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.feed_comment)) }
            IconButton(onClick = { onOpenDetails(post) }) { Icon(Icons.Default.Info, stringResource(R.string.feed_details)) }

            Spacer(Modifier.weight(1f))

            IconButton(
                onClick = {
                    haptics.toggle(!post.isSaved)
                    onToggleSave(post)
                }
            ) {
                Icon(
                    if (post.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    stringResource(R.string.feed_save),
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = {
                    haptics.confirm()
                    onShare(post)
                }
            ) { Icon(Icons.Default.Share, stringResource(R.string.feed_share)) }
        }

        // ── Likes count ──
        if (post.likesCount > 0 && !SettingsManager.hideLikesCount) {
            Text(
                stringResource(R.string.feed_likes_count, post.likesCount),
                modifier = Modifier.padding(horizontal = VividSpace.m),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        // ── Caption ──
        if (post.caption.isNotBlank()) {
            Row(modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.xxs)) {
                Text(post.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(6.dp))
                Text(SettingsManager.filterOffensiveWords(post.caption), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }

        // ── Comments count ──
        if (post.commentsCount > 0) {
            TextButton(onClick = { onOpenComments(post) }, modifier = Modifier.padding(horizontal = VividSpace.xs)) {
                Text(
                    stringResource(R.string.feed_view_comments, post.commentsCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Respiro interno del card: M3 recomienda 8-12dp entre tarjetas,
        // el espacio externo lo da el LazyColumn (12dp). Aquí solo dejamos
        // un pequeño padding inferior para que el contenido no pegue al borde
        // redondeado del surfaceContainerLow.
        Spacer(Modifier.height(VividSpace.xs))
    }
}

// ── Utility ──

internal fun formatTimestamp(ts: Long): String {
    if (ts <= 0) return ""
    return try { SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ts)) } catch (_: Exception) { "" }
}

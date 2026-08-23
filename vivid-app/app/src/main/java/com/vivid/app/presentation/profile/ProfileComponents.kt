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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.ui.components.VividVerifiedBadge
import com.vivid.app.ui.components.pressMorphShape
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.ui.motion.vividSharedElement
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.SoraFamily
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividShapes

@Composable
internal fun ProfileHeader(
    profile: ProfileUiState,
    isOwnProfile: Boolean,
    relationshipState: com.vivid.app.domain.repository.FollowRelationshipState,
    isFollowActionLoading: Boolean,
    isVerified: Boolean = false,
    onToggleFollow: () -> Unit,
    onEditProfile: () -> Unit
) {
    val haptics = rememberVividHaptics()
    var showAvatarViewer by remember(profile.uid) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = VividSpace.m),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar como elemento hero con anillo de marca ──
        // El anillo usa los acentos de producto (armonizados con el color
        // dinámico); el círculo es el destino de la transición compartida que
        // arranca en el avatar del feed, del chat o del buscador.
        // Al mantenerlo pulsado la silueta se transforma (pressMorphShape),
        // el mismo lenguaje que el botón Crear y el FAB de Reels.
        val avatarInteractions = remember { MutableInteractionSource() }
        val avatarShape = pressMorphShape(
            interactionSource = avatarInteractions,
            resting = com.vivid.app.theme.VividMaterialShapes.AvatarResting,
            pressed = com.vivid.app.theme.VividMaterialShapes.AvatarActive
        )
        Box(
            modifier = Modifier
                .vividSharedElement(
                    key = VividSharedKeys.avatar(profile.uid),
                    zIndexInOverlay = 1f
                )
                .size(116.dp)
                .clip(avatarShape)
                .clickable(
                    interactionSource = avatarInteractions,
                    indication = null,
                    onClickLabel = "Ver foto de perfil"
                ) {
                    haptics.tick()
                    showAvatarViewer = true
                }
                .background(Brush.sweepGradient(LocalVividAccents.current.storyRing))
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                ProfileAvatar(profile.displayName, profile.avatarUrl, profile.avatarBase64)
            }
        }

        if (showAvatarViewer) {
            ProfileAvatarViewerDialog(
                profile = profile,
                onDismiss = { showAvatarViewer = false }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Nombre y bio ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                profile.displayName,
                // headlineMedium (no headlineLarge): en un teléfono pequeño
                // un nombre largo con headlineLarge se comía dos líneas y
                // empujaba stats y acción fuera de la primera pantalla.
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            // Badge de verificado con forma de gema: solo si la cuenta lo es.
            if (isVerified) {
                Spacer(Modifier.width(VividSpace.xs))
                VividVerifiedBadge(size = 20.dp)
            }
        }
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

        // ── Cuenta privada ──
        // AssistChip con candado: un label diminuto de 10sp no comunicaba que
        // el contenido está restringido; el chip es un elemento reconocible y
        // el texto explícito evita la duda de "privada… ¿para quién?".
        if (profile.isPrivate) {
            Spacer(Modifier.height(VividSpace.s))
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        "Esta cuenta es privada",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                shape = VividExpressiveShapes.ChipUnselected,
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = null
            )
        }

        Spacer(Modifier.height(VividSpace.m))

        // ── Estadísticas: una tarjeta por métrica ──
        // Antes eran Text sueltos dentro de una fila con divisores: nada
        // sugería que fueran tocables ni dónde acababa cada zona. Ahora cada
        // métrica es su propia Surface (SmallCard), con el número en Sora Bold
        // 20sp y la etiqueta en labelSmall onSurfaceVariant.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)
        ) {
            ProfileStat(profile.postsCount.toString(), "Posts", Modifier.weight(1f))
            ProfileStat(profile.reelsCount.toString(), "Reels", Modifier.weight(1f))
            ProfileStat(profile.followersCount.toString(), "Seguidores", Modifier.weight(1f))
            ProfileStat(profile.followingCount.toString(), "Siguiendo", Modifier.weight(1f))
        }

        Spacer(Modifier.height(VividSpace.m))

        // ── Acción principal ──
        if (isOwnProfile) {
            FilledTonalButton(
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = VividExpressiveShapes.SecondaryButton
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(VividSpace.xs))
                Text("Editar perfil")
            }
        } else {
            Button(
                onClick = {
                    haptics.toggle(!relationshipState.isFollowing)
                    onToggleFollow()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = !isFollowActionLoading,
                shape = VividExpressiveShapes.SecondaryButton,
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
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        polygons = com.vivid.app.theme.VividMaterialShapes.LoadingSequence
                    )
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
        Spacer(Modifier.height(VividSpace.m))
        // Nombre
        Box(Modifier.width(190.dp).height(22.dp).clip(VividShapes.extraSmall).background(blockColor))
        Spacer(Modifier.height(VividSpace.xs))
        // @usuario
        Box(Modifier.width(130.dp).height(14.dp).clip(VividShapes.extraSmall).background(blockColor))
        Spacer(Modifier.height(20.dp))
        // Grupo de estadísticas
        Surface(
            shape = VividExpressiveShapes.MediumCard,
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
                        Box(Modifier.width(48.dp).height(18.dp).clip(VividShapes.extraSmall).background(blockColor))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(70.dp).height(11.dp).clip(VividShapes.extraSmall).background(blockColor))
                    }
                }
            }
        }
        Spacer(Modifier.height(VividSpace.m))
        // Botón de acción
        Box(Modifier.fillMaxWidth().height(44.dp).clip(VividExpressiveShapes.SmallCard).background(blockColor))
    }
}

@Composable
internal fun ProfileGridSkeletonCell() {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(VividShapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f))
    )
}

@Composable
internal fun PrivateProfileLock(username: String, hasPendingRequest: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(VividSpace.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = VividExpressiveShapes.HeroCard
    ) {
        Column(
            modifier = Modifier.padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cookie9Sided + secondaryContainer: el candado "privada" también
            // usa el contenedor expresivo, en tono secondary para distinguirlo
            // de un estado vacío normal (no falta contenido, está bloqueado).
            Surface(
                shape = com.vivid.app.theme.VividMaterialShapes.EmptyStateContainer,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(112.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Esta cuenta es privada",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(VividSpace.xs))
            Text(
                "Sigue a @$username para ver sus publicaciones y reels.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (hasPendingRequest) {
                Spacer(Modifier.height(VividSpace.s))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = VividExpressiveShapes.Media
                ) {
                    Text(
                        "Solicitud enviada",
                        modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
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
        modifier = Modifier.fillMaxWidth().padding(VividSpace.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = VividExpressiveShapes.HeroCard
    ) {
        Column(
            modifier = Modifier.padding(VividSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cookie9Sided + surfaceContainerHigh + onSurfaceVariant: mismo
            // lenguaje expresivo que el resto de estados vacíos de la app.
            Surface(
                shape = com.vivid.app.theme.VividMaterialShapes.EmptyStateContainer,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(104.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(VividSpace.s))
            Text("Aún no hay publicaciones.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun EmptySavedPostsPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(VividSpace.l),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = VividExpressiveShapes.HeroCard
    ) {
        Column(
            modifier = Modifier.padding(VividSpace.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cookie9Sided + surfaceContainerHigh + onSurfaceVariant: estado
            // vacío expresivo en vez de un BookmarkBorder suelto.
            Surface(
                shape = com.vivid.app.theme.VividMaterialShapes.EmptyStateContainer,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(112.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(Modifier.height(VividSpace.m))
            Text(
                "Aún no has guardado publicaciones",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(VividSpace.xs))
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
    val haptics = rememberVividHaptics()
    LaunchedEffect(post.imageBase64) {
        bitmap = if (post.imageBase64.isNotBlank()) try { val bytes = Base64.decode(post.imageBase64, Base64.NO_WRAP); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) } catch (_: Exception) { null } else null
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            // La miniatura ES la imagen del detalle: crece hasta la pantalla
            // completa en vez de que el detalle aparezca de la nada.
            .vividSharedElement(VividSharedKeys.postImage(post.id))
            .clip(VividShapes.extraSmall)
            .clickable {
                haptics.tick()
                onClick()
            }
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            post.imageUrl.isNotBlank() -> AsyncImage(model = post.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else -> Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
        if (post.isVideo) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = CircleShape, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Default.PlayArrow, "Reel", tint = Color.White, modifier = Modifier.padding(VividSpace.xs).size(24.dp))
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
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = VividExpressiveShapes.MediumCard) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(VividSpace.s),
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

/**
 * Métrica del perfil como tarjeta propia.
 *
 * Número en Sora Bold 20sp (dominante, escala con la fuente del sistema) y
 * etiqueta en labelSmall onSurfaceVariant. Si se pasa [onClick] la tarjeta
 * se vuelve tocable con rol de botón; si no, se agrupa como un solo nodo de
 * accesibilidad ("1.893 Seguidores") en vez de leerse en dos trozos.
 */
@Composable
fun ProfileStat(
    count: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val haptics = rememberVividHaptics()
    val clickModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button) {
            haptics.tick()
            onClick()
        }
    } else {
        Modifier
    }
    Surface(
        shape = VividExpressiveShapes.SmallCard,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .clip(VividExpressiveShapes.SmallCard)
            .then(clickModifier)
            .semantics(mergeDescendants = true) { contentDescription = "$count $label" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = VividSpace.xxs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SoraFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Visor a pantalla completa del avatar (se abre al tocar el hero del perfil). */
@Composable
internal fun ProfileAvatarViewerDialog(profile: ProfileUiState, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = VividExpressiveShapes.HeroCard,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(VividSpace.l),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(
                    profile.displayName,
                    profile.avatarUrl,
                    profile.avatarBase64,
                    size = 240.dp
                )
                Spacer(Modifier.height(VividSpace.m))
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(VividSpace.xs))
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        }
    }
}


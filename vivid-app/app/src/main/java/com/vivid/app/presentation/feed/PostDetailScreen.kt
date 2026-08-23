package com.vivid.app.presentation.feed

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.R
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividLikeButton
import com.vivid.app.ui.haptics.rememberVividHaptics
import com.vivid.app.ui.motion.VividSharedKeys
import com.vivid.app.ui.motion.vividSharedElement
import kotlinx.coroutines.tasks.await

/**
 * Detalle de una publicación.
 *
 * Es el destino de la transición compartida que arranca en el grid de Explorar
 * (y en el de un perfil): la miniatura tocada crece hasta esta imagen en vez de
 * que la pantalla aparezca de golpe, así que el usuario nunca pierde de vista
 * qué publicación abrió.
 *
 * Las acciones viven en un `HorizontalFloatingToolbar` de M3 Expressive:
 * flotan sobre el contenido, con la acción principal (like) destacada en color
 * de acento, en vez de la barra de iconos plana de antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    feedViewModel: FeedViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val haptics = rememberVividHaptics()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    var post by remember { mutableStateOf<PostData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(postId, currentUserId) {
        loading = true
        val db = FirebaseFirestore.getInstance()
        post = runCatching {
            val doc = db.collection("posts").document(postId).get().await()
            if (!doc.exists()) return@runCatching null
            PostData(
                id = postId,
                userId = doc.getString("userId").orEmpty(),
                username = doc.getString("username").orEmpty(),
                userProfilePicture = doc.getString("userProfilePicture").orEmpty(),
                userProfilePictureBase64 = "",
                imageUrl = doc.getString("imageUrl").orEmpty(),
                videoUrl = doc.getString("videoUrl").orEmpty(),
                isVideo = doc.getBoolean("isVideo") ?: false,
                caption = doc.getString("caption").orEmpty(),
                likesCount = doc.getLong("likesCount")?.toInt() ?: 0,
                commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0,
                timestamp = doc.getLong("timestamp") ?: 0L,
                isLiked = false,
                isSaved = false,
                storageKey = doc.getString("storageKey").orEmpty()
            )
        }.getOrNull()
        likesCount = post?.likesCount ?: 0
        // Estado real del like de este usuario: posts/{id}/likes/{uid}.
        isLiked = currentUserId.isNotBlank() && runCatching {
            db.collection("posts").document(postId)
                .collection("likes").document(currentUserId)
                .get().await().exists()
        }.getOrDefault(false)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feed_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                // El Scaffold de navegación ya aplica safeDrawing: no
                // re-consumir los top insets (doble padding de status bar).
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator(
                        polygons = VividMaterialShapes.LoadingSequence
                    )
                }

                post == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.explore_error))
                }

                else -> {
                    val loaded = post!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (loaded.imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = loaded.imageUrl,
                                contentDescription = stringResource(R.string.cd_post_detail_image),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    // Misma clave que la miniatura del grid: es
                                    // literalmente la misma imagen, no otra.
                                    .vividSharedElement(VividSharedKeys.postImage(loaded.id))
                                    .clip(VividExpressiveShapes.MediaLarge),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "@${loaded.username}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.vividSharedElement(
                                    VividSharedKeys.username(loaded.userId)
                                )
                            )
                            if (loaded.caption.isNotBlank()) {
                                Text(
                                    loaded.caption,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = VividSpace.xs)
                                )
                            }
                            if (likesCount > 0) {
                                Text(
                                    stringResource(R.string.feed_likes_count, likesCount),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = VividSpace.s)
                                )
                            }
                            // Aire para que la barra flotante no tape el texto.
                            Spacer(Modifier.height(96.dp))
                        }
                    }

                    HorizontalFloatingToolbar(
                        expanded = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = VividSpace.l)
                    ) {
                        VividLikeButton(
                            isLiked = isLiked,
                            onToggle = {
                                val next = !isLiked
                                isLiked = next
                                likesCount = (likesCount + if (next) 1 else -1).coerceAtLeast(0)
                                feedViewModel.togglePostLike(
                                    postId = loaded.id,
                                    currentUserId = currentUserId,
                                    shouldLike = next,
                                    onFailure = {
                                        // Revertir el optimismo si Firestore rechaza.
                                        isLiked = !next
                                        likesCount = (likesCount + if (next) -1 else 1)
                                            .coerceAtLeast(0)
                                    }
                                )
                            }
                        )
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onOpenProfile(loaded.userId)
                            }
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = stringResource(
                                    R.string.post_detail_open_profile
                                )
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                haptics.confirm()
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "vivid://post/${loaded.id}"
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(share, null)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.feed_share)
                            )
                        }
                    }
                }
            }
        }
    }
}

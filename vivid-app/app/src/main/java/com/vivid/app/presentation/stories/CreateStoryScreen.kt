package com.vivid.app.presentation.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.delay

/**
 * Pantalla para crear un Story (foto o video).
 *
 * Diferencias vs Reels:
 *   - Stories son cortos (15s).
 *   - Siempre con watermark "Vivid ✦".
 *   - Auto-borrado a 24h (campo expiresAt en Firestore).
 *   - Más simple: solo caption breve o sticker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun CreateStoryScreen(
    navController: NavController,
    viewModel: CreateStoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaType by remember { mutableStateOf<MediaKind?>(null) }
    var caption by remember { mutableStateOf("") }

    // Recoger video grabado por la cámara
    val backStackEntry by navController.currentBackStackEntryAsState()
    val recordedPath by (
        backStackEntry?.savedStateHandle?.getStateFlow("recordedVideo", "")
            ?: remember { mutableStateOf("") }
        ).collectAsState()

    LaunchedEffect(recordedPath) {
        if (recordedPath.isNotBlank()) {
            mediaUri = Uri.parse(recordedPath)
            mediaType = MediaKind.VIDEO
            viewModel.reset()
            backStackEntry?.savedStateHandle?.remove<String>("recordedVideo")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = MediaKind.PHOTO
            viewModel.reset()
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = MediaKind.VIDEO
            viewModel.reset()
        }
    }

    LaunchedEffect(state) {
        if (state is CreateStoryUiState.Success) {
            delay(700)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Story", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Preview 9:16 (vertical IG-style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    mediaUri == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Elige foto o video para tu story",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Dura 24 horas",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    mediaType == MediaKind.VIDEO -> VideoPreview(mediaUri!!)
                    else -> androidx.compose.foundation.Image(
                        painter = coil.compose.rememberAsyncImagePainter(mediaUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }

                // Overlay de progreso
                (state as? CreateStoryUiState.Compressing)?.let {
                    ProgressOverlay("Comprimiendo… ${it.percent}%")
                }
                (state as? CreateStoryUiState.Watermarking)?.let {
                    ProgressOverlay("Marca de agua… ${it.percent}%")
                }
                (state as? CreateStoryUiState.Uploading)?.let {
                    ProgressOverlay("Subiendo… ${it.percent}%")
                }
                if (state is CreateStoryUiState.SavingMetadata) {
                    ProgressOverlay("Guardando…")
                }
                if (state is CreateStoryUiState.Success) {
                    ProgressOverlay("¡Publicado! ✓", success = true)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (mediaUri == null) {
                // Selección
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            videoLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Foto")
                    }
                }

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = { navController.navigate("camera_video") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Grabar video ahora")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        mediaUri = null
                        mediaType = null
                        viewModel.reset()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar")
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Texto") },
                placeholder = { Text("Escribe algo…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(20.dp))

            val isBusy = state !is CreateStoryUiState.Idle &&
                    state !is CreateStoryUiState.Error &&
                    state !is CreateStoryUiState.Success

            Button(
                onClick = {
                    mediaUri?.let { uri ->
                        when (mediaType) {
                            MediaKind.VIDEO -> viewModel.publishVideoStory(context, uri, caption)
                            MediaKind.PHOTO -> viewModel.publishPhotoStory(context, uri, caption)
                            null -> {}
                        }
                    }
                },
                enabled = mediaUri != null && !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Subiendo…")
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar Story")
                }
            }

            (state as? CreateStoryUiState.Error)?.let { err ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(err.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

private enum class MediaKind { PHOTO, VIDEO }

@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ProgressOverlay(label: String, success: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (success) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 4.dp,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

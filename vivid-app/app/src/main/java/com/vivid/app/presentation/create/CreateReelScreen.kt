package com.vivid.app.presentation.create

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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

/**
 * Pantalla "Crear Reel" con flujo Material You 3 completo:
 *
 *   1. Seleccionar video (galería o grabar)
 *   2. [OPCIONAL] Trim → VideoTrimmerScreen
 *   3. Caption
 *   4. Elegir si llevar watermark "Vivid ✦"
 *   5. Publicar → comprime → marca → miniatura → upload → metadata
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun CreateReelScreen(
    navController: NavController,
    viewModel: CreateReelViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var withWatermark by remember { mutableStateOf(true) }

    // Recoger el video grabado o trimeado
    val backStackEntry = navController.currentBackStackEntry
    val recordedFlow = backStackEntry?.savedStateHandle?.getStateFlow("recordedVideo", "")
    val recordedPathState = recordedFlow?.collectAsState(initial = "")
    val recordedPath = recordedPathState?.value ?: ""

    LaunchedEffect(recordedPath) {
        if (recordedPath.isNotBlank()) {
            selectedUri = Uri.parse(recordedPath)
            viewModel.reset()
            backStackEntry?.savedStateHandle?.remove<String>("recordedVideo")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            viewModel.reset()
        }
    }

    LaunchedEffect(state) {
        if (state is CreateReelUiState.Success) {
            delay(800)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear Reel", fontWeight = FontWeight.SemiBold) },
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

            // Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (selectedUri == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Elige o graba un video",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    VideoPreview(selectedUri!!)
                }

                (state as? CreateReelUiState.Compressing)?.let {
                    ProgressOverlay("Comprimiendo… ${it.percent}%")
                }
                (state as? CreateReelUiState.Watermarking)?.let {
                    ProgressOverlay("Marca de agua… ${it.percent}%")
                }
                (state as? CreateReelUiState.Uploading)?.let {
                    ProgressOverlay("Subiendo a B2… ${it.percent}%")
                }
                if (state is CreateReelUiState.SavingMetadata) {
                    ProgressOverlay("Guardando…")
                }
                if (state is CreateReelUiState.Success) {
                    ProgressOverlay("¡Publicado! ✓", success = true)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Botones de acción
            if (selectedUri == null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Galería")
                    }
                    Button(
                        onClick = { navController.navigate("camera_video") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Grabar")
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedUri = null
                            viewModel.reset()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                    FilledTonalButton(
                        onClick = {
                            // Pasa el URI a la pantalla de trim
                            backStackEntry?.savedStateHandle?.set(
                                "trimInputUri", selectedUri.toString()
                            )
                            navController.navigate("video_trimmer")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Trim")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Toggle watermark
            if (selectedUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Brush,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Marca de agua \"Vivid\"",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Tu logo en cada frame del video",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = withWatermark,
                            onCheckedChange = { withWatermark = it }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // Caption
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                placeholder = { Text("Escribe algo…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(16.dp))

            val isBusy = state !is CreateReelUiState.Idle &&
                    state !is CreateReelUiState.Error &&
                    state !is CreateReelUiState.Success

            Button(
                onClick = {
                    selectedUri?.let {
                        viewModel.publishReel(
                            context = context,
                            videoUri = it,
                            caption = caption,
                            withWatermark = withWatermark
                        )
                    }
                },
                enabled = selectedUri != null && !isBusy,
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
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar Reel")
                }
            }

            (state as? CreateReelUiState.Error)?.let { err ->
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

@UnstableApi
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

package com.vivid.app.presentation.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.delay

/**
 * Pantalla "Crear Reel" — Material You 3 + Música
 *
 *  1. Seleccionar video
 *  2. Trim opcional
 *  3. [NUEVO] Selector de música (AudioMixer)
 *  4. Watermark
 *  5. Caption
 *  6. Publicar
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
    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(-1L) }

    // Música
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedMusicUri by remember { mutableStateOf<Uri?>(null) }
    var musicVolume by remember { mutableFloatStateOf(1.0f) }
    var originalVolume by remember { mutableFloatStateOf(0.3f) }
    var showMusicSheet by remember { mutableStateOf(false) }

    val backStackEntry = navController.currentBackStackEntry
    val recordedFlow = backStackEntry?.savedStateHandle?.getStateFlow("recordedVideo", "")
    val recordedPathState = recordedFlow?.collectAsState(initial = "")
    val recordedPath = recordedPathState?.value ?: ""
    val trimStartFlow = backStackEntry?.savedStateHandle?.getStateFlow("trimStartMs", 0L)
    val trimEndFlow = backStackEntry?.savedStateHandle?.getStateFlow("trimEndMs", -1L)
    val trimStartState = trimStartFlow?.collectAsState(initial = 0L)
    val trimEndState = trimEndFlow?.collectAsState(initial = -1L)

    LaunchedEffect(recordedPath) {
        if (recordedPath.isNotBlank()) {
            selectedUri = Uri.parse(recordedPath)
            trimStartMs = 0L
            trimEndMs = -1L
            viewModel.reset()
            backStackEntry?.savedStateHandle?.remove<String>("recordedVideo")
        }
    }

    LaunchedEffect(trimStartState?.value, trimEndState?.value) {
        val newStart = trimStartState?.value ?: 0L
        val newEnd = trimEndState?.value ?: -1L
        if (newEnd > newStart && newEnd > 0L) {
            trimStartMs = newStart
            trimEndMs = newEnd
            backStackEntry?.savedStateHandle?.remove<Long>("trimStartMs")
            backStackEntry?.savedStateHandle?.remove<Long>("trimEndMs")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            trimStartMs = 0L
            trimEndMs = -1L
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
                .verticalScroll(rememberScrollState())
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
                        Icon(Icons.Default.MovieCreation, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Elige o graba un video", color = Color.White.copy(alpha = 0.7f))
                    }
                } else {
                    VideoPreview(selectedUri!!)
                }

                (state as? CreateReelUiState.Compressing)?.let { ProgressOverlay("Comprimiendo… ${it.percent}%") }
                (state as? CreateReelUiState.MixingAudio)?.let { ProgressOverlay("Mezclando música… ${it.percent}%") }
                (state as? CreateReelUiState.Watermarking)?.let { ProgressOverlay("Marca de agua… ${it.percent}%") }
                (state as? CreateReelUiState.Uploading)?.let { ProgressOverlay("Subiendo a B2… ${it.percent}%") }
                if (state is CreateReelUiState.SavingMetadata) ProgressOverlay("Guardando…")
                if (state is CreateReelUiState.Success) ProgressOverlay("¡Publicado! ✓", success = true)
            }

            Spacer(Modifier.height(16.dp))

            // Botones de acción
            if (selectedUri == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Galería")
                    }
                    Button(onClick = { navController.navigate("camera_video") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Videocam, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Grabar")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { selectedUri = null; trimStartMs = 0L; trimEndMs = -1L; viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                    FilledTonalButton(
                        onClick = {
                            backStackEntry?.savedStateHandle?.set("trimInputUri", selectedUri.toString())
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

            if (selectedUri != null && trimEndMs > trimStartMs && trimEndMs > 0L) {
                Spacer(Modifier.height(12.dp))
                AssistChip(
                    onClick = {
                        backStackEntry?.savedStateHandle?.set("trimInputUri", selectedUri.toString())
                        navController.navigate("video_trimmer")
                    },
                    label = { Text("Trim: ${formatTrimLabel(trimStartMs)} → ${formatTrimLabel(trimEndMs)}") },
                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Selector de música — Material You 3 ──
            if (selectedUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selectedTrack != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                    border = if (selectedTrack != null) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (selectedTrack != null) selectedTrack!!.title else "Agregar música",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (selectedTrack != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (selectedTrack != null) "${selectedTrack!!.artist} • Música ${ (musicVolume*100).toInt()}% / Voz ${ (originalVolume*100).toInt()}%" else "Elige una canción para tu reel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (selectedTrack != null) {
                            IconButton(onClick = { selectedTrack = null; selectedMusicUri = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar música")
                            }
                        }
                        FilledTonalButton(onClick = { showMusicSheet = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(if (selectedTrack != null) "Cambiar" else "Elegir")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Watermark toggle
            if (selectedUri != null) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Marca de agua \"Vivid\"", style = MaterialTheme.typography.titleSmall)
                            Text("Tu logo en cada frame", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = withWatermark, onCheckedChange = { withWatermark = it })
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

            val isBusy = state !is CreateReelUiState.Idle && state !is CreateReelUiState.Error && state !is CreateReelUiState.Success

            Button(
                onClick = {
                    selectedUri?.let {
                        viewModel.publishReel(
                            context = context,
                            videoUri = it,
                            caption = caption,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            withWatermark = withWatermark,
                            musicUri = selectedMusicUri,
                            musicAssetFile = selectedTrack?.assetFile,
                            musicVolume = musicVolume,
                            originalVolume = originalVolume
                        )
                    }
                },
                enabled = selectedUri != null && !isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
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
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(err.message, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMusicSheet) {
        MusicSelectorBottomSheet(
            selected = selectedTrack,
            selectedUri = selectedMusicUri,
            musicVolume = musicVolume,
            originalVolume = originalVolume,
            onDismiss = { showMusicSheet = false },
            onSelected = { track, uri ->
                selectedTrack = track
                selectedMusicUri = uri
            },
            onRemove = { selectedTrack = null; selectedMusicUri = null },
            onVolumeChange = { mv, ov -> musicVolume = mv; originalVolume = ov }
        )
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
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())
}

private fun formatTrimLabel(ms: Long): String {
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun ProgressOverlay(label: String, success: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (success) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
            else CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

package com.vivid.app.presentation.stories

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.delay

/**
 * Pantalla para crear un Story (foto o video) — VERSIÓN MEJORADA 2026-08-09
 *
 * Mejoras:
 * - Fix botón: ahora siempre se habilita cuando hay media seleccionada
 * - Auto-trim a 15s estilo IG (se muestra badge informativo)
 * - Selector de audio del dispositivo (mp3, m4a, wav, etc.) para video stories
 *   usa AudioMixer para reemplazar el audio original
 * - Estados nuevos: Trimming y MixingAudio con overlays
 * - Manejo robusto de errores y reset
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
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioFileName by remember { mutableStateOf<String?>(null) }

    // Recoger video grabado por la cámara
    val backStackEntry = navController.currentBackStackEntry
    val recordedFlow = backStackEntry?.savedStateHandle?.getStateFlow("recordedVideo", "")
    val recordedPathState = recordedFlow?.collectAsState(initial = "")
    val recordedPath = recordedPathState?.value ?: ""

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
            selectedAudioUri = null
            audioFileName = null
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

    // NUEVO: selector de audio del dispositivo
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAudioUri = it
            // Intentar obtener nombre del archivo
            audioFileName = try {
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else "audio"
                } ?: "audio"
            } catch (_: Exception) {
                "audio seleccionado"
            }
        }
    }

    // Fallback para galería vieja (si PickVisualMedia falla)
    val fallbackImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = MediaKind.PHOTO
            selectedAudioUri = null
            audioFileName = null
            viewModel.reset()
        }
    }
    val fallbackVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = MediaKind.VIDEO
            viewModel.reset()
        }
    }

    LaunchedEffect(state) {
        if (state is CreateStoryUiState.Success) {
            delay(800)
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
                                "Dura 24h • máx 15s • con música opcional",
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

                // Badge de auto-trim
                if (mediaType == MediaKind.VIDEO && mediaUri != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Auto-recorte a 15s (estilo IG)",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Overlay de progreso con todos los estados nuevos
                when (val s = state) {
                    is CreateStoryUiState.Trimming -> ProgressOverlay("Recortando a 15s… ${s.percent}%")
                    is CreateStoryUiState.Compressing -> ProgressOverlay("Comprimiendo… ${s.percent}%")
                    is CreateStoryUiState.MixingAudio -> ProgressOverlay("Mezclando audio… ${s.percent}%")
                    is CreateStoryUiState.Watermarking -> ProgressOverlay("Marca de agua… ${s.percent}%")
                    is CreateStoryUiState.Uploading -> ProgressOverlay("Subiendo… ${s.percent}%")
                    is CreateStoryUiState.SavingMetadata -> ProgressOverlay("Guardando…")
                    is CreateStoryUiState.Success -> ProgressOverlay("¡Publicado! ✓", success = true)
                    else -> {}
                }
            }

            Spacer(Modifier.height(16.dp))

            if (mediaUri == null) {
                // Selección inicial
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                videoLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            } catch (_: Exception) {
                                fallbackVideoLauncher.launch("video/*")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            } catch (_: Exception) {
                                fallbackImageLauncher.launch("image/*")
                            }
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
                // Cuando ya hay media seleccionada: opciones de audio + cambiar
                if (mediaType == MediaKind.VIDEO) {
                    // Selector de audio
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedAudioUri != null)
                                MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (selectedAudioUri != null)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (selectedAudioUri != null) "Música: ${audioFileName ?: "seleccionada"}" else "Agregar música del dispositivo",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selectedAudioUri != null)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (selectedAudioUri != null) "Se reemplazará el audio original" else "mp3, m4a, wav — opcional",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedAudioUri != null)
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (selectedAudioUri != null) {
                                IconButton(onClick = {
                                    selectedAudioUri = null
                                    audioFileName = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar audio")
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { audioPickerLauncher.launch("audio/*") }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Añadir")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Botón para cambiar audio si ya hay uno
                    if (selectedAudioUri == null) {
                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Elegir música del teléfono")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    // Foto: informar que el audio solo funciona con video por ahora
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Tip: para stories con música, usa video. El audio para fotos viene pronto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            mediaUri = null
                            mediaType = null
                            selectedAudioUri = null
                            audioFileName = null
                            viewModel.reset()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                    if (mediaType == MediaKind.VIDEO && selectedAudioUri == null) {
                        FilledTonalButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Música")
                        }
                    }
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
                            MediaKind.VIDEO -> viewModel.publishVideoStory(context, uri, caption, selectedAudioUri)
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
                    val label = when (state) {
                        is CreateStoryUiState.Trimming -> "Recortando a 15s…"
                        is CreateStoryUiState.Compressing -> "Comprimiendo…"
                        is CreateStoryUiState.MixingAudio -> "Mezclando audio…"
                        is CreateStoryUiState.Watermarking -> "Marca de agua…"
                        is CreateStoryUiState.Uploading -> "Subiendo…"
                        is CreateStoryUiState.SavingMetadata -> "Guardando…"
                        else -> "Subiendo…"
                    }
                    Text(label)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.reset() }) {
                            Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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

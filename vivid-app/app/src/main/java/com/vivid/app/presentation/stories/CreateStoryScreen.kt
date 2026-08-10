package com.vivid.app.presentation.stories

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.vivid.app.presentation.create.MusicSelectorBottomSheet
import com.vivid.app.presentation.create.MusicTrack
import kotlinx.coroutines.delay
import com.vivid.app.theme.SquircleShape

/**
 * Crear Story — Material You 3 + Música APK + Dispositivo + Auto-trim 15s
 *
 * - Diseño Material You 3 con colores temáticos por defecto (secondaryContainer, primaryContainer)
 * - Música: del dispositivo (via picker) Y de la librería del APK (assets/music) usando MusicSelectorBottomSheet
 * - Auto-trim a 15s estilo IG
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

    // Música: soporta tanto APK (asset) como dispositivo (Uri)
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var audioFileName by remember { mutableStateOf<String?>(null) }
    var showMusicSheet by remember { mutableStateOf(false) }

    // Trim de audio a 15s (nuevo)
    var pendingTrimUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTrimTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showTrimSheet by remember { mutableStateOf(false) }

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
            selectedTrack = null
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

    val fallbackImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            mediaUri = it
            mediaType = MediaKind.PHOTO
            selectedAudioUri = null
            selectedTrack = null
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

    // Helper para resolver nombre de archivo de audio del dispositivo
    fun resolveAudioName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else "audio"
            } ?: "audio"
        } catch (_: Exception) { "audio" }
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
                title = { Text("Crear Story", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = SquircleShape(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(9f / 16f)
                        .clip(SquircleShape()),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        mediaUri == null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Surface(
                                    shape = SquircleShape(),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Elige foto o video",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Las stories duran 24 horas",
                                    color = Color.White.copy(alpha = 0.7f),
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

                    if (mediaType == MediaKind.VIDEO && mediaUri != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = SquircleShape(),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auto-recorte 15s IG", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

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
            }

            Spacer(Modifier.height(16.dp))

            if (mediaUri == null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            } catch (_: Exception) { fallbackVideoLauncher.launch("video/*") }
                        },
                        modifier = Modifier.weight(1f),
                        shape = SquircleShape(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Video")
                    }
                    FilledTonalButton(
                        onClick = {
                            try {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            } catch (_: Exception) { fallbackImageLauncher.launch("image/*") }
                        },
                        modifier = Modifier.weight(1f),
                        shape = SquircleShape()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Foto")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate("camera_video") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleShape()
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Grabar video ahora")
                }
            } else {
                // Música: Card Material You 3 que usa MusicSelectorBottomSheet (APK + dispositivo)
                if (mediaType == MediaKind.VIDEO) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTrack != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = if (selectedTrack != null) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = SquircleShape(),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
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
                                    if (selectedTrack != null) "${selectedTrack!!.artist} • ${selectedTrack!!.mood}" else "Del dispositivo o de la app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (selectedTrack != null) {
                                IconButton(onClick = { selectedTrack = null; selectedAudioUri = null; audioFileName = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar")
                                }
                            }
                            FilledTonalButton(
                                onClick = { showMusicSheet = true },
                                shape = SquircleShape()
                            ) {
                                Text(if (selectedTrack != null) "Cambiar" else "Elegir")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = SquircleShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Para stories con música usa video. Con foto, la música viene pronto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            mediaUri = null
                            mediaType = null
                            selectedTrack = null
                            selectedAudioUri = null
                            audioFileName = null
                            viewModel.reset()
                        },
                        modifier = Modifier.weight(1f),
                        shape = SquircleShape()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                    if (mediaType == MediaKind.VIDEO) {
                        Button(
                            onClick = { showMusicSheet = true },
                            modifier = Modifier.weight(1f),
                            shape = SquircleShape()
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Música")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Texto") },
                placeholder = { Text("Escribe algo…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = SquircleShape(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )

            Spacer(Modifier.height(20.dp))

            val isBusy = state !is CreateStoryUiState.Idle && state !is CreateStoryUiState.Error && state !is CreateStoryUiState.Success

            // Determinar la Uri real de audio para el ViewModel:
            // - Si el track es de la APK (assetFile != null y uri == null), necesitamos copiar asset a cache y pasar Uri del archivo
            // - Si el track tiene uri (dispositivo), usar esa uri
            // Para simplificar, CreateStoryViewModel ya soporta audioUri del dispositivo; para assets, necesitamos resolver.
            // Por ahora, si es asset, lo resolvemos copiando a cache aquí.
            Button(
                onClick = {
                    mediaUri?.let { uri ->
                        when (mediaType) {
                            MediaKind.VIDEO -> {
                                // Resolver audio: si es asset, copiar a cache y obtener Uri
                                val finalAudioUri = when {
                                    selectedAudioUri != null -> selectedAudioUri
                                    selectedTrack?.assetFile != null -> {
                                        try {
                                            // Copiar asset a archivo temporal
                                            val assetPath = selectedTrack!!.assetFile!!
                                            val input = context.assets.open(assetPath)
                                            val tempFile = java.io.File(context.cacheDir, "story_asset_${System.currentTimeMillis()}_${assetPath.substringAfterLast("/")}")
                                            tempFile.outputStream().use { out -> input.copyTo(out) }
                                            input.close()
                                            Uri.fromFile(tempFile)
                                        } catch (e: Exception) { null }
                                    }
                                    selectedTrack?.uri != null -> selectedTrack?.uri
                                    else -> null
                                }
                                viewModel.publishVideoStory(context, uri, caption, finalAudioUri)
                            }
                            MediaKind.PHOTO -> viewModel.publishPhotoStory(context, uri, caption)
                            null -> {}
                        }
                    }
                },
                enabled = mediaUri != null && !isBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = SquircleShape()
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    val label = when (state) {
                        is CreateStoryUiState.Trimming -> "Recortando 15s…"
                        is CreateStoryUiState.Compressing -> "Comprimiendo…"
                        is CreateStoryUiState.MixingAudio -> "Mezclando audio…"
                        is CreateStoryUiState.Watermarking -> "Marca de agua…"
                        is CreateStoryUiState.Uploading -> "Subiendo…"
                        else -> "Subiendo…"
                    }
                    Text(label)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar Story", fontWeight = FontWeight.Bold)
                }
            }

            (state as? CreateStoryUiState.Error)?.let { err ->
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = SquircleShape(), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Error", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { viewModel.reset() }) { Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showMusicSheet) {
        MusicSelectorBottomSheet(
            selected = selectedTrack,
            selectedUri = selectedAudioUri,
            musicVolume = 1f,
            originalVolume = 0.3f,
            onDismiss = { showMusicSheet = false },
            onSelected = { track, uri ->
                // Si el usuario eligió audio del dispositivo (uri != null) → ofrecer recorte a 15s
                if (uri != null) {
                    pendingTrimTrack = track
                    pendingTrimUri = uri
                    showTrimSheet = true
                    showMusicSheet = false
                } else if (track.assetFile != null) {
                    // Asset del APK: copiar a cache y ofrecer recorte también (por si quiere recortar)
                    try {
                        val assetPath = track.assetFile
                        val input = context.assets.open(assetPath)
                        val tempFile = java.io.File(context.cacheDir, "story_asset_${System.currentTimeMillis()}_${assetPath.substringAfterLast("/")}")
                        tempFile.outputStream().use { out -> input.copyTo(out) }
                        input.close()
                        val assetUri = Uri.fromFile(tempFile)
                        pendingTrimTrack = track
                        pendingTrimUri = assetUri
                        showTrimSheet = true
                        showMusicSheet = false
                    } catch (_: Exception) {
                        // Si falla el copy, usar directo sin recorte
                        selectedTrack = track
                        selectedAudioUri = uri
                        audioFileName = track.title
                        showMusicSheet = false
                    }
                } else {
                    selectedTrack = track
                    selectedAudioUri = uri
                    audioFileName = track.title
                    showMusicSheet = false
                }
            },
            onRemove = { selectedTrack = null; selectedAudioUri = null; audioFileName = null },
            onVolumeChange = { _, _ -> }
        )
    }

    if (showTrimSheet && pendingTrimUri != null) {
        com.vivid.app.presentation.create.AudioTrimBottomSheet(
            audioUri = pendingTrimUri!!,
            originalName = pendingTrimTrack?.title ?: "Audio",
            onDismiss = {
                // Si cancela recorte, usar original sin recortar
                showTrimSheet = false
                selectedTrack = pendingTrimTrack
                selectedAudioUri = pendingTrimUri
                audioFileName = pendingTrimTrack?.title
                pendingTrimUri = null
                pendingTrimTrack = null
            },
            onTrimConfirmed = { trimmedUri, startMs, endMs ->
                selectedTrack = pendingTrimTrack?.copy(
                    title = "${pendingTrimTrack?.title} (${(endMs - startMs)/1000}s recorte)",
                    durationLabel = "${(endMs - startMs)/1000}s"
                ) ?: pendingTrimTrack
                selectedAudioUri = trimmedUri
                audioFileName = "${pendingTrimTrack?.title} recortado ${startMs/1000}-${endMs/1000}s"
                showTrimSheet = false
                pendingTrimUri = null
                pendingTrimTrack = null
            }
        )
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
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ProgressOverlay(label: String, success: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (success) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
            else CircularProgressIndicator(modifier = Modifier.size(64.dp), strokeWidth = 4.dp, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

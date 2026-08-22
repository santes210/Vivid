package com.vivid.app.presentation.stories

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.presentation.create.MusicSelectorBottomSheet
import com.vivid.app.presentation.create.MusicTrack
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.VividMaterialShapes
import kotlinx.coroutines.delay

/**
 * Crear Story — Material You 3 Expressive.
 *
 * FIX (2026-08-15): la Column no tenía scroll y la preview 9:16 a ancho
 * completo empujaba música, texto y el botón Publicar FUERA de la pantalla
 * (por eso "no funcionaba" Publicar: era inalcanzable). Ahora:
 *   - El botón Publicar vive en un bottomBar fijo: SIEMPRE visible.
 *   - El contenido hace scroll (verticalScroll).
 *   - La preview tiene altura contenida (max 420dp) manteniendo 9:16.
 *   - Formas del sistema expresivo (VividExpressiveShapes), no radios sueltos.
 *
 * Lo que ya hacía y se conserva:
 *   - Música del APK (assets) y del dispositivo + recorte a 15s.
 *   - Compresión de foto (ImageCompressor) y video (VideoCompressor).
 *   - expiresAt = 24h; limpieza Firestore+B2 al expirar (cleanExpiredStories).
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

    // Trim de audio a 15s
    var pendingTrimUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTrimTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showTrimSheet by remember { mutableStateOf(false) }

    val backStackEntry = navController.currentBackStackEntry
    val recordedFlow = backStackEntry?.savedStateHandle?.getStateFlow("recordedVideo", "")
    val recordedPathState = recordedFlow?.collectAsState(initial = "")
    val recordedPath = recordedPathState?.value ?: ""

    // Limpieza oportunista: borra stories propias ya expiradas (Firestore + B2)
    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            viewModel.cleanExpiredStories(uid)
        }
    }

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

    val isBusy = state !is CreateStoryUiState.Idle &&
        state !is CreateStoryUiState.Error &&
        state !is CreateStoryUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crear story", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                actions = {
                    // Chip informativo 24h — expressive, comunica la regla del producto
                    Surface(
                        shape = VividExpressiveShapes.ChipSelected,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(end = VividSpace.s)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(VividSpace.xxs))
                            Text(
                                "24 h",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        },
        // FIX CLAVE: el botón Publicar vive aquí, anclado abajo, SIEMPRE visible.
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VividSpace.m, vertical = VividSpace.s)
                        .navigationBarsPadding()
                ) {
                    (state as? CreateStoryUiState.Error)?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = VividExpressiveShapes.SmallCard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(VividSpace.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.width(VividSpace.xs))
                                Text(
                                    err.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.reset() }) {
                                    Text("Reintentar", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            mediaUri?.let { uri ->
                                when (mediaType) {
                                    MediaKind.VIDEO -> {
                                        // Resolver audio: si es asset del APK, copiar a cache y obtener Uri
                                        val finalAudioUri = when {
                                            selectedAudioUri != null -> selectedAudioUri
                                            selectedTrack?.assetFile != null -> {
                                                try {
                                                    val assetPath = selectedTrack!!.assetFile!!
                                                    val input = context.assets.open(assetPath)
                                                    val tempFile = java.io.File(
                                                        context.cacheDir,
                                                        "story_asset_${System.currentTimeMillis()}_${assetPath.substringAfterLast("/")}"
                                                    )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = VividExpressiveShapes.PrimaryButton
                    ) {
                        if (isBusy) {
                            LoadingIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                polygons = VividMaterialShapes.LoadingSequence
                            )
                            Spacer(Modifier.width(10.dp))
                            val label = when (state) {
                                is CreateStoryUiState.Trimming -> "Recortando 15s…"
                                is CreateStoryUiState.Compressing -> "Comprimiendo…"
                                is CreateStoryUiState.MixingAudio -> "Mezclando audio…"
                                is CreateStoryUiState.Watermarking -> "Marca de agua…"
                                is CreateStoryUiState.Uploading -> "Subiendo…"
                                else -> "Publicando…"
                            }
                            Text(label)
                        } else if (state is CreateStoryUiState.Success) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("¡Publicada!", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("Publicar story", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // FIX CLAVE: scroll — antes el contenido se cortaba sin remedio
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VividSpace.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(VividSpace.xs))

            // ── Preview 9:16 con altura CONTENIDA (antes devoraba la pantalla) ──
            Card(
                shape = VividExpressiveShapes.HeroCard,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .clip(VividExpressiveShapes.HeroCard),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        mediaUri == null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(VividSpace.l)
                            ) {
                                Surface(
                                    shape = VividExpressiveShapes.SelectedContainerActive,
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
                                Spacer(Modifier.height(VividSpace.m))
                                Text(
                                    "Elige foto o video",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Dura 24 horas y se borra sola",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        mediaType == MediaKind.VIDEO -> VideoPreview(mediaUri!!)
                        else -> Image(
                            painter = rememberAsyncImagePainter(mediaUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (mediaType == MediaKind.VIDEO && mediaUri != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = VividExpressiveShapes.ChipSelected,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(VividSpace.s)
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
                                Text("Auto-recorte 15s", color = Color.White, style = MaterialTheme.typography.labelSmall)
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

            Spacer(Modifier.height(VividSpace.m))

            if (mediaUri == null) {
                // ── Selección de medio: tiles expresivos ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VividSpace.s),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            } catch (_: Exception) { fallbackVideoLauncher.launch("video/*") }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = VividExpressiveShapes.SecondaryButton,
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
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = VividExpressiveShapes.SecondaryButton
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Foto")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { navController.navigate("camera_video") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = VividExpressiveShapes.PrimaryButton
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Grabar video ahora")
                }
            } else {
                // ── Música (video): card expresiva con MusicSelectorBottomSheet ──
                if (mediaType == MediaKind.VIDEO) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VividExpressiveShapes.MediumCard,
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTrack != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        border = if (selectedTrack != null) BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        ) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = VividExpressiveShapes.AvatarSquircle,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(VividSpace.s))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (selectedTrack != null) selectedTrack!!.title else "Agregar música",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedTrack != null) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    if (selectedTrack != null) "${selectedTrack!!.artist} • ${selectedTrack!!.mood}"
                                    else "Del dispositivo o de la app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            if (selectedTrack != null) {
                                IconButton(onClick = {
                                    selectedTrack = null
                                    selectedAudioUri = null
                                    audioFileName = null
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar música")
                                }
                            }
                            FilledTonalButton(
                                onClick = { showMusicSheet = true },
                                shape = VividExpressiveShapes.SegmentedControl
                            ) {
                                Text(if (selectedTrack != null) "Cambiar" else "Elegir")
                            }
                        }
                    }
                    Spacer(Modifier.height(VividSpace.s))
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VividExpressiveShapes.SmallCard,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(VividSpace.s),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(VividSpace.xs))
                            Text(
                                "Para stories con música usa video. Con foto, la música viene pronto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(VividSpace.s))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(VividSpace.s), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            mediaUri = null
                            mediaType = null
                            selectedTrack = null
                            selectedAudioUri = null
                            audioFileName = null
                            viewModel.reset()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = VividExpressiveShapes.SecondaryButton
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cambiar")
                    }
                    if (mediaType == MediaKind.VIDEO) {
                        Button(
                            onClick = { showMusicSheet = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = VividExpressiveShapes.SecondaryButton
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Música")
                        }
                    }
                }
            }

            Spacer(Modifier.height(VividSpace.m))

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Texto") },
                placeholder = { Text("Escribe algo…") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = VividExpressiveShapes.FieldFocused,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )

            Spacer(Modifier.height(VividSpace.l))
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
                    // Asset del APK: copiar a cache y ofrecer recorte también
                    try {
                        val assetPath = track.assetFile
                        val input = context.assets.open(assetPath)
                        val tempFile = java.io.File(
                            context.cacheDir,
                            "story_asset_${System.currentTimeMillis()}_${assetPath.substringAfterLast("/")}"
                        )
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
                    title = "${pendingTrimTrack?.title} (${(endMs - startMs) / 1000}s recorte)",
                    durationLabel = "${(endMs - startMs) / 1000}s"
                ) ?: pendingTrimTrack
                selectedAudioUri = trimmedUri
                audioFileName = "${pendingTrimTrack?.title} recortado ${startMs / 1000}-${endMs / 1000}s"
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
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
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
            if (success) Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = LocalVividAccents.current.online,
                modifier = Modifier.size(64.dp)
            )
            else LoadingIndicator(modifier = Modifier.size(64.dp), color = Color.White, polygons = VividMaterialShapes.LoadingSequence)
            Spacer(Modifier.height(VividSpace.s))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

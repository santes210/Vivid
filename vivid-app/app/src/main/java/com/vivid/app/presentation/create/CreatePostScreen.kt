package com.vivid.app.presentation.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage

private enum class CreateContentType {
    POST,
    STORY,
    REEL
}

/**
 * CreatePostScreen v3 — Material You 3 + Música
 *
 * - Posts ahora pueden llevar música: del dispositivo o de la librería del APK (assets/music)
 * - Diseño Material You 3 completo con colores temáticos por defecto (primaryContainer, secondaryContainer)
 * - Preview grande con Card redondeada
 * - Selector de música estilo Reel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    navController: NavController,
    onPostCreated: () -> Unit = {},
    viewModel: CreatePostViewModel = hiltViewModel()
) {
    var selectedContentType by remember { mutableStateOf(CreateContentType.POST) }
    var caption by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Música
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedMusicUri by remember { mutableStateOf<Uri?>(null) }
    var showMusicSheet by remember { mutableStateOf(false) }

    // Trim de audio del dispositivo a 15s
    var pendingTrimUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTrimTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showTrimSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val capturedPhotoPathState = currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("capturedPhoto", "")
        ?.collectAsState()
    val capturedPhotoPath = capturedPhotoPathState?.value.orEmpty()

    val publishState by viewModel.state.collectAsState()
    LaunchedEffect(publishState) {
        when (publishState) {
            is CreatePostUiState.Idle -> { isUploading = false; errorMessage = null }
            is CreatePostUiState.Compressing -> { isUploading = true; uploadProgress = "Comprimiendo… ${(publishState as CreatePostUiState.Compressing).percent}%" }
            is CreatePostUiState.Uploading -> { isUploading = true; uploadProgress = "Subiendo imagen… ${(publishState as CreatePostUiState.Uploading).percent}%" }
            is CreatePostUiState.UploadingAudio -> { isUploading = true; uploadProgress = "Subiendo audio… ${(publishState as CreatePostUiState.UploadingAudio).percent}%" }
            is CreatePostUiState.SavingMetadata -> { isUploading = true; uploadProgress = "Guardando…" }
            is CreatePostUiState.Success -> {
                isUploading = false
                uploadProgress = "¡Publicado!"
                viewModel.reset()
                onPostCreated()
                navController.popBackStack()
            }
            is CreatePostUiState.Error -> {
                isUploading = false
                errorMessage = (publishState as CreatePostUiState.Error).message
            }
        }
    }

    LaunchedEffect(capturedPhotoPath) {
        if (capturedPhotoPath.isNotBlank()) {
            selectedImageUri = Uri.parse(capturedPhotoPath)
            errorMessage = null
            currentBackStackEntry?.savedStateHandle?.remove<String>("capturedPhoto")
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
        errorMessage = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedContentType) {
                            CreateContentType.POST -> "Crear publicación"
                            CreateContentType.STORY -> "Crear story"
                            CreateContentType.REEL -> "Crear Reel"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selector de tipo (Material You 3 FilterChips con colores temáticos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedContentType == CreateContentType.POST,
                    onClick = { selectedContentType = CreateContentType.POST },
                    label = { Text("Publicación", fontWeight = if (selectedContentType == CreateContentType.POST) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
                FilterChip(
                    selected = selectedContentType == CreateContentType.STORY,
                    onClick = { selectedContentType = CreateContentType.STORY },
                    label = { Text("Story") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                FilterChip(
                    selected = selectedContentType == CreateContentType.REEL,
                    onClick = { selectedContentType = CreateContentType.REEL },
                    label = { Text("Reel") },
                    leadingIcon = { Icon(Icons.Default.MovieCreation, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Si eligió Reel, redirigir
            if (selectedContentType == CreateContentType.REEL) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.MovieCreation,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Crear Reel con video", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Elige o graba → ajusta el trim → agrega música del dispositivo o de la app → publica",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { navController.navigate("create_reel") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Ir a crear Reel")
                        }
                    }
                }
                return@Column
            }

            if (selectedContentType == CreateContentType.STORY) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Stories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Las stories duran 1 dia y puedes agregarle musica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { navController.navigate("create_story") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ir a crear Story")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Preview grande con Material You 3 Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Imagen seleccionada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(100.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Selecciona o toma una foto",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Luego puedes añadir música",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Botones Galería / Cámara — Material You 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }
                Button(
                    onClick = { navController.navigate("camera") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Selector de música (Material You 3) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTrack != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                border = if (selectedTrack != null) androidx.compose.foundation.BorderStroke(
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
                        shape = RoundedCornerShape(12.dp),
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
                            if (selectedTrack != null) "${selectedTrack!!.artist} • ${selectedTrack!!.mood}" else "Del dispositivo o de la app.",
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
                    FilledTonalButton(
                        onClick = { showMusicSheet = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (selectedTrack != null) "Cambiar" else "Elegir")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Caption con Material You 3
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                placeholder = { Text("Simplemente....  Escribe....") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            Spacer(Modifier.height(20.dp))

            // Botón Publicar — Material You 3 con progreso
            Button(
                onClick = {
                    if (selectedContentType == CreateContentType.POST && selectedImageUri != null) {
                        isUploading = true
                        errorMessage = null
                        uploadProgress = "Comprimiendo..."
                        viewModel.publishPost(
                            context = context,
                            imageUri = selectedImageUri!!,
                            caption = caption,
                            musicTrack = selectedTrack,
                            musicUri = selectedMusicUri
                        )
                    }
                },
                enabled = !isUploading && selectedImageUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(uploadProgress, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar", fontWeight = FontWeight.Bold)
                }
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
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
            musicVolume = 1f,
            originalVolume = 0f,
            onDismiss = { showMusicSheet = false },
            onSelected = { track, uri ->
                // Si es audio del dispositivo (uri != null), ofrecer recorte a 15s
                if (uri != null && track.artist == "Tu dispositivo") {
                    pendingTrimTrack = track
                    pendingTrimUri = uri
                    showTrimSheet = true
                    showMusicSheet = false
                } else {
                    // Asset del APK: usar directo (ya es corto, pero también se puede recortar si el usuario quiere)
                    // Para assets largos, también permitir recorte
                    if (uri != null) {
                        // uri de dispositivo ya con posible recorte previo, o asset copiado
                        pendingTrimTrack = track
                        pendingTrimUri = uri
                        showTrimSheet = true
                        showMusicSheet = false
                    } else {
                        // Asset sin uri: track.assetFile != null, se usará directo sin recorte (o recorte posterior si quiere)
                        selectedTrack = track
                        selectedMusicUri = null
                        showMusicSheet = false
                    }
                }
            },
            onRemove = { selectedTrack = null; selectedMusicUri = null },
            onVolumeChange = { _, _ -> }
        )
    }

    if (showTrimSheet && pendingTrimUri != null) {
        AudioTrimBottomSheet(
            audioUri = pendingTrimUri!!,
            originalName = pendingTrimTrack?.title ?: "Audio",
            onDismiss = {
                showTrimSheet = false
                // Si cancela el trim, usar el original sin recortar
                selectedTrack = pendingTrimTrack
                selectedMusicUri = pendingTrimUri
                pendingTrimUri = null
                pendingTrimTrack = null
            },
            onTrimConfirmed = { trimmedUri, startMs, endMs ->
                // Guardar el recorte de 15s
                selectedTrack = pendingTrimTrack?.copy(
                    title = "${pendingTrimTrack?.title ?: "Audio"} (${(endMs - startMs)/1000}s recorte)",
                    durationLabel = "${(endMs - startMs)/1000}s"
                ) ?: pendingTrimTrack
                selectedMusicUri = trimmedUri
                showTrimSheet = false
                pendingTrimUri = null
                pendingTrimTrack = null
            }
        )
    }
}

package com.vivid.app.presentation.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.vivid.app.R
import com.vivid.app.domain.model.PostVisibility
import com.vivid.app.theme.SoraFamily
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividMaterialShapes
import com.vivid.app.ui.components.pressPrimaryButtonShape

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
    // Audiencia de la publicación: Público o Solo amigos (seguidores).
    var audience by rememberSaveable { mutableStateOf(PostVisibility.PUBLIC) }
    var selectedMusicUri by remember { mutableStateOf<Uri?>(null) }
    var showMusicSheet by remember { mutableStateOf(false) }

    // Trim de audio del dispositivo a 15s
    var pendingTrimUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTrimTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showTrimSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val publishInteraction = remember { MutableInteractionSource() }
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
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                // El Scaffold de navegación ya aplica safeDrawing: no
                // re-consumir los top insets (doble padding de status bar).
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(VividSpace.m),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selector de tipo (Material You 3 FilterChips con colores temáticos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)
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

            Spacer(modifier = Modifier.height(VividSpace.m))

            // Si eligió Reel, redirigir
            if (selectedContentType == CreateContentType.REEL) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = VividExpressiveShapes.HeroCard,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(VividSpace.l),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = VividExpressiveShapes.MediumCard,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.MovieCreation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(VividSpace.m))
                        Text("Crear Reel con video", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(VividSpace.xs))
                        Text(
                            "Elige o graba → ajusta el trim → agrega música del dispositivo o de la app → publica",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(VividSpace.l))
                        Button(
                            onClick = { navController.navigate("create_reel") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = VividExpressiveShapes.SecondaryButton
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("Ir a crear Reel")
                        }
                    }
                }
                return@Column
            }

            if (selectedContentType == CreateContentType.STORY) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = VividExpressiveShapes.MediumCard,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(VividSpace.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("Stories", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Spacer(Modifier.height(VividSpace.xs))
                        Text(
                            "Las stories duran 1 dia y puedes agregarle musica.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(VividSpace.s))
                        FilledTonalButton(
                            onClick = { navController.navigate("create_story") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = VividExpressiveShapes.SecondaryButton
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Ir a crear Story")
                        }
                    }
                }
                Spacer(Modifier.height(VividSpace.m))
            }

            // Preview grande con Material You 3 Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = VividExpressiveShapes.HeroCard,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
                            modifier = Modifier.padding(VividSpace.l)
                        ) {
                            Surface(
                                shape = VividExpressiveShapes.HeroCard,
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
                            Spacer(Modifier.height(VividSpace.m))
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

            Spacer(Modifier.height(VividSpace.m))

            // Botones Galería / Cámara — Material You 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(VividSpace.s)
            ) {
                FilledTonalButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = VividExpressiveShapes.SecondaryButton,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(VividSpace.xs))
                    Text("Galería")
                }
                Button(
                    onClick = { navController.navigate("camera") },
                    modifier = Modifier.weight(1f),
                    shape = VividExpressiveShapes.SecondaryButton
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(VividSpace.xs))
                    Text("Cámara")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Selector de música (Material You 3) ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = VividExpressiveShapes.MediumCard,
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedTrack != null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
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
                        shape = VividExpressiveShapes.Media,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(VividSpace.s))
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
                        shape = VividExpressiveShapes.SegmentedControl
                    ) {
                        Text(
                            stringResource(
                                if (selectedTrack != null) R.string.create_music_change
                                else R.string.create_music_pick
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Audiencia — Material You: segmented buttons ──
            // "¿Quién puede ver esto?" resuelto con el componente canónico de
            // selección única de M3, en vez de un switch o un menú escondido.
            Text(
                stringResource(R.string.audience_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(VividSpace.xs))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = audience == PostVisibility.PUBLIC,
                    onClick = { audience = PostVisibility.PUBLIC },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Outlined.Public, contentDescription = null) },
                    label = { Text(stringResource(R.string.audience_public)) }
                )
                SegmentedButton(
                    selected = audience == PostVisibility.FRIENDS,
                    onClick = { audience = PostVisibility.FRIENDS },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Outlined.Group, contentDescription = null) },
                    label = { Text(stringResource(R.string.audience_friends)) }
                )
            }
            Text(
                stringResource(
                    if (audience == PostVisibility.PUBLIC) R.string.audience_public_hint
                    else R.string.audience_friends_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = VividSpace.xxs)
            )

            Spacer(Modifier.height(20.dp))

            // Caption con Material You 3
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text(stringResource(R.string.create_caption_label)) },
                placeholder = { Text(stringResource(R.string.create_caption_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                shape = VividExpressiveShapes.FieldFocused,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
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
                            musicUri = selectedMusicUri,
                            visibility = audience
                        )
                    }
                },
                enabled = !isUploading && selectedImageUri != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                interactionSource = publishInteraction,
                shape = pressPrimaryButtonShape(publishInteraction),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                if (isUploading) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        polygons = VividMaterialShapes.LoadingSequence
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        uploadProgress,
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(VividSpace.xs))
                    Text(
                        "Publicar",
                        fontFamily = SoraFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(VividSpace.s))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = VividExpressiveShapes.SmallCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(VividSpace.s), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(VividSpace.xs))
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(VividSpace.l))
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

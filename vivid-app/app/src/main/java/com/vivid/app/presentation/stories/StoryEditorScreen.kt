package com.vivid.app.presentation.stories

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Editor de Story estilo Instagram.
 *
 * Funciones:
 *   - Anadir texto con posicion, color, tamano
 *   - Anadir stickers (emojis) arrastables
 *   - Mover, rotar, escalar con gestos
 *   - Exportar el resultado como bitmap para subir a B2
 *
 * Funciona para fotos Y videos (para video, renderiza los overlays
 * como un bitmap estatico que se superpone al video al exportar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun StoryEditorScreen(
    navController: NavController,
    inputUri: Uri,
    isVideo: Boolean,
    onPublish: (renderedBitmap: Bitmap, overlays: List<StoryOverlay>) -> Unit
) {
    val context = LocalContext.current
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var overlays by remember { mutableStateOf<List<StoryOverlay>>(emptyList()) }
    var selectedOverlayId by remember { mutableStateOf<String?>(null) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showStickerSheet by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    // Cargar el bitmap base (para fotos) o thumbnail del video (para preview)
    LaunchedEffect(inputUri) {
        withContext(Dispatchers.IO) {
            if (!isVideo) {
                context.contentResolver.openInputStream(inputUri)?.use {
                    baseBitmap = BitmapFactory.decodeStream(it)
                }
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, inputUri)
                baseBitmap = retriever.getFrameAtTime(
                    500_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                retriever.release()
            }
        }
    }

    // Setup del ExoPlayer si es video
    LaunchedEffect(inputUri, isVideo) {
        if (isVideo) {
            videoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(inputUri))
                repeatMode = ExoPlayer.REPEAT_MODE_ALL
                volume = 0f
                prepare()
                playWhenReady = true
            }
        }
    }
    DisposableEffect(videoPlayer) {
        onDispose { videoPlayer?.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Story") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            baseBitmap?.let { bmp ->
                                val rendered = StoryOverlayRenderer.renderOverlays(bmp, overlays)
                                onPublish(rendered, overlays)
                            }
                        },
                        enabled = baseBitmap != null
                    ) {
                        Text("Siguiente", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorToolButton(
                        icon = Icons.Default.TextFields,
                        label = "Texto",
                        onClick = { showTextDialog = true }
                    )
                    EditorToolButton(
                        icon = Icons.Default.EmojiEmotions,
                        label = "Sticker",
                        onClick = { showStickerSheet = true }
                    )
                    if (selectedOverlayId != null) {
                        EditorToolButton(
                            icon = Icons.Default.ColorLens,
                            label = "Color",
                            onClick = { showColorPicker = true }
                        )
                        EditorToolButton(
                            icon = Icons.Default.Delete,
                            label = "Borrar",
                            tint = MaterialTheme.colorScheme.error,
                            onClick = {
                                overlays = overlays.filter { it.id != selectedOverlayId }
                                selectedOverlayId = null
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .onSizeChanged { canvasSize = it },
            contentAlignment = Alignment.Center
        ) {
            // Base: foto o video
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (isVideo && videoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                player = videoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (baseBitmap != null) {
                    Image(
                        bitmap = baseBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Overlays
                if (canvasSize.width > 0) {
                    overlays.forEach { overlay ->
                        OverlayItem(
                            overlay = overlay,
                            isSelected = overlay.id == selectedOverlayId,
                            onSelect = { selectedOverlayId = overlay.id },
                            onUpdate = { updated ->
                                overlays = overlays.map {
                                    if (it.id == overlay.id) updated else it
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog para anadir texto
    if (showTextDialog) {
        TextInputDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text ->
                if (text.isNotBlank()) {
                    overlays = overlays + StoryOverlay.TextOverlay(
                        text = text,
                        color = Color.White.value.toInt()
                    )
                }
                showTextDialog = false
            }
        )
    }

    // Bottom sheet de stickers
    if (showStickerSheet) {
        ModalBottomSheet(onDismissRequest = { showStickerSheet = false }) {
            StickerPickerContent(
                onPick = { emoji ->
                    overlays = overlays + StoryOverlay.StickerOverlay(emoji = emoji)
                    showStickerSheet = false
                }
            )
        }
    }

    // Color picker
    if (showColorPicker) {
        ColorPickerDialog(
            onPick = { color ->
                overlays = overlays.map {
                    when (it) {
                        is StoryOverlay.TextOverlay -> it.copy(color = color)
                        is StoryOverlay.StickerOverlay -> it.copy(color = color)
                    }
                }
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun OverlayItem(
    overlay: StoryOverlay,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onUpdate: (StoryOverlay) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    x = (overlay.x * 1000).toInt(),  // se posiciona via .offset()
                    y = (overlay.y * 1000).toInt()
                )
            }
            .pointerInput(overlay.id) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    onSelect()
                    val updated = when (overlay) {
                        is StoryOverlay.TextOverlay -> overlay.copy(
                            x = (overlay.x + pan.x / 1000f).coerceIn(0f, 1f),
                            y = (overlay.y + pan.y / 1000f).coerceIn(0f, 1f),
                            scale = (overlay.scale * zoom).coerceIn(0.5f, 3f),
                            rotation = overlay.rotation + rotation
                        )
                        is StoryOverlay.StickerOverlay -> overlay.copy(
                            x = (overlay.x + pan.x / 1000f).coerceIn(0f, 1f),
                            y = (overlay.y + pan.y / 1000f).coerceIn(0f, 1f),
                            scale = (overlay.scale * zoom).coerceIn(0.5f, 3f),
                            rotation = overlay.rotation + rotation
                        )
                    }
                    onUpdate(updated)
                }
            }
            .padding(8.dp)
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        when (overlay) {
            is StoryOverlay.TextOverlay -> {
                Text(
                    text = overlay.text,
                    color = Color(overlay.color),
                    fontSize = (overlay.fontSizeSp * overlay.scale).sp,
                    fontWeight = if (overlay.fontWeight == FontWeight.BOLD)
                        ComposeFontWeight.Bold else ComposeFontWeight.Normal,
                    modifier = Modifier
                        .graphicsLayer(rotationZ = overlay.rotation)
                )
            }
            is StoryOverlay.StickerOverlay -> {
                Text(
                    text = overlay.emoji,
                    fontSize = (48 * overlay.scale).sp,
                    modifier = Modifier
                        .graphicsLayer(rotationZ = overlay.rotation)
                )
            }
        }
    }
}

@Composable
private fun EditorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun TextInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar texto") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Escribe...") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun StickerPickerContent(onPick: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "Elige un sticker",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        StickerLibrary.categories.forEach { (category, stickers) ->
            Text(
                category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(stickers) { emoji ->
                    Text(
                        emoji,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                            .pointerInput(emoji) {
                                detectTapGestures(onTap = { onPick(emoji) })
                            }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ColorPickerDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = listOf(
        Color.White, Color.Black, Color.Red, Color(0xFFFF6B6B),
        Color(0xFFFFD93D), Color(0xFF6BCB77), Color(0xFF4D96FF),
        Color(0xFFB983FF), Color(0xFFFF9F45)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color") },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                colors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(c)
                            .pointerInput(c) {
                                detectTapGestures(onTap = { onPick(c.value.toInt()) })
                            }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

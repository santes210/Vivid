package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes

// ─────────────────────────────────────────────────────────────
// Model
// ─────────────────────────────────────────────────────────────

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val mood: String, // Energético, Chill, Lo-Fi, Pop, Electronic
    val durationLabel: String, // "0:30" etc
    val assetFile: String? = null, // e.g. "music/happy.wav" inside assets
    val uri: Uri? = null // custom picked
)

object MusicCatalog {
    // Curated demo tracks — if asset not present, UI still shows them but mixing will require custom pick
    val demoTracks = listOf(
        MusicTrack("1", "Vivid Pop Energy", "Luna Skye", "Pop", "0:29", "music/vivid_pop.mp3"),
        MusicTrack("2", "Lo-Fi Dreams", "A. Kumo", "Lo-Fi", "1:12", "music/lofi_dreams.mp3"),
        MusicTrack("3", "Sunset Chill", "Coastline", "Chill", "0:45", "music/sunset_chill.mp3"),
        MusicTrack("4", "Neon Nights", "Synthwave 84", "Electronic", "0:38", "music/neon_nights.mp3"),
        MusicTrack("5", "Happy Loop", "Joy Parade", "Pop", "0:32", "music/happy_loop.mp3"),
        MusicTrack("6", "Calm Piano", "E. Sol", "Chill", "1:05", "music/calm_piano.mp3"),
        MusicTrack("7", "Energetic Beat", "Rush", "Energético", "0:27", "music/energetic_beat.mp3"),
        MusicTrack("8", "Midnight Lo-Fi", "Night Owl", "Lo-Fi", "0:58", "music/midnight_lofi.mp3"),
    )

    val moods = listOf("Todos", "Pop", "Lo-Fi", "Chill", "Electronic", "Energético")

    fun discoverAssets(context: Context): List<MusicTrack> {
        return try {
            val files = context.assets.list("music")?.filter { com.vivid.app.util.MusicAssets.isPackedAudio(it) } ?: emptyList()
            files.mapIndexed { idx, name ->
                MusicTrack(
                    id = "asset_$idx",
                    title = name.substringBefore(".").replace("_", " ").replace("-", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    artist = "Librería local",
                    mood = "Pop",
                    durationLabel = "—",
                    assetFile = "music/$name"
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

// ─────────────────────────────────────────────────────────────
// BottomSheet UI — Material You 3 Expressive
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSelectorBottomSheet(
    selected: MusicTrack?,
    selectedUri: Uri?,
    musicVolume: Float,
    originalVolume: Float,
    onDismiss: () -> Unit,
    onSelected: (MusicTrack, Uri?) -> Unit, // track + resolved uri
    onRemove: () -> Unit,
    onVolumeChange: (musicVol: Float, originalVol: Float) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var moodFilter by remember { mutableStateOf("Todos") }
    var localVolume by remember(musicVolume) { mutableFloatStateOf(musicVolume) }
    var localOriginal by remember(originalVolume) { mutableFloatStateOf(originalVolume) }

    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast("/")?.take(28) ?: "Audio personalizado"
            val track = MusicTrack(id = "custom_${System.currentTimeMillis()}", title = name, artist = "Tu dispositivo", mood = "Pop", durationLabel = "—", uri = uri)
            onSelected(track, uri)
        }
    }

    val assetTracks = remember { MusicCatalog.discoverAssets(context) }
    val allTracks = remember(assetTracks) {
        if (assetTracks.isNotEmpty()) assetTracks else MusicCatalog.demoTracks
    }

    val filtered = remember(search, moodFilter, allTracks) {
        allTracks.filter { t ->
            (moodFilter == "Todos" || t.mood == moodFilter) &&
            (search.isBlank() || t.title.contains(search, ignoreCase = true) || t.artist.contains(search, ignoreCase = true))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = VividExpressiveShapes.BottomSheet
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = VividSpace.s)) {
            // Handle + header
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(VividSpace.s))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Elige tu música", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Toca para preview • Se mezclará con tu video", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            }

            if (selected != null) {
                Spacer(Modifier.height(14.dp))
                SelectedMusicHeader(track = selected, onRemove = onRemove)
            }

            Spacer(Modifier.height(14.dp))
            // Search + device pick
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.m),
                placeholder = { Text("Buscar canción o artista…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, contentDescription = null) } },
                singleLine = true,
                shape = VividExpressiveShapes.FieldResting,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = VividSpace.m), horizontalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
                items(MusicCatalog.moods, key = { it }) { mood ->
                    val isSel = mood == moodFilter
                    FilterChip(
                        selected = isSel,
                        onClick = { moodFilter = mood },
                        label = { Text(mood, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium) },
                        leadingIcon = if (isSel) ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { devicePicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.m), shape = VividExpressiveShapes.SecondaryButton) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                Spacer(Modifier.width(VividSpace.xs))
                Text("Elegir audio del dispositivo")
            }

            Spacer(Modifier.height(VividSpace.s))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = VividSpace.m))
            Spacer(Modifier.height(VividSpace.xs))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.xs), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { track ->
                        MusicTrackRow(
                            track = track,
                            isSelected = selected?.id == track.id,
                            onSelect = {
                                // Resolve uri: custom uri ?? asset uri ?? null demo (needs device pick)
                                val resolved: Uri? = when {
                                    track.uri != null -> track.uri
                                    track.assetFile != null -> {
                                        // try to resolve asset -> copy to cache and return uri? For preview we play asset via ExoPlayer using asset://
                                        // We store as asset:// and resolve at mix time by copying to file
                                        null // will be handled in ViewModel via asset copy
                                    }
                                    else -> null
                                }
                                // If demo without file, still select but user will need to pick device later? We allow selection.
                                onSelected(track, resolved ?: track.uri)
                            }
                        )
                    }
                }
            }

            // Volume controls when a track is selected
            if (selected != null) {
                Spacer(Modifier.height(VividSpace.s))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.m), shape = VividExpressiveShapes.MediumCard, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(modifier = Modifier.padding(VividSpace.m), verticalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
                        Text("Mezcla de audio", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("Música", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium)
                            Slider(value = localVolume, onValueChange = { localVolume = it; onVolumeChange(localVolume, localOriginal) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                            Text("${(localVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(VividSpace.xs))
                            Text("Video original", modifier = Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium)
                            Slider(value = localOriginal, onValueChange = { localOriginal = it; onVolumeChange(localVolume, localOriginal) }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                            Text("${(localOriginal * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        Text("Tip: baja el volumen del video si quieres que la música destaque.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(VividSpace.s))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.m), horizontalArrangement = Arrangement.spacedBy(VividSpace.s)) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f), shape = VividExpressiveShapes.SecondaryButton) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quitar")
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), shape = VividExpressiveShapes.SecondaryButton) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Usar canción")
                    }
                }
            }

            Spacer(Modifier.height(VividSpace.xs))
        }
    }
}

@Composable
private fun SelectedMusicHeader(track: MusicTrack, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = VividSpace.m), shape = VividExpressiveShapes.MediumCard, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(VividSpace.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} • ${track.durationLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            AssistChip(onClick = onRemove, label = { Text("Cambiar") }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }
    }
}

@Composable
private fun MusicTrackRow(track: MusicTrack, isSelected: Boolean, onSelect: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember(track.id) { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Manage preview playback
    DisposableEffect(track.id, isPlaying) {
        if (isPlaying) {
            val uriToPlay: Uri? = when {
                track.uri != null -> track.uri
                track.assetFile != null -> {
                    // ExoPlayer can play asset via "asset:///music/..."
                    Uri.parse("asset:///${com.vivid.app.util.MusicAssets.resolvePackedPath(track.assetFile)}")
                }
                else -> null
            }
            if (uriToPlay != null) {
                val p = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uriToPlay))
                    prepare()
                    playWhenReady = true
                    volume = 0.8f
                }
                player = p
            }
        } else {
            player?.release()
            player = null
        }
        onDispose {
            player?.release()
            player = null
        }
    }

    val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = VividExpressiveShapes.MediumCard,
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(VividSpace.s), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(VividExpressiveShapes.Media).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Icon(if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(VividSpace.s))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} • ${track.mood} • ${track.durationLabel}", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.75f), maxLines = 1)
            }
            Spacer(Modifier.width(VividSpace.xs))
            FilledTonalIconButton(
                onClick = {
                    if (track.uri == null && track.assetFile == null) {
                        onSelect()
                    } else {
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Preview", modifier = Modifier.size(20.dp))
            }
            if (!isSelected) {
                Spacer(Modifier.width(6.dp))
                RadioButton(selected = false, onClick = onSelect)
            } else {
                Spacer(Modifier.width(6.dp))
                RadioButton(selected = true, onClick = null)
            }
        }
    }
}

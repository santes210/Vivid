package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.vivid.app.util.AudioTrimmer
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimBottomSheet(
    audioUri: Uri,
    originalName: String = "Audio",
    onDismiss: () -> Unit,
    onTrimConfirmed: (trimmedUri: Uri, startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var isLoadingDuration by remember { mutableStateOf(true) }
    var isTrimming by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

    // Cargar duración
    LaunchedEffect(audioUri) {
        durationMs = AudioTrimmer.getDurationMs(context, audioUri)
        isLoadingDuration = false
        if (durationMs > 15_000) {
            startMs = 0L
        } else {
            startMs = 0L
        }
    }

    // Manejador de preview
    DisposableEffect(audioUri, startMs, durationMs, isPlaying) {
        if (isPlaying) {
            val end = (startMs + 15_000).coerceAtMost(durationMs)
            val mediaItem = MediaItem.Builder()
                .setUri(audioUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(end)
                        .build()
                )
                .build()
            val p = ExoPlayer.Builder(context).build().apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
                volume = 1f
            }
            player = p
        } else {
            player?.release()
            player = null
        }
        onDispose {
            player?.release()
            player = null
        }
    }

    val maxStart = (durationMs - 15_000).coerceAtLeast(0L)
    val endMs = (startMs + 15_000).coerceAtMost(durationMs)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recortar audio", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "$originalName • ${formatMs(durationMs)} total → recorte 15s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cerrar") }
            }

            if (isLoadingDuration) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Segmento: ${formatMs(startMs)} → ${formatMs(endMs)} (15s máx)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (durationMs > 15_000) {
                            Text(
                                "Desliza para elegir desde qué segundo empieza el recorte. Se recortarán 15 segundos a partir de ahí, estilo IG.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = startMs.toFloat(),
                                onValueChange = { startMs = it.toLong() },
                                valueRange = 0f..maxStart.toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatMs(0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatMs(maxStart), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Text(
                                "Este audio dura menos de 15s (${formatMs(durationMs)}), se usará completo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPlaying) "Pausar preview" else "Probar recorte")
                            }
                            OutlinedButton(
                                onClick = {
                                    startMs = 0L
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Inicio")
                            }
                        }
                    }
                }

                // Botón confirmar recorte
                Button(
                    onClick = {
                        if (durationMs <= 15_000) {
                            // No necesita recorte, devolver original
                            onTrimConfirmed(audioUri, 0L, durationMs)
                        } else {
                            // Recortar
                            isTrimming = true
                            scope.launch {
                                try {
                                    // Usar .m4a para compatibilidad con MediaMuxer (MP4 container)
                                    val outFile = File(context.cacheDir, "trimmed_audio_${System.currentTimeMillis()}.m4a")
                                    val trimmedPath = AudioTrimmer.trimAudio(
                                        context = context,
                                        inputUri = audioUri,
                                        outputFile = outFile,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    val trimmedFile = File(trimmedPath)
                                    val trimmedUri = if (trimmedFile.exists() && trimmedFile.length() > 1024) {
                                        Uri.fromFile(trimmedFile)
                                    } else {
                                        // Fallback: usar original pero con clip info
                                        audioUri
                                    }
                                    onTrimConfirmed(trimmedUri, startMs, endMs)
                                } catch (e: Exception) {
                                    // Fallback: devolver original con el rango seleccionado
                                    onTrimConfirmed(audioUri, startMs, endMs)
                                } finally {
                                    isTrimming = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isTrimming
                ) {
                    if (isTrimming) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Recortando…")
                    } else {
                        Icon(Icons.Default.ContentCut, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (durationMs > 15_000) "Usar recorte 15s" else "Usar audio completo")
                    }
                }

                TextButton(
                    onClick = { onTrimConfirmed(audioUri, 0L, durationMs.coerceAtMost(15_000)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Usar sin recortar (completo)")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

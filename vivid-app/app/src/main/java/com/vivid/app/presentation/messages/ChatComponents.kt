package com.vivid.app.presentation.messages

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.vivid.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.util.formatVoiceDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

@Composable
internal fun RecordingBar(durationMs: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Red))
            Spacer(Modifier.width(10.dp))
            Text(formatVoiceDuration(durationMs), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            RecordingWaveform(modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.onErrorContainer) }
            FilledIconButton(onClick = onSend, modifier = Modifier.size(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                Icon(Icons.Default.Send, contentDescription = "Enviar voz", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun RecordingWaveform(modifier: Modifier = Modifier) {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val bars = 18

    if (!animationsEnabled) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(bars) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                )
            }
        }
        return
    }

    val infinite = rememberInfiniteTransition(label = "wave")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(bars) { i ->
            val anim by infinite.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(420 + i * 35, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "bar$i")
            Box(modifier = Modifier.width(3.dp).height((10 + 16 * anim).dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)))
        }
    }
}

@Composable
internal fun TypingIndicatorBubble() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    if (!animationsEnabled) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.widthIn(max = 120.dp)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.msg_typing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val infinite = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.widthIn(max = 120.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { idx ->
                    val delayMs = idx * 200
                    val scale by infinite.animateFloat(initialValue = 0.6f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = delayMs, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "dot$idx")
                    val alpha by infinite.animateFloat(initialValue = 0.45f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = delayMs), repeatMode = RepeatMode.Reverse), label = "a$idx")
                    Box(modifier = Modifier.size((8 * scale).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)))
                }
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.msg_typing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DateHeaderPill(timestamp: Long) {
    // Separador de fecha discreto: solo texto centrado, sin píldora ni sombra
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            text = formatDateHeader(timestamp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        )
    }
}

internal sealed interface MessageListItem {
    val key: String
    data class ChatMessage(val message: Message) : MessageListItem { override val key get() = message.id }
    data class Upload(val upload: ImageUpload) : MessageListItem { override val key get() = "upload_${upload.localId}" }
    data class Voice(val upload: VoiceUpload) : MessageListItem { override val key get() = "vup_${upload.localId}" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    isGroupStart: Boolean,
    isGroupEnd: Boolean,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onResignImage: (Message) -> Unit = {},
    onResignVoice: (Message) -> Unit = {}
) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 20.dp, topEnd = if (isGroupStart) 20.dp else 6.dp, bottomStart = 20.dp, bottomEnd = if (isGroupEnd) 20.dp else 6.dp)
    } else {
        RoundedCornerShape(topStart = if (isGroupStart) 20.dp else 6.dp, topEnd = 20.dp, bottomStart = if (isGroupEnd) 20.dp else 6.dp, bottomEnd = 20.dp)
    }
    // Burbujas con colores tonales del usuario: primario para las mías, neutro para las suyas.
    // Sin sombras ni degradados (M3 Expressive: superficies planas con jerarquía tonal).
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(shape = bubbleShape, color = bubbleColor, tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.widthIn(max = 280.dp).pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress() }, onDoubleTap = { onDoubleTap() }) }) {
                    Box(modifier = Modifier.padding(horizontal = if (message.type == "image") 4.dp else 14.dp, vertical = if (message.type == "image" || message.type == "voice") 8.dp else 9.dp)) {
                        when (message.type) {
                            "image" -> ImageMessageContent(message = message, isMine = isMine, onImageClick = onImageClick, onResignImage = onResignImage, onLongPress = onLongPress, onDoubleTap = onDoubleTap)
                            "voice" -> VoiceMessageContent(message = message, isMine = isMine, onResignVoice = onResignVoice)
                            "story_reply" -> StoryReplyContent(message = message, isMine = isMine)
                            else -> Column {
                                Text(text = com.vivid.app.util.SettingsManager.filterOffensiveWords(message.text), color = contentColor, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp))
                                Spacer(Modifier.height(3.dp))
                                MessageMetaRow(message = message, isMine = isMine)
                            }
                        }
                    }
                }
                if (message.reaction.isNotBlank()) {
                    Box(modifier = Modifier.offset(x = if (isMine) (-4).dp else 4.dp, y = 12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape).clickable { onDoubleTap() }.padding(horizontal = 5.dp, vertical = 1.dp), contentAlignment = Alignment.Center) { Text(text = message.reaction, fontSize = 13.sp) }
                }
            }
            if (message.reaction.isNotBlank()) Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageMessageContent(message: Message, isMine: Boolean, onImageClick: (String) -> Unit, onResignImage: (Message) -> Unit, onLongPress: () -> Unit = {}, onDoubleTap: () -> Unit = {}) {
    var resignAttempted by remember(message.id) { mutableStateOf(false) }
    Column {
        Box(modifier = Modifier.defaultMinSize(minWidth = 160.dp, minHeight = 160.dp).sizeIn(maxWidth = 240.dp, maxHeight = 320.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = { onImageClick(message.imageUrl) }, onLongClick = { onLongPress() }, onDoubleClick = { onDoubleTap() })) {
            AsyncImage(model = message.imageUrl, contentDescription = "Imagen del chat", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { if (message.imageKey.isNotBlank() && !resignAttempted) { resignAttempted = true; onResignImage(message) } })
        }
        Spacer(Modifier.height(3.dp))
        MessageMetaRow(message = message, isMine = isMine)
    }
}

@Composable
internal fun VoiceMessageContent(message: Message, isMine: Boolean, onResignVoice: (Message) -> Unit) {
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var progress by remember(message.id) { mutableFloatStateOf(0f) }
    var showError by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val player = remember(message.voiceUrl) {
        ExoPlayer.Builder(ctx).build().apply {
            // Caché local: las notas de voz de B2 no se re-descargan en cada escucha
            if (com.vivid.app.util.VideoCacheManager.isCacheable(message.voiceUrl)) {
                setMediaSource(com.vivid.app.util.VideoCacheManager.buildCachedMediaSource(ctx, message.voiceUrl))
            } else {
                setMediaItem(MediaItem.fromUri(message.voiceUrl))
            }
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    val totalMs = message.voiceDurationMs.coerceAtLeast(1L)
    LaunchedEffect(isPlaying, message.id) {
        if (isPlaying) {
            player.play()
            while (isPlaying) {
                progress = (player.currentPosition.toFloat() / totalMs).coerceIn(0f, 1f)
                if (player.playbackState == Player.STATE_ENDED || progress >= 1f) {
                    progress = 1f
                    isPlaying = false
                    player.pause()
                    player.seekTo(0)
                    break
                }
                delay(100)
            }
        } else {
            player.pause()
        }
    }

    Column(modifier = Modifier.widthIn(min = 210.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledIconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                // Waveform que avanza con la reproducción
                VoiceWaveform(progress = progress, isMine = isMine)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (isPlaying) {
                        "${formatVoiceDuration((progress * totalMs).toLong())} / ${formatVoiceDuration(totalMs)}"
                    } else {
                        formatVoiceDuration(totalMs)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        MessageMetaRow(message = message, isMine = isMine, showResignVoice = showError, onResignVoice = onResignVoice)
    }
}

@Composable
internal fun VoiceWaveform(progress: Float, isMine: Boolean) {
    val barHeights = listOf(5, 10, 7, 13, 9, 15, 8, 12, 6, 14, 10, 16, 7, 11, 9, 13, 6, 12, 8, 14)
    val activeColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val idleColor = activeColor.copy(alpha = 0.3f)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        barHeights.forEachIndexed { index, h ->
            val played = index < (barHeights.size * progress).toInt()
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (played) activeColor else idleColor)
            )
        }
    }
}

@Composable
internal fun StoryReplyContent(message: Message, isMine: Boolean) {
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f) else MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Respuesta a story", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else contentColor)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(text = com.vivid.app.util.SettingsManager.filterOffensiveWords(message.text), color = contentColor, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp))
        Spacer(Modifier.height(3.dp))
        MessageMetaRow(message = message, isMine = isMine)
    }
}

@Composable
internal fun MessageMetaRow(message: Message, isMine: Boolean, showResignVoice: Boolean = false, onResignVoice: (Message) -> Unit = {}) {
    val metaColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        if (message.isEdited) {
            Text(
                text = stringResource(R.string.msg_edited),
                style = MaterialTheme.typography.labelSmall,
                color = metaColor,
                fontSize = 9.sp
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor,
            fontSize = 9.sp
        )
        if (isMine) {
            Spacer(Modifier.width(3.dp))
            val icon: ImageVector
            val tint: Color
            val receiptCd: String
            when {
                message.isRead -> {
                    icon = Icons.Filled.DoneAll
                    tint = MaterialTheme.colorScheme.tertiary
                    receiptCd = stringResource(R.string.msg_read)
                }
                message.isDelivered -> {
                    icon = Icons.Filled.DoneAll
                    tint = metaColor
                    receiptCd = stringResource(R.string.msg_delivered)
                }
                else -> {
                    icon = Icons.Filled.Check
                    tint = metaColor
                    receiptCd = stringResource(R.string.msg_sent)
                }
            }
            Icon(icon, contentDescription = receiptCd, tint = tint, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
internal fun UploadBubble(upload: ImageUpload, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text = when (upload.phase) { ImageUpload.Phase.COMPRESSING -> "Comprimiendo imagen…"; ImageUpload.Phase.UPLOADING -> "Subiendo imagen… ${upload.progress}%"; ImageUpload.Phase.DONE -> "Imagen enviada"; ImageUpload.Phase.FAILED -> "No se pudo enviar la imagen" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (upload.phase) {
                    ImageUpload.Phase.COMPRESSING -> { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary) }
                    ImageUpload.Phase.UPLOADING -> { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { upload.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainer) }
                    ImageUpload.Phase.FAILED -> { Spacer(Modifier.height(4.dp)); Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onDismiss) { Text("Descartar") }; TextButton(onClick = onRetry) { Text("Reintentar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }
                    ImageUpload.Phase.DONE -> Unit
                }
            }
        }
    }
}

@Composable
internal fun VoiceUploadBubble(
    upload: VoiceUpload,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.widthIn(max = 240.dp)) {
                    Text(
                        text = if (upload.phase == ImageUpload.Phase.FAILED) {
                            upload.error ?: "No se pudo enviar la voz"
                        } else {
                            "Subiendo nota de voz… ${upload.progress}%"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (upload.phase == ImageUpload.Phase.UPLOADING) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { upload.progress / 100f },
                            modifier = Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                    if (upload.phase == ImageUpload.Phase.FAILED) {
                        Row(modifier = Modifier.align(Alignment.End)) {
                            TextButton(onClick = onDismiss) { Text("Descartar") }
                            TextButton(onClick = onRetry) { Text("Reintentar") }
                        }
                    }
                }
            }
        }
    }
}

internal fun isSameDay(t1: Long, t2: Long): Boolean { val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); return df.format(Date(t1)) == df.format(Date(t2)) }
internal fun formatDateHeader(timestamp: Long): String { val now = System.currentTimeMillis(); return when { isSameDay(timestamp, now) -> "Hoy"; isSameDay(timestamp, now - 86_400_000) -> "Ayer"; else -> SimpleDateFormat("d 'de' MMMM", Locale.getDefault()).format(Date(timestamp)) } }


package com.vivid.app.presentation.messages

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.util.formatVoiceDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.DisposableEffect
import com.vivid.app.theme.SquircleShape


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    receiverId: String,
    otherUserName: String = "Usuario",
    onBack: () -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val clipboardManager = LocalClipboardManager.current
    val animationsEnabled = LocalVividAnimationsEnabled.current
    val messages: List<Message> = viewModel.messages.collectAsState(initial = emptyList()).value
    val canMessage: Boolean = viewModel.canMessage.collectAsState(initial = true).value
    val isOtherTyping: Boolean = viewModel.isOtherTyping.collectAsState().value
    val isRecording: Boolean = viewModel.isRecording.collectAsState().value
    val recDuration: Long = viewModel.recordingDurationMs.collectAsState().value

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }

    val imageUploads: List<ImageUpload> = viewModel.imageUploads.collectAsState(initial = emptyList()).value
    val voiceUploads: List<VoiceUpload> = viewModel.voiceUploads.collectAsState(initial = emptyList()).value
    var viewerImageUrl by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.sendImage(chatId, receiverId, uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startVoiceRecording()
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatId, receiverId, otherUserName) {
        viewModel.openChat(chatId, receiverId, otherUserName)
    }

    LaunchedEffect(
        messages.size,
        imageUploads.size,
        voiceUploads.size,
        isOtherTyping,
        animationsEnabled
    ) {
        if (messages.isNotEmpty() || isOtherTyping) {
            // Mantener el chat abajo; sin movimiento, saltar directamente al último mensaje.
            try {
                if (animationsEnabled) listState.animateScrollToItem(0)
                else listState.scrollToItem(0)
            } catch (_: Exception) {
                // La lista puede cambiar mientras se ejecuta el desplazamiento.
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { if (receiverId.isNotBlank()) onOpenProfile(receiverId) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = otherUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                otherUserName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (isOtherTyping) {
                                Text(
                                    "escribiendo…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    if (canMessage) "• En línea" else "Cuenta privada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canMessage) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ver perfil") },
                                onClick = {
                                    showMenu = false
                                    if (receiverId.isNotBlank()) onOpenProfile(receiverId)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        val bgBrush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                when {
                    !canMessage -> {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = SquircleShape(),
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No puedes enviar mensajes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Esta cuenta es privada y todavía no la sigues.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    messages.isEmpty() && imageUploads.none { it.phase != ImageUpload.Phase.DONE } && voiceUploads.isEmpty() && !isOtherTyping -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Surface(shape = SquircleShape(), color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Aún no hay mensajes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Envía el primer mensaje para empezar la conversación.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    else -> {
                        val listItems = remember(messages, imageUploads, voiceUploads) {
                            buildList {
                                messages.reversed().forEach { add(MessageListItem.ChatMessage(it)) }
                                imageUploads.filter { it.phase != ImageUpload.Phase.DONE }.reversed().forEach { add(MessageListItem.Upload(it)) }
                                voiceUploads.filter { it.phase != ImageUpload.Phase.DONE }.reversed().forEach { add(MessageListItem.Voice(it)) }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            reverseLayout = true
                        ) {
                            // Typing indicator as first item (bottom)
                            if (isOtherTyping) {
                                item(key = "typing") {
                                    TypingIndicatorBubble()
                                }
                            }
                            itemsIndexed(listItems, key = { _, item -> item.key }) { index, item ->
                                when (item) {
                                    is MessageListItem.Upload -> {
                                        UploadBubble(upload = item.upload, onRetry = { viewModel.retryImageUpload(chatId, receiverId, item.upload.localId) }, onDismiss = { viewModel.dismissImageUpload(item.upload.localId) })
                                    }
                                    is MessageListItem.Voice -> {
                                        VoiceUploadBubble(upload = item.upload, onDismiss = { viewModel.dismissVoiceUpload(item.upload.localId) })
                                    }
                                    is MessageListItem.ChatMessage -> {
                                        val message = item.message
                                        val isMine = message.senderId == currentUserId
                                        val reversed = messages.reversed()
                                        val prevMsg = if (index < reversed.size - 1) reversed[index + 1] else null
                                        val nextMsg = if (index > 0) reversed[index - 1] else null
                                        val isSameAsPrev = prevMsg?.senderId == message.senderId
                                        val isSameAsNext = nextMsg?.senderId == message.senderId
                                        val showDateHeader = prevMsg == null || !isSameDay(message.timestamp, prevMsg.timestamp)
                                        val isGroupStart = isSameAsPrev != true
                                        val isGroupEnd = isSameAsNext != true

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = if (isGroupStart) 10.dp else 1.dp)
                                        ) {
                                            if (showDateHeader) DateHeaderPill(timestamp = message.timestamp)
                                            MessageBubble(
                                                message = message,
                                                isMine = isMine,
                                                isGroupStart = isGroupStart,
                                                isGroupEnd = isGroupEnd,
                                                onLongPress = {
                                                    activeReactionMessageId = message.id
                                                    selectedMessageForOptions = message
                                                },
                                                onDoubleTap = { viewModel.reactToMessage(chatId, message.id, "❤️") },
                                                onImageClick = { url -> viewerImageUrl = url },
                                                onResignImage = { msg -> viewModel.refreshImageUrl(msg.id, msg.imageKey) },
                                                onResignVoice = { msg -> viewModel.refreshVoiceUrl(msg.id, msg.voiceKey) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Input bar (or recording bar) ──
                if (canMessage) {
                    if (isRecording) {
                        RecordingBar(
                            durationMs = recDuration,
                            onCancel = { viewModel.stopVoiceRecording(cancel = true) },
                            onSend = {
                                val file = viewModel.stopVoiceRecording(cancel = false)
                                if (file != null) {
                                    val dur = recDuration.coerceAtLeast(900L)
                                    viewModel.sendVoice(chatId, receiverId, file, dur)
                                }
                            }
                        )
                    } else {
                        // ── Composer tipo dock: anclado al borde inferior, sin sombras ──
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick = { showAttachMenu = true },
                                    modifier = Modifier.size(44.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Adjuntar", modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = {
                                        messageText = it
                                        viewModel.onTextChanged(chatId, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Escribe un mensaje…") },
                                    maxLines = 4,
                                    shape = SquircleShape(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (messageText.isNotBlank()) {
                                    FilledIconButton(
                                        enabled = receiverId.isNotBlank(),
                                        onClick = {
                                            val text = messageText.trim()
                                            if (text.isNotBlank()) {
                                                viewModel.sendMessage(chatId, text, receiverId)
                                                messageText = ""
                                            }
                                        },
                                        modifier = Modifier.size(46.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                            if (hasPerm) viewModel.startVoiceRecording()
                                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        modifier = Modifier.size(46.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                    ) {
                                        Icon(Icons.Filled.Mic, contentDescription = "Voz", modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = activeReactionMessageId != null,
                enter = if (animationsEnabled) fadeIn() + scaleIn() else EnterTransition.None,
                exit = if (animationsEnabled) fadeOut() + scaleOut() else ExitTransition.None,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
            ) {
                // Menú de reacciones expresivo: superficie tonal plana, sin sombras ni bordes
                Surface(
                    shape = SquircleShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "🥰", "😂", "😮", "😢", "🫶", "🔥", "🙃").forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.clickable {
                                    activeReactionMessageId?.let { msgId -> viewModel.reactToMessage(chatId, msgId, emoji) }
                                    activeReactionMessageId = null
                                    selectedMessageForOptions = null
                                }
                            ) {
                                Text(text = emoji, fontSize = 27.sp, modifier = Modifier.padding(5.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMessageForOptions?.let { message ->
        ModalBottomSheet(
            onDismissRequest = { selectedMessageForOptions = null; activeReactionMessageId = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Opciones del Mensaje", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                when (message.type) {
                    "image" -> {
                        ListItem(headlineContent = { Text("Ver imagen") }, supportingContent = { Text("Imagen adjunta", maxLines = 1) }, leadingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(SquircleShape()).clickable { viewerImageUrl = message.imageUrl; selectedMessageForOptions = null; activeReactionMessageId = null }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    "voice" -> {
                        ListItem(headlineContent = { Text("Nota de voz") }, supportingContent = { Text(formatVoiceDuration(message.voiceDurationMs), maxLines = 1) }, leadingContent = { Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(SquircleShape()), colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    else -> {
                        ListItem(headlineContent = { Text("Copiar texto") }, supportingContent = { Text(message.text.take(80), maxLines = 1) }, leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(SquircleShape()).clickable { clipboardManager.setText(AnnotatedString(message.text)); selectedMessageForOptions = null; activeReactionMessageId = null }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                }
                if (message.senderId == currentUserId) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ListItem(headlineContent = { Text("Eliminar mensaje", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) }, supportingContent = { Text(when(message.type){ "image" -> "Se borrará la imagen también del servidor."; "voice" -> "Se borrará el audio también."; else -> "Se borrará esta burbuja permanentemente." }) }, leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }, modifier = Modifier.clip(SquircleShape()).clickable { viewModel.deleteMessage(chatId, message); selectedMessageForOptions = null; activeReactionMessageId = null }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Adjuntos en bottom sheet (composer) ──
    if (showAttachMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Adjuntar",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ListItem(
                    headlineContent = { Text("Enviar foto") },
                    supportingContent = { Text("Desde tu galería") },
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(SquircleShape()).clickable {
                        showAttachMenu = false
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Ver perfil") },
                    supportingContent = { Text("@$otherUserName") },
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(SquircleShape()).clickable {
                        showAttachMenu = false
                        if (receiverId.isNotBlank()) onOpenProfile(receiverId)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Copiar chat ID") },
                    supportingContent = { Text("Para soporte o depuración") },
                    leadingContent = { Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(SquircleShape()).clickable {
                        clipboardManager.setText(AnnotatedString(chatId))
                        showAttachMenu = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    viewerImageUrl?.let { url ->
        Dialog(onDismissRequest = { viewerImageUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.96f)).clickable { viewerImageUrl = null }, contentAlignment = Alignment.Center) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 4f); offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan } }, contentAlignment = Alignment.Center) {
                    AsyncImage(model = url, contentDescription = "Imagen del chat", modifier = Modifier.fillMaxWidth().padding(16.dp).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
                }
                Surface(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), shape = SquircleShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(text = "Visor de Imagen", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewerImageUrl = null }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBar(durationMs: Long, onCancel: () -> Unit, onSend: () -> Unit) {
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
private fun RecordingWaveform(modifier: Modifier = Modifier) {
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
                        .clip(SquircleShape())
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
            Box(modifier = Modifier.width(3.dp).height((10 + 16 * anim).dp).clip(SquircleShape()).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)))
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val animationsEnabled = LocalVividAnimationsEnabled.current
    if (!animationsEnabled) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
            Surface(shape = SquircleShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.widthIn(max = 120.dp)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("escribiendo…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }

    val infinite = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        Surface(shape = SquircleShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 0.dp, shadowElevation = 0.dp, modifier = Modifier.widthIn(max = 120.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { idx ->
                    val delayMs = idx * 200
                    val scale by infinite.animateFloat(initialValue = 0.6f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = delayMs, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "dot$idx")
                    val alpha by infinite.animateFloat(initialValue = 0.45f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(500, delayMillis = delayMs), repeatMode = RepeatMode.Reverse), label = "a$idx")
                    Box(modifier = Modifier.size((8 * scale).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)))
                }
                Spacer(Modifier.width(4.dp))
                Text("escribiendo…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DateHeaderPill(timestamp: Long) {
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

private sealed interface MessageListItem {
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
private fun ImageMessageContent(message: Message, isMine: Boolean, onImageClick: (String) -> Unit, onResignImage: (Message) -> Unit, onLongPress: () -> Unit = {}, onDoubleTap: () -> Unit = {}) {
    var resignAttempted by remember(message.id) { mutableStateOf(false) }
    Column {
        Box(modifier = Modifier.defaultMinSize(minWidth = 160.dp, minHeight = 160.dp).sizeIn(maxWidth = 240.dp, maxHeight = 320.dp).clip(SquircleShape()).combinedClickable(onClick = { onImageClick(message.imageUrl) }, onLongClick = { onLongPress() }, onDoubleClick = { onDoubleTap() })) {
            AsyncImage(model = message.imageUrl, contentDescription = "Imagen del chat", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { if (message.imageKey.isNotBlank() && !resignAttempted) { resignAttempted = true; onResignImage(message) } })
        }
        Spacer(Modifier.height(3.dp))
        MessageMetaRow(message = message, isMine = isMine)
    }
}

@Composable
private fun VoiceMessageContent(message: Message, isMine: Boolean, onResignVoice: (Message) -> Unit) {
    var isPlaying by remember(message.id) { mutableStateOf(false) }
    var progress by remember(message.id) { mutableFloatStateOf(0f) }
    var showError by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val player = remember(message.voiceUrl) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(message.voiceUrl))
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
private fun VoiceWaveform(progress: Float, isMine: Boolean) {
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
                    .clip(SquircleShape())
                    .background(if (played) activeColor else idleColor)
            )
        }
    }
}

@Composable
private fun StoryReplyContent(message: Message, isMine: Boolean) {
    val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column {
        Surface(
            shape = SquircleShape(),
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
private fun MessageMetaRow(message: Message, isMine: Boolean, showResignVoice: Boolean = false, onResignVoice: (Message) -> Unit = {}) {
    val metaColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = metaColor,
            fontSize = 9.sp
        )
        if (isMine) {
            Spacer(Modifier.width(3.dp))
            // Estados enviado / entregado / leído — compactos
            val icon: ImageVector
            val tint: Color
            when {
                message.isRead -> { icon = Icons.Filled.DoneAll; tint = MaterialTheme.colorScheme.tertiary }
                message.isDelivered -> { icon = Icons.Filled.DoneAll; tint = metaColor }
                else -> { icon = Icons.Filled.Check; tint = metaColor }
            }
            Icon(icon, contentDescription = if (message.isRead) "Leído" else if (message.isDelivered) "Entregado" else "Enviado", tint = tint, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun UploadBubble(upload: ImageUpload, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(shape = SquircleShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text = when (upload.phase) { ImageUpload.Phase.COMPRESSING -> "Comprimiendo imagen…"; ImageUpload.Phase.UPLOADING -> "Subiendo imagen… ${upload.progress}%"; ImageUpload.Phase.DONE -> "Imagen enviada"; ImageUpload.Phase.FAILED -> "No se pudo enviar la imagen" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (upload.phase) {
                    ImageUpload.Phase.COMPRESSING -> { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp), strokeWidth = 4.dp, color = MaterialTheme.colorScheme.primary) }
                    ImageUpload.Phase.UPLOADING -> { Spacer(Modifier.height(10.dp)); LinearProgressIndicator(progress = { upload.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(SquircleShape()), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainer) }
                    ImageUpload.Phase.FAILED -> { Spacer(Modifier.height(4.dp)); Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onDismiss) { Text("Descartar") }; TextButton(onClick = onRetry) { Text("Reintentar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) } } }
                    ImageUpload.Phase.DONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun VoiceUploadBubble(upload: VoiceUpload, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(shape = SquircleShape(), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(text = if (upload.phase == ImageUpload.Phase.FAILED) "No se pudo enviar la voz" else "Subiendo nota de voz… ${upload.progress}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (upload.phase == ImageUpload.Phase.UPLOADING) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(progress = { upload.progress / 100f }, modifier = Modifier.width(160.dp).height(6.dp).clip(SquircleShape())) }
                    if (upload.phase == ImageUpload.Phase.FAILED) { TextButton(onClick = onDismiss) { Text("Descartar") } }
                }
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean { val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); return df.format(Date(t1)) == df.format(Date(t2)) }
private fun formatDateHeader(timestamp: Long): String { val now = System.currentTimeMillis(); return when { isSameDay(timestamp, now) -> "Hoy"; isSameDay(timestamp, now - 86_400_000) -> "Ayer"; else -> SimpleDateFormat("d 'de' MMMM", Locale.getDefault()).format(Date(timestamp)) } }


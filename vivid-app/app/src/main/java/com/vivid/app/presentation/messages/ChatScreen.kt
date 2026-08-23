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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.R
import com.vivid.app.theme.LocalVividAnimationsEnabled
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
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
import com.vivid.app.ui.components.VividSnackbarHost
import com.vivid.app.ui.components.VividAlertDialog
import com.vivid.app.ui.components.VividDialogTone


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
    val userMessage: String? = viewModel.userMessage.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }

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
        else viewModel.onMicrophonePermissionDenied()
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatId, receiverId, otherUserName) {
        viewModel.openChat(chatId, receiverId, otherUserName)
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeUserMessage(message)
        }
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
        snackbarHost = { VividSnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                        Spacer(Modifier.width(VividSpace.s))
                        Column {
                            Text(
                                otherUserName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (isOtherTyping) {
                                Text(
                                    stringResource(R.string.msg_typing),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    if (canMessage) stringResource(R.string.chat_online) else stringResource(R.string.account_private),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (canMessage) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(VividSpace.l),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = VividExpressiveShapes.SearchBar,
                                color = MaterialTheme.colorScheme.errorContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(VividSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No puedes enviar mensajes", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.height(VividSpace.xs))
                                    Text("Esta cuenta es privada y todavía no la sigues.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }

                    messages.isEmpty() && imageUploads.none { it.phase != ImageUpload.Phase.DONE } && voiceUploads.isEmpty() && !isOtherTyping -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(VividSpace.l), contentAlignment = Alignment.Center) {
                            Surface(shape = VividExpressiveShapes.HeroCard, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(modifier = Modifier.padding(VividSpace.l), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Aún no hay mensajes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(VividSpace.xs))
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
                            contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.m),
                            verticalArrangement = Arrangement.spacedBy(VividSpace.xxs),
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
                                        VoiceUploadBubble(
                                            upload = item.upload,
                                            onRetry = {
                                                viewModel.retryVoiceUpload(chatId, receiverId, item.upload.localId)
                                            },
                                            onDismiss = { viewModel.dismissVoiceUpload(item.upload.localId) }
                                        )
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
                                                    // Long-press SOLO abre la barra de reacciones.
                                                    // Copiar/eliminar viven en el botón "⋯" de esa barra,
                                                    // así la ventana de opciones no tapa las reacciones.
                                                    activeReactionMessageId = message.id
                                                    selectedMessageForOptions = null
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
                            shape = RoundedCornerShape(28.dp),
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = VividSpace.xs),
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
                                Spacer(Modifier.width(VividSpace.xs))
                                OutlinedTextField(
                                    value = messageText,
                                    onValueChange = {
                                        messageText = it
                                        viewModel.onTextChanged(chatId, it)
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Escribe un mensaje…") },
                                    maxLines = 4,
                                    shape = RoundedCornerShape(28.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                )
                                Spacer(modifier = Modifier.width(VividSpace.xs))
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
                                        modifier = Modifier.size(48.dp),
                                        shape = CircleShape,
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.cd_send_message), modifier = Modifier.size(20.dp))
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
                                        Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.cd_voice_message), modifier = Modifier.size(22.dp))
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
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Scrim invisible: tocar fuera cierra la barra de reacciones
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                activeReactionMessageId = null
                                selectedMessageForOptions = null
                            }
                    )
                    // Menú de reacciones expresivo: superficie tonal plana, sin sombras ni bordes
                    Surface(
                        shape = VividExpressiveShapes.HeroCard,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shadowElevation = 0.dp,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 92.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = VividSpace.xs),
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
                            // Botón "⋯": abre copiar/eliminar SIN tapar las reacciones
                            VerticalDivider(
                                modifier = Modifier
                                    .height(28.dp)
                                    .padding(horizontal = VividSpace.xs),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            IconButton(
                                onClick = {
                                    val targetId = activeReactionMessageId
                                    activeReactionMessageId = null
                                    selectedMessageForOptions = messages.firstOrNull { it.id == targetId }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Más opciones (copiar, eliminar)",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
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
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(VividSpace.xs)) {
                Text("Opciones del Mensaje", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = VividSpace.xs))
                when (message.type) {
                    "image" -> {
                        ListItem(headlineContent = { Text("Ver imagen") }, supportingContent = { Text("Imagen adjunta", maxLines = 1) }, leadingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable { viewerImageUrl = message.imageUrl; selectedMessageForOptions = null; activeReactionMessageId = null }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    "voice" -> {
                        ListItem(headlineContent = { Text("Nota de voz") }, supportingContent = { Text(formatVoiceDuration(message.voiceDurationMs), maxLines = 1) }, leadingContent = { Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(VividExpressiveShapes.SmallCard), colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                    else -> {
                        ListItem(headlineContent = { Text("Copiar texto") }, supportingContent = { Text(message.text.take(80), maxLines = 1) }, leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable { clipboardManager.setText(AnnotatedString(message.text)); selectedMessageForOptions = null; activeReactionMessageId = null }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                    }
                }
                if (message.canBeEditedBy(currentUserId)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.msg_edit)) },
                        supportingContent = { Text(stringResource(R.string.msg_edited).take(40), maxLines = 1) },
                        leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable {
                            editingMessage = message
                            selectedMessageForOptions = null
                            activeReactionMessageId = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                if (message.senderId == currentUserId) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.msg_delete),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        supportingContent = { Text(stringResource(R.string.msg_delete_confirm_body)) },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable {
                            pendingDelete = message
                            selectedMessageForOptions = null
                            activeReactionMessageId = null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                Spacer(Modifier.height(VividSpace.m))
            }
        }
    }

    pendingDelete?.let { message ->
        VividAlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.msg_delete_confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.msg_delete_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMessage(chatId, message)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.msg_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
            tone = VividDialogTone.Destructive
        )
    }

    editingMessage?.let { msg ->
        var draft by remember(msg.id) { mutableStateOf(msg.text) }
        VividAlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text(stringResource(R.string.msg_edit_title), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    shape = VividExpressiveShapes.FieldResting
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotBlank() && trimmed != msg.text) {
                            viewModel.editMessage(chatId, msg.id, trimmed)
                        }
                        editingMessage = null
                    },
                    enabled = draft.trim().isNotBlank()
                ) { Text(stringResource(R.string.msg_edit_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
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
                    .padding(bottom = VividSpace.l)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(VividSpace.xxs)
            ) {
                Text(
                    "Adjuntar",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = VividSpace.xxs)
                )
                ListItem(
                    headlineContent = { Text("Enviar foto") },
                    supportingContent = { Text("Desde tu galería") },
                    leadingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable {
                        showAttachMenu = false
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Ver perfil") },
                    supportingContent = { Text("@$otherUserName") },
                    leadingContent = { Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable {
                        showAttachMenu = false
                        if (receiverId.isNotBlank()) onOpenProfile(receiverId)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Copiar chat ID") },
                    supportingContent = { Text("Para soporte o depuración") },
                    leadingContent = { Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clip(VividExpressiveShapes.SmallCard).clickable {
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
                    AsyncImage(model = url, contentDescription = "Imagen del chat", modifier = Modifier.fillMaxWidth().padding(VividSpace.m).graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
                }
                // safeDrawing: overlay inmersivo (el Scaffold no aplica
                // insets en chat), se cubre notch/cutout incluso borderless.
                Surface(modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(horizontal = VividSpace.l, vertical = VividSpace.s), shape = VividExpressiveShapes.SearchBar, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                    Row(modifier = Modifier.padding(horizontal = VividSpace.m, vertical = VividSpace.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VividSpace.m)) {
                        Text(text = "Visor de Imagen", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewerImageUrl = null }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
}


package com.vivid.app.presentation.messages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import android.net.Uri
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    receiverId: String,
    otherUserName: String = "Usuario",
    onBack: () -> Unit = {},
    onOpenProfile: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val clipboardManager = LocalClipboardManager.current
    val messages: List<Message> = viewModel.messages.collectAsState(initial = emptyList<Message>()).value
    val canMessage: Boolean = viewModel.canMessage.collectAsState(initial = true).value

    var messageText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }

    // States for custom Reaction bar & long-press bottom sheet
    var activeReactionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForOptions by remember { mutableStateOf<Message?>(null) }

    // Imágenes: envío con progreso + visor a pantalla completa
    val imageUploads: List<ImageUpload> = viewModel.imageUploads.collectAsState(initial = emptyList()).value
    var viewerImageUrl by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendImage(chatId, receiverId, uri)
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chatId, receiverId, otherUserName) {
        viewModel.openChat(chatId, receiverId, otherUserName)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
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
                            Text(
                                if (canMessage) "• En línea" else "Cuenta privada",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (canMessage) Color(0xFF2ECC71) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
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
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        // Fondo con gradiente sutil y premium
        val bgBrush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "No puedes enviar mensajes",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Esta cuenta es privada y todavía no la sigues.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    messages.isEmpty() && imageUploads.none { it.phase != ImageUpload.Phase.DONE } -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Aún no hay mensajes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Envía el primer mensaje para empezar la conversación real.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        // Mensajes (abajo) + envíos de imagen en progreso (arriba de ellos)
                        val listItems = remember(messages, imageUploads) {
                            buildList {
                                messages.reversed().forEach { add(MessageListItem.ChatMessage(it)) }
                                imageUploads
                                    .filter { it.phase != ImageUpload.Phase.DONE }
                                    .reversed()
                                    .forEach { add(MessageListItem.Upload(it)) }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            reverseLayout = true
                        ) {
                            itemsIndexed(listItems, key = { _, item -> item.key }) { index, item ->
                                when (item) {
                                    is MessageListItem.Upload -> {
                                        UploadBubble(
                                            upload = item.upload,
                                            onRetry = {
                                                viewModel.retryImageUpload(chatId, receiverId, item.upload.localId)
                                            },
                                            onDismiss = {
                                                viewModel.dismissImageUpload(item.upload.localId)
                                            }
                                        )
                                    }

                                    is MessageListItem.ChatMessage -> {
                                        val message = item.message
                                        val isMine = message.senderId == currentUserId

                                        // Determinar si los mensajes adyacentes son del mismo remitente para agrupar
                                        val prevMsg = if (index < messages.size - 1) messages.reversed()[index + 1] else null
                                        val nextMsg = if (index > 0) messages.reversed()[index - 1] else null

                                        val isSameAsPrev = prevMsg?.senderId == message.senderId
                                        val isSameAsNext = nextMsg?.senderId == message.senderId

                                        // Mostrar cabecera de fecha si el día cambia
                                        val showDateHeader = prevMsg == null || !isSameDay(message.timestamp, prevMsg.timestamp)

                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            if (showDateHeader) {
                                                DateHeaderPill(timestamp = message.timestamp)
                                            }

                                            MessageBubble(
                                                message = message,
                                                isMine = isMine,
                                                isGroupStart = !isSameAsPrev,
                                                isGroupEnd = !isSameAsNext,
                                                onLongPress = {
                                                    activeReactionMessageId = message.id
                                                    selectedMessageForOptions = message
                                                },
                                                onDoubleTap = {
                                                    viewModel.reactToMessage(chatId, message.id, "❤️")
                                                },
                                                onImageClick = { url -> viewerImageUrl = url },
                                                onResignImage = { msg ->
                                                    viewModel.refreshImageUrl(msg.id, msg.imageKey)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Barra de Entrada Inferior Flotante Premium
                if (canMessage) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(onClick = { showAttachMenu = true }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Acciones rápidas",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showAttachMenu,
                                    onDismissRequest = { showAttachMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Enviar foto") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Image,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            showAttachMenu = false
                                            imagePicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ver perfil") },
                                        onClick = {
                                            showAttachMenu = false
                                            if (receiverId.isNotBlank()) onOpenProfile(receiverId)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copiar chat ID") },
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(chatId))
                                            showAttachMenu = false
                                        }
                                    )
                                }
                            }
                            
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                                placeholder = { Text("Escribe un mensaje vibrante...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            FilledIconButton(
                                enabled = messageText.isNotBlank() && receiverId.isNotBlank(),
                                onClick = {
                                    val text = messageText.trim()
                                    if (text.isNotBlank()) {
                                        viewModel.sendMessage(chatId, text, receiverId)
                                        messageText = ""
                                    }
                                },
                                modifier = Modifier.size(46.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Send, 
                                    contentDescription = "Enviar",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Barra Flotante de Reacciones rápidas (Material You 3 style)
            AnimatedVisibility(
                visible = activeReactionMessageId != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("❤️", "👍", "😂", "😮", "😢", "🙏", "🔥").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 26.sp,
                                modifier = Modifier
                                    .clickable {
                                        activeReactionMessageId?.let { msgId ->
                                            viewModel.reactToMessage(chatId, msgId, emoji)
                                        }
                                        activeReactionMessageId = null
                                        selectedMessageForOptions = null
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet de Opciones del mensaje
    selectedMessageForOptions?.let { message ->
        ModalBottomSheet(
            onDismissRequest = { 
                selectedMessageForOptions = null 
                activeReactionMessageId = null
            },
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Opciones del Mensaje", 
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (message.type == "image") {
                    ListItem(
                        headlineContent = { Text("Ver imagen", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("Imagen adjunta", maxLines = 1) },
                        leadingContent = { Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                viewerImageUrl = message.imageUrl
                                selectedMessageForOptions = null
                                activeReactionMessageId = null
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("Copiar texto", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text(message.text.take(80), maxLines = 1) },
                        leadingContent = { Icon(Icons.Outlined.FileCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                selectedMessageForOptions = null
                                activeReactionMessageId = null
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                
                if (message.senderId == currentUserId) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ListItem(
                        headlineContent = { Text("Eliminar mensaje", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)) },
                        supportingContent = { Text(if (message.type == "image") "Se borrará la imagen también del servidor." else "Se borrará esta burbuja permanentemente.") },
                        leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                viewModel.deleteMessage(chatId, message)
                                selectedMessageForOptions = null
                                activeReactionMessageId = null
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Visor de imagen a pantalla completa mejorado (Material You 3 & zoom/pan) ──
    viewerImageUrl?.let { url ->
        Dialog(
            onDismissRequest = { viewerImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .clickable { viewerImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                // Imagen con zoom y ajuste fluido
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Imagen del chat en alta definición",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                // Barra superior flotante con Material You 3
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Visor de Imagen",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { viewerImageUrl = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cerrar",
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

@Composable
private fun DateHeaderPill(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            tonalElevation = 1.dp
        ) {
            Text(
                text = formatDateHeader(timestamp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
    }
}

/** Ítems combinados de la lista de mensajes (mensajes + subidas en curso). */
private sealed interface MessageListItem {
    val key: String

    data class ChatMessage(val message: Message) : MessageListItem {
        override val key get() = message.id
    }

    data class Upload(val upload: ImageUpload) : MessageListItem {
        override val key get() = "upload_${upload.localId}"
    }
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
    onResignImage: (Message) -> Unit = {}
) {
    val alignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    
    // Bubble shape with group-merging dynamic corners
    val bubbleShape = if (isMine) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = if (isGroupStart) 18.dp else 4.dp,
            bottomStart = 18.dp,
            bottomEnd = if (isGroupEnd) 18.dp else 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = if (isGroupStart) 18.dp else 4.dp,
            topEnd = 18.dp,
            bottomStart = if (isGroupEnd) 18.dp else 4.dp,
            bottomEnd = 18.dp
        )
    }

    // Genuinos colores premium con gradientes en burbujas del emisor
    val bubbleBackground = if (isMine) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp), // Espaciado ultra compacto de burbujas continuas
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    shape = bubbleShape,
                    tonalElevation = if (isMine) 0.dp else 1.dp,
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onLongPress = { onLongPress() },
                                onDoubleTap = { onDoubleTap() }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .background(bubbleBackground)
                            .padding(
                                horizontal = if (message.type == "image") 4.dp else 14.dp,
                                vertical = if (message.type == "image") 4.dp else 9.dp
                            )
                    ) {
                        if (message.type == "image") {
                            ImageMessageContent(
                                message = message,
                                isMine = isMine,
                                onImageClick = onImageClick,
                                onResignImage = onResignImage,
                                onLongPress = onLongPress,
                                onDoubleTap = onDoubleTap
                            )
                        } else {
                            Column {
                                Text(
                                    text = com.vivid.app.util.SettingsManager.filterOffensiveWords(message.text),
                                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp)
                                )
                                Spacer(Modifier.height(3.dp))
                                MessageMetaRow(message = message, isMine = isMine)
                            }
                        }
                    }
                }

                // Burbuja de reacción flotante si tiene emoji activo
                if (message.reaction.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .offset(
                                x = if (isMine) (-4).dp else 4.dp,
                                y = 12.dp
                            )
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                            .clickable { onDoubleTap() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = message.reaction,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Añadir un espacio adicional en la burbuja de abajo para no colisionar con la reacción flotante
            if (message.reaction.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Contenido de una burbuja de imagen: thumbnail + hora + check de enviado. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMessageContent(
    message: Message,
    isMine: Boolean,
    onImageClick: (String) -> Unit,
    onResignImage: (Message) -> Unit,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    var resignAttempted by remember(message.id) { mutableStateOf(false) }

    Column {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 160.dp, minHeight = 160.dp)
                .sizeIn(maxWidth = 240.dp, maxHeight = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onImageClick(message.imageUrl) },
                    onLongClick = { onLongPress() },
                    onDoubleClick = { onDoubleTap() }
                )
        ) {
            AsyncImage(
                model = message.imageUrl,
                contentDescription = "Imagen del chat",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = {
                    // URL firmada caducada (B2, TTL 7 días): se re-firma con la key
                    if (message.imageKey.isNotBlank() && !resignAttempted) {
                        resignAttempted = true
                        onResignImage(message)
                    }
                }
            )
        }
        Spacer(Modifier.height(3.dp))
        MessageMetaRow(message = message, isMine = isMine)
    }
}

/** Hora + check de enviado (compartido por burbujas de texto e imagen). */
@Composable
private fun MessageMetaRow(message: Message, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = if (isMine) {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            },
            fontSize = 10.sp
        )
        if (isMine) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/** Burbuja temporal que muestra el progreso de una imagen que se está enviando. */
@Composable
private fun UploadBubble(
    upload: ImageUpload,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (upload.phase) {
                            ImageUpload.Phase.COMPRESSING -> "Comprimiendo imagen…"
                            ImageUpload.Phase.UPLOADING -> "Subiendo imagen… ${upload.progress}%"
                            ImageUpload.Phase.DONE -> "Imagen enviada"
                            ImageUpload.Phase.FAILED -> "No se pudo enviar la imagen"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (upload.phase) {
                    ImageUpload.Phase.COMPRESSING -> {
                        Spacer(Modifier.height(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            strokeWidth = 4.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    ImageUpload.Phase.UPLOADING -> {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { upload.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    ImageUpload.Phase.FAILED -> {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Descartar")
                            }
                            TextButton(onClick = onRetry) {
                                Text(
                                    "Reintentar",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    ImageUpload.Phase.DONE -> Unit
                }
            }
        }
    }
}

private fun isSameDay(t1: Long, t2: Long): Boolean {
    val df = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return df.format(Date(t1)) == df.format(Date(t2))
}

private fun formatDateHeader(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return when {
        isSameDay(timestamp, now) -> "Hoy"
        isSameDay(timestamp, now - 86_400_000) -> "Ayer"
        else -> SimpleDateFormat("d 'de' MMMM", Locale.getDefault()).format(Date(timestamp))
    }
}

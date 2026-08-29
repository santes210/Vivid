package com.vivid.app.presentation.messages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.R
import com.vivid.app.ui.components.VividErrorState
import com.vivid.app.ui.components.VividOfflineBannerHost
import com.vivid.app.ui.components.VividSkeletonListItem
import com.vivid.app.util.CrashReporter
import com.vivid.app.util.toUserFacingMessage
import com.vivid.app.util.withNetworkTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vivid.app.theme.VividSpace
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.LocalVividAccents

private const val TAG = "ChatListScreen"

@androidx.compose.runtime.Immutable
data class ChatPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessage: String,
    val lastMessageSenderId: String = "",
    val lastMessageType: String = "text",
    val timestamp: Long,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onChatClick: (chatId: String, otherUserId: String, otherUserName: String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()

    var chats by remember { mutableStateOf<List<ChatPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var presenceByUserId by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    // Caché de presencia: evita re-consultar Firestore en cada cambio del snapshot
    var cachedPresence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var lastPresenceFetch by remember { mutableLongStateOf(0L) }

    // ── Caché Room de la lista de chats: se muestra al instante mientras
    // Firestore refresca en background (el servidor trabaja menos). ──
    val appContext = LocalContext.current.applicationContext
    val chatDao = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            appContext,
            com.vivid.app.di.VividCacheEntryPoint::class.java
        ).database().chatDao()
    }
    val roomChats by chatDao.getAllChats().collectAsState(initial = emptyList())

    // Si Firestore aún no responde pero hay caché, mostrarla de inmediato
    var firestoreLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(roomChats, firestoreLoaded) {
        if (!firestoreLoaded && roomChats.isNotEmpty()) {
            chats = roomChats.map { entity ->
                ChatPreview(
                    chatId = entity.chatId,
                    otherUserId = entity.otherUserId,
                    otherUserName = entity.otherUserName,
                    lastMessage = entity.lastMessage,
                    lastMessageSenderId = entity.lastMessageSenderId,
                    lastMessageType = entity.lastMessageType,
                    timestamp = entity.lastMessageTimestamp,
                    avatarUrl = entity.otherUserAvatar,
                    avatarBase64 = entity.avatarBase64,
                    unreadCount = entity.unreadCount,
                    isOnline = false // la presencia se calcula aparte
                )
            }
            isLoading = false
        }
    }

    // Search & Category states
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Todos, 1 = No leídos, 2 = Activos
    val tabs = listOf("Todos", "No leídos", "Activos")

    DisposableEffect(currentUserId, retryKey) {
        var registration: ListenerRegistration? = null

        if (currentUserId.isBlank()) {
            isLoading = false
            errorMessage = "Inicia sesión para ver tus mensajes."
        } else {
            registration = db.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        CrashReporter.recordNonFatal(TAG, error, "Listener de chats falló")
                        errorMessage = error.toUserFacingMessage("No se pudieron cargar los mensajes.")
                        isLoading = false
                        return@addSnapshotListener
                    }

                    val previews = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val participants = doc.get("participants") as? List<*> ?: emptyList<Any>()
                        val otherUserId = participants
                            .mapNotNull { it as? String }
                            .firstOrNull { it != currentUserId }
                            ?: return@mapNotNull null

                        val participantNames = doc.get("participantNames") as? Map<*, *>
                        val participantAvatars = doc.get("participantAvatars") as? Map<*, *>
                        val participantAvatarBase64s = doc.get("participantAvatarBase64s") as? Map<*, *>
                        val unreadCounts = doc.get("unreadCounts") as? Map<*, *>
                        val unreadCount = (unreadCounts?.get(currentUserId) as? Long)?.toInt()
                            ?: (unreadCounts?.get(currentUserId) as? Int)
                            ?: 0
                        val lastSenderId = doc.getString("lastMessageSenderId")
                            ?: doc.getString("lastSenderId")
                            ?: ""
                        val lastMessageRaw = doc.getString("lastMessage").orEmpty()
                        val lastMessageType = doc.getString("lastMessageType")

                        ChatPreview(
                            chatId = doc.id,
                            otherUserId = otherUserId,
                            otherUserName = participantNames?.get(otherUserId) as? String ?: "Usuario",
                            lastMessage = when {
                                lastMessageRaw.isNotBlank() -> lastMessageRaw
                                lastMessageType == "image" -> "Imagen"
                                else -> ""
                            },
                            lastMessageSenderId = lastSenderId,
                            lastMessageType = lastMessageType ?: "text",
                            timestamp = doc.getLong("lastTimestamp") ?: 0L,
                            avatarUrl = participantAvatars?.get(otherUserId) as? String ?: "",
                            avatarBase64 = participantAvatarBase64s?.get(otherUserId) as? String ?: "",
                            unreadCount = unreadCount,
                            isOnline = presenceByUserId[otherUserId] == true
                        )
                    }

                    // Guardar en caché Room para arranque instantáneo/offline
                    firestoreLoaded = true
                    scope.launch {
                        runCatching {
                            val entities = previews.map { p ->
                                com.vivid.app.data.local.entity.ChatEntity(
                                    chatId = p.chatId,
                                    otherUserId = p.otherUserId,
                                    otherUserName = p.otherUserName,
                                    otherUserAvatar = p.avatarUrl,
                                    lastMessage = p.lastMessage,
                                    lastMessageTimestamp = p.timestamp,
                                    unreadCount = p.unreadCount,
                                    lastMessageSenderId = p.lastMessageSenderId,
                                    lastMessageType = p.lastMessageType,
                                    avatarBase64 = p.avatarBase64,
                                    // Antes quedaba en 0 → isChatCacheFresh()
                                    // siempre reportaba caché vencido.
                                    cachedAt = System.currentTimeMillis()
                                )
                            }
                            chatDao.insertChats(entities)
                        }
                    }

                    // Presencia: 1 query por lote de 10 (whereIn) en vez de 1 query por usuario.
                    // Con caché de 60s para no re-consultar en cada evento del snapshot.
                    val now = System.currentTimeMillis()
                    val neededIds = previews.map { it.otherUserId }.distinct().toSet()
                    val missingIds = neededIds - cachedPresence.keys
                    val cacheStale = now - lastPresenceFetch > 60_000

                    if (missingIds.isNotEmpty() || cacheStale) {
                        scope.launch {
                            val presenceMap = loadPresenceMap(
                                firestore = db,
                                userIds = neededIds.toList()
                            )
                            cachedPresence = presenceMap
                            lastPresenceFetch = System.currentTimeMillis()
                            presenceByUserId = presenceMap
                            chats = previews
                                .map { chat -> chat.copy(isOnline = presenceMap[chat.otherUserId] == true) }
                                .sortedByDescending { it.timestamp }
                        }
                    } else {
                        presenceByUserId = cachedPresence
                        chats = previews
                            .map { chat -> chat.copy(isOnline = cachedPresence[chat.otherUserId] == true) }
                            .sortedByDescending { it.timestamp }
                    }
                    errorMessage = null
                    isLoading = false
                }
        }

        onDispose { registration?.remove() }
    }

    val filteredChats = chats.filter {
        it.otherUserName.contains(searchQuery, ignoreCase = true) ||
                it.lastMessage.contains(searchQuery, ignoreCase = true)
    }.filter {
        when (selectedTab) {
            1 -> it.unreadCount > 0
            2 -> it.isOnline
            else -> true
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "Mensajes",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            "Mensajes en tiempo real",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
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
        ) {
            VividOfflineBannerHost()

            // Buscador moderno estilo Material You
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.chat_list_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VividSpace.m, vertical = 6.dp),
                shape = VividExpressiveShapes.SearchBar,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                singleLine = true
            )

            // Categorías horizontales / Píldoras de Filtro
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                divider = {},
                indicator = {},
                containerColor = Color.Transparent,
                modifier = Modifier.padding(vertical = VividSpace.xs)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val chipBgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                    val chipTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .padding(end = VividSpace.xs)
                            .clip(VividExpressiveShapes.SmallCard)
                            .background(chipBgColor)
                            .clickable { selectedTab = index }
                            .padding(horizontal = VividSpace.m, vertical = VividSpace.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index == 2) {
                                Icon(
                                    Icons.Outlined.Star,
                                    contentDescription = null,
                                    tint = chipTextColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(VividSpace.xxs))
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = chipTextColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(VividSpace.xxs))

            when {
                isLoading -> {
                    // Skeleton con la misma silueta que ChatPreviewCard:
                    // la lista "existe" desde el primer frame; los datos la
                    // rellenan. Igual que feed y explore.
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(VividSpace.xs)
                    ) {
                        repeat(7) { VividSkeletonListItem() }
                    }
                }

                errorMessage != null -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        VividErrorState(
                            title = if (currentUserId.isBlank()) {
                                stringResource(R.string.chat_list_error_signed_out)
                            } else {
                                stringResource(R.string.chat_list_error_loading)
                            },
                            message = errorMessage ?: stringResource(R.string.chat_list_error_loading),
                            onRetry = when {
                                currentUserId.isBlank() -> null
                                else -> ({
                                    errorMessage = null
                                    isLoading = true
                                    retryKey++
                                })
                            }
                        )
                    }
                }

                filteredChats.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Text(
                                stringResource(R.string.chat_list_no_matches, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            EmptyMessagesState()
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = VividSpace.m, vertical = VividSpace.xs),
                        verticalArrangement = Arrangement.spacedBy(VividSpace.s)
                    ) {
                        items(filteredChats, key = { it.chatId }) { chat ->
                            ChatPreviewCard(chat = chat, currentUserId = currentUserId) {
                                onChatClick(chat.chatId, chat.otherUserId, chat.otherUserName)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessagesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(VividSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("¡Chatea con tus amigos!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(VividSpace.xs))
        Text(
            "Visita perfiles de creadores o amigos, toca 'Mensaje' e inicia una conversación.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ChatPreviewCard(chat: ChatPreview, currentUserId: String, onClick: () -> Unit) {
    val isLastMsgMine = chat.lastMessageSenderId == currentUserId
    val unread = chat.unreadCount > 0

    val cardBg = if (unread) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val cardBorder = if (unread) {
        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = VividExpressiveShapes.MediumCard,
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VividSpace.m, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AvatarForChat(chat)
                // Indicador de conexión activo / online
                if (chat.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(LocalVividAccents.current.online)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(VividSpace.m))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chat.otherUserName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(VividSpace.xxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLastMsgMine) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Tú",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(VividSpace.xxs))
                    }
                    Text(
                        text = com.vivid.app.util.SettingsManager.filterOffensiveWords(chat.lastMessage.ifBlank { "Conversación iniciada" }),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                            color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(modifier = Modifier.width(VividSpace.xs))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatChatTime(chat.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (unread) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarForChat(chat: ChatPreview) {
    com.vivid.app.ui.components.UserAvatar(
        imageUrl = chat.avatarUrl,
        name = chat.otherUserName,
        userId = chat.otherUserId,
        size = 56.dp
    )
}

/**
 * Presencia en 1 sola consulta por lote (whereIn admite máx 10 valores).
 * Antes era 1 lectura por usuario por cada cambio del snapshot (N+1).
 */
private suspend fun loadPresenceMap(
    firestore: FirebaseFirestore,
    userIds: List<String>
): Map<String, Boolean> {
    if (userIds.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, Boolean>()
    userIds.distinct().chunked(10).forEach { chunk ->
        runCatching {
            // Timeout por lote: si la red cuelga, la presencia simplemente
            // queda pendiente en vez de bloquear la lista de chats.
            val snap = withNetworkTimeout("chatList.presence") {
                firestore.collection("users")
                    .whereIn("uid", chunk)
                    .get()
                    .await()
            }
            snap.documents.forEach { doc ->
                val uid = doc.getString("uid") ?: doc.id
                val statusEnabled = doc.getBoolean("activityStatusEnabled") ?: true
                val isOnline = doc.getBoolean("isOnline") ?: false
                result[uid] = statusEnabled && isOnline
            }
        }
    }
    return result
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "ahora"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> "${diff / 86_400_000}d"
    }
}

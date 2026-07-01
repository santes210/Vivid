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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessage: String,
    val lastMessageSenderId: String = "",
    val timestamp: Long,
    val avatarUrl: String = "",
    val avatarBase64: String = "",
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

    // Search & Category states
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Todos, 1 = No leídos, 2 = Destacados
    val tabs = listOf("Todos", "No leídos", "Destacados")

    DisposableEffect(currentUserId) {
        var registration: ListenerRegistration? = null

        if (currentUserId.isBlank()) {
            isLoading = false
            errorMessage = "Inicia sesión para ver tus mensajes."
        } else {
            registration = db.collection("chats")
                .whereArrayContains("participants", currentUserId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorMessage = error.message ?: "No se pudieron cargar los mensajes."
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
                        
                        // Determinar si es online simulando o usando actividad si existiera
                        val isOnline = otherUserId.hashCode() % 3 == 0

                        ChatPreview(
                            chatId = doc.id,
                            otherUserId = otherUserId,
                            otherUserName = participantNames?.get(otherUserId) as? String ?: "Usuario",
                            lastMessage = doc.getString("lastMessage").orEmpty(),
                            lastMessageSenderId = doc.getString("lastMessageSenderId").orEmpty(),
                            timestamp = doc.getLong("lastTimestamp") ?: 0L,
                            avatarUrl = participantAvatars?.get(otherUserId) as? String ?: "",
                            avatarBase64 = participantAvatarBase64s?.get(otherUserId) as? String ?: "",
                            isOnline = isOnline
                        )
                    }
                    chats = previews.sortedByDescending { it.timestamp }
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
            1 -> it.lastMessageSenderId.isNotBlank() && it.lastMessageSenderId != currentUserId // "No leídos" (simulados)
            2 -> it.isOnline // "Destacados" (simulado con usuarios online)
            else -> true // Todos
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
                            "Tus conversaciones en tiempo real",
                            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Buscador moderno estilo Material You
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar conversación o amigo...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
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
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val chipBgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    val chipTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(chipBgColor)
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                Spacer(Modifier.width(4.dp))
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

            Spacer(Modifier.height(4.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
                    }
                }

                filteredChats.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Text(
                                "No se encontraron resultados para '$searchQuery'",
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
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
        Text("¡Chatea con tu comunidad!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Visita perfiles de creadores o amigos, toca 'Mensaje' e inicia una plática vibrante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun ChatPreviewCard(chat: ChatPreview, currentUserId: String, onClick: () -> Unit) {
    val isLastMsgMine = chat.lastMessageSenderId == currentUserId
    val unread = !isLastMsgMine && chat.lastMessageSenderId.isNotBlank() // Simulación de no leído
    
    val cardBg = if (unread) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            .background(Color(0xFF2ECC71))
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chat.otherUserName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLastMsgMine) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = "Tú",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
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
            Spacer(modifier = Modifier.width(8.dp))
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
    if (chat.avatarBase64.isNotBlank()) {
        var bitmap by remember(chat.avatarBase64) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(chat.avatarBase64) {
            bitmap = try {
                val bytes = Base64.decode(chat.avatarBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) { null }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = chat.otherUserName,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }
    if (chat.avatarUrl.isNotBlank()) {
        AsyncImage(
            model = chat.avatarUrl,
            contentDescription = chat.otherUserName,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                chat.otherUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
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

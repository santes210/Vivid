package com.vivid.app.presentation.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class ChatPreview(
    val chatId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessage: String,
    val timestamp: Long,
    val avatarUrl: String = ""
)

@Composable
fun ChatListScreen(onChatClick: (chatId: String, otherUserId: String, otherUserName: String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid.orEmpty()

    var chats by remember { mutableStateOf<List<ChatPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

                        ChatPreview(
                            chatId = doc.id,
                            otherUserId = otherUserId,
                            otherUserName = participantNames?.get(otherUserId) as? String ?: "Usuario",
                            lastMessage = doc.getString("lastMessage").orEmpty(),
                            timestamp = doc.getLong("lastTimestamp") ?: 0L,
                            avatarUrl = participantAvatars?.get(otherUserId) as? String ?: ""
                        )
                    }
                    chats = previews.sortedByDescending { it.timestamp }
                    errorMessage = null
                    isLoading = false
                }
        }

        onDispose { registration?.remove() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Mensajes") })

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "Error", color = MaterialTheme.colorScheme.error)
                }
            }
            chats.isEmpty() -> {
                EmptyMessagesState()
            }
            else -> {
                LazyColumn {
                    items(chats, key = { it.chatId }) { chat ->
                        ChatPreviewItem(chat = chat) {
                            onChatClick(chat.chatId, chat.otherUserId, chat.otherUserName)
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
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Email,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Sin mensajes todavía", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Busca una persona y toca Mensaje para iniciar una conversación real.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChatPreviewItem(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chat.avatarUrl.isNotBlank()) {
            AsyncImage(
                model = chat.avatarUrl,
                contentDescription = chat.otherUserName,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chat.otherUserName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.otherUserName, style = MaterialTheme.typography.titleMedium)
            Text(
                chat.lastMessage.ifBlank { "Conversación iniciada" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = formatChatTime(chat.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

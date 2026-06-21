package com.vivid.app.presentation.messages

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
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class ChatPreview(
    val chatId: String,
    val otherUserName: String,
    val lastMessage: String,
    val timestamp: Long,
    val avatarUrl: String = "https://picsum.photos/id/1005/48/48"
)

@Composable
fun ChatListScreen(onChatClick: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserId = auth.currentUser?.uid ?: "demo-user"

    var chats by remember { mutableStateOf<List<ChatPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Demo data + real-time simulation
    LaunchedEffect(Unit) {
        // In production: use snapshotListener on user's chats collection
        chats = listOf(
            ChatPreview("chat1_ana", "ana_vivid", "¡Qué foto tan increíble!", System.currentTimeMillis() - 3600000, "https://picsum.photos/id/1009/48/48"),
            ChatPreview("chat2_carlos", "carlos_vivid", "Nos vemos mañana", System.currentTimeMillis() - 7200000, "https://picsum.photos/id/1012/48/48"),
            ChatPreview("chat3_lucia", "lucia_vivid", "Me encanta tu último reel", System.currentTimeMillis() - 86400000, "https://picsum.photos/id/1006/48/48")
        )
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Mensajes") })

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn {
                items(chats) { chat ->
                    ChatPreviewItem(chat = chat) {
                        onChatClick(chat.chatId)
                    }
                }
            }
        }
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
        AsyncImage(
            model = chat.avatarUrl,
            contentDescription = chat.otherUserName,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(chat.otherUserName, style = MaterialTheme.typography.titleMedium)
            Text(
                chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = if (System.currentTimeMillis() - chat.timestamp < 86400000) "hoy" else "ayer",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
package com.vivid.app.domain.repository

import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.presentation.messages.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private val currentUserId get() = auth.currentUser?.uid ?: "demo-user"

    fun getChatsFlow(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    suspend fun createOrGetChat(otherUserId: String, otherUserName: String, avatarUrl: String): String {
        val chatId = listOf(currentUserId, otherUserId).sorted().joinToString("_")
        
        val chat = ChatEntity(
            chatId = chatId,
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            otherUserAvatar = avatarUrl,
            lastMessage = "",
            lastMessageTimestamp = System.currentTimeMillis()
        )
        
        chatDao.insertOrUpdateChat(chat)
        
        // Create in Firestore if not exists
        firestore.collection("chats").document(chatId).set(
            mapOf(
                "participants" to listOf(currentUserId, otherUserId),
                "lastMessage" to "",
                "lastTimestamp" to System.currentTimeMillis()
            )
        )
        
        return chatId
    }

    fun getMessagesFlow(chatId: String): Flow<List<Message>> {
        return messageDao.getMessagesForChat(chatId).map { entities ->
            entities.map { entity ->
                Message(
                    id = entity.id,
                    text = entity.text,
                    senderId = entity.senderId,
                    timestamp = entity.timestamp,
                    isRead = entity.isRead
                )
            }
        }
    }

    suspend fun sendMessage(chatId: String, text: String, receiverId: String) {
        val messageId = System.currentTimeMillis().toString()
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        
        // Save locally
        messageDao.insertMessage(message)
        
        // Update chat last message
        val chat = ChatEntity(
            chatId = chatId,
            otherUserId = receiverId,
            otherUserName = "", // Will be updated from Firestore
            otherUserAvatar = "",
            lastMessage = text,
            lastMessageTimestamp = System.currentTimeMillis()
        )
        chatDao.insertOrUpdateChat(chat)
        
        // Send to Firestore
        try {
            firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .set(
                    mapOf(
                        "text" to text,
                        "senderId" to currentUserId,
                        "timestamp" to System.currentTimeMillis(),
                        "type" to "text"
                    )
                ).await()
            
            // Update chat metadata
            firestore.collection("chats").document(chatId).update(
                "lastMessage", text,
                "lastTimestamp", System.currentTimeMillis()
            )
        } catch (e: Exception) {
            // Will sync later when online
        }
    }

    // Real-time listener for new messages
    fun listenToMessages(chatId: String, onNewMessage: (Message) -> Unit) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val msg = Message(
                            id = change.document.id,
                            text = data["text"] as? String ?: "",
                            senderId = data["senderId"] as? String ?: "",
                            timestamp = (data["timestamp"] as? Long) ?: 0L
                        )
                        onNewMessage(msg)
                    }
                }
            }
    }
}
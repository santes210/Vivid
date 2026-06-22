package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.presentation.messages.Message
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

    private val currentUserId get() = auth.currentUser?.uid.orEmpty()

    fun getChatsFlow(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    suspend fun createOrGetChat(otherUserId: String, otherUserName: String, avatarUrl: String, avatarBase64: String = ""): String {
        val chatId = buildChatId(currentUserId, otherUserId)
        ensureChatExists(chatId, otherUserId, otherUserName, avatarUrl, avatarBase64)
        return chatId
    }

    suspend fun ensureChatExists(chatId: String, otherUserId: String, otherUserName: String, avatarUrl: String, avatarBase64: String = "") {
        if (currentUserId.isBlank() || otherUserId.isBlank()) return

        val currentUser = auth.currentUser
        val currentUserName = currentUser?.displayName
            ?: currentUser?.email?.substringBefore("@")
            ?: "Usuario"
        val currentAvatar = currentUser?.photoUrl?.toString().orEmpty()
        val currentBase64 = ""

        val now = System.currentTimeMillis()
        chatDao.insertOrUpdateChat(
            ChatEntity(
                chatId = chatId,
                otherUserId = otherUserId,
                otherUserName = otherUserName,
                otherUserAvatar = avatarUrl,
                lastMessage = "",
                lastMessageTimestamp = now
            )
        )

        firestore.collection("chats").document(chatId).set(
            mapOf(
                "participants" to listOf(currentUserId, otherUserId),
                "participantNames" to mapOf(
                    currentUserId to currentUserName,
                    otherUserId to otherUserName
                ),
                "participantAvatars" to mapOf(
                    currentUserId to currentAvatar,
                    otherUserId to avatarUrl
                ),
                "participantAvatarBase64s" to mapOf(
                    currentUserId to currentBase64,
                    otherUserId to avatarBase64
                ),
                "createdAt" to now,
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).await()
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
            }.sortedBy { it.timestamp }
        }
    }

    suspend fun sendMessage(chatId: String, text: String, receiverId: String) {
        if (currentUserId.isBlank() || receiverId.isBlank() || text.isBlank()) return

        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            text = text,
            timestamp = now
        )

        messageDao.insertMessage(message)

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .set(
                mapOf(
                    "text" to text,
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "timestamp" to now,
                    "type" to "text",
                    "isRead" to false
                )
            ).await()

        firestore.collection("chats").document(chatId).set(
            mapOf(
                "participants" to listOf(currentUserId, receiverId),
                "lastMessage" to text,
                "lastSenderId" to currentUserId,
                "lastTimestamp" to now,
                "updatedAt" to now
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        messageDao.deleteMessage(messageId)
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .delete()
            .await()

        val latestRemaining = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        firestore.collection("chats").document(chatId).set(
            mapOf(
                "lastMessage" to latestRemaining?.getString("text").orEmpty(),
                "lastSenderId" to latestRemaining?.getString("senderId").orEmpty(),
                "lastTimestamp" to (latestRemaining?.getLong("timestamp") ?: System.currentTimeMillis()),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    fun listenToMessages(chatId: String, onMessageEvent: (MessageChange) -> Unit) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    val timestamp = change.document.getLong("timestamp") ?: 0L
                    val msg = Message(
                        id = change.document.id,
                        text = change.document.getString("text").orEmpty(),
                        senderId = change.document.getString("senderId").orEmpty(),
                        timestamp = timestamp,
                        isRead = change.document.getBoolean("isRead") ?: false
                    )
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> onMessageEvent(MessageChange.Upsert(msg))
                        DocumentChange.Type.REMOVED -> onMessageEvent(MessageChange.Removed(msg.id))
                    }
                }
            }
    }

    sealed class MessageChange {
        data class Upsert(val message: Message) : MessageChange()
        data class Removed(val messageId: String) : MessageChange()
    }

    companion object {
        fun buildChatId(userA: String, userB: String): String =
            listOf(userA, userB).sorted().joinToString("_")
    }
}

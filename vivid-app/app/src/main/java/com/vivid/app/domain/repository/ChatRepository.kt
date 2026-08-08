package com.vivid.app.domain.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.presentation.messages.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider
) {

    private val currentUserId get() = auth.currentUser?.uid.orEmpty()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        // NOTA: no se incluye unreadCounts aquí a propósito. Con merge(), reescribirlo
        // al abrir el chat borraría los no-leídos del OTRO participante. Los contadores
        // solo se tocan con FieldValue.increment (al enviar) y markChatAsRead (al abrir).
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
                    isRead = entity.isRead,
                    isDelivered = entity.isDelivered,
                    type = entity.type,
                    imageUrl = entity.imageUrl,
                    imageKey = entity.imageKey,
                    voiceUrl = entity.voiceUrl,
                    voiceKey = entity.voiceKey,
                    voiceDurationMs = entity.voiceDurationMs,
                    replyToStoryId = entity.replyToStoryId
                )
            }.sortedBy { it.timestamp }
        }
    }

    suspend fun sendMessage(chatId: String, text: String, receiverId: String, replyToStoryId: String = "") {
        if (currentUserId.isBlank() || receiverId.isBlank() || text.isBlank()) return

        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
        val type = if (replyToStoryId.isNotBlank()) "story_reply" else "text"
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            text = text,
            timestamp = now,
            type = type,
            replyToStoryId = replyToStoryId
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
                    "type" to type,
                    "isRead" to false,
                    "isDelivered" to false,
                    "replyToStoryId" to replyToStoryId
                )
            ).await()

        val preview = when (type) {
            "story_reply" -> "↳ Respondió a tu story"
            else -> text
        }
        updateChatPreview(chatId, receiverId, lastMessage = preview, lastMessageType = type, now)
    }

    /**
     * Envía un mensaje de imagen. El binario YA está en B2; aquí solo se guarda
     * la URL firmada + la key remota en Firestore (documento ligero, sin Base64).
     */
    suspend fun sendImageMessage(chatId: String, receiverId: String, imageUrl: String, imageKey: String) {
        if (currentUserId.isBlank() || receiverId.isBlank() || imageUrl.isBlank()) return

        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            text = "",
            timestamp = now,
            type = "image",
            imageUrl = imageUrl,
            imageKey = imageKey
        )

        messageDao.insertMessage(message)

        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .set(
                mapOf(
                    "text" to "",
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "timestamp" to now,
                    "type" to "image",
                    "isRead" to false,
                    "isDelivered" to false,
                    "imageUrl" to imageUrl,
                    "imageKey" to imageKey
                )
            ).await()

        updateChatPreview(chatId, receiverId, lastMessage = "Imagen", lastMessageType = "image", now)
    }

    /**
     * Envía una nota de voz. El audio YA está en B2; guarda URL + duración.
     */
    suspend fun sendVoiceMessage(chatId: String, receiverId: String, voiceUrl: String, voiceKey: String, durationMs: Long) {
        if (currentUserId.isBlank() || receiverId.isBlank() || voiceUrl.isBlank()) return
        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = currentUserId,
            text = "",
            timestamp = now,
            type = "voice",
            voiceUrl = voiceUrl,
            voiceKey = voiceKey,
            voiceDurationMs = durationMs
        )
        messageDao.insertMessage(message)
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .set(
                mapOf(
                    "text" to "",
                    "senderId" to currentUserId,
                    "receiverId" to receiverId,
                    "timestamp" to now,
                    "type" to "voice",
                    "isRead" to false,
                    "isDelivered" to false,
                    "voiceUrl" to voiceUrl,
                    "voiceKey" to voiceKey,
                    "voiceDurationMs" to durationMs
                )
            ).await()
        updateChatPreview(chatId, receiverId, lastMessage = "🎙️ Nota de voz ${formatDuration(durationMs)}", lastMessageType = "voice", now)
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    private suspend fun updateChatPreview(
        chatId: String,
        receiverId: String,
        lastMessage: String,
        lastMessageType: String,
        now: Long
    ) {
        // Actualizar preview del chat: participantes, último mensaje, timestamps
        // y contadores de no leídos usando dot-notation para evitar sobrescribir
        val chatRef = firestore.collection("chats").document(chatId)
        try {
            chatRef.update(
                mapOf(
                    "participants" to listOf(currentUserId, receiverId),
                    "lastMessage" to lastMessage,
                    "lastMessageType" to lastMessageType,
                    "lastSenderId" to currentUserId,
                    "lastMessageSenderId" to currentUserId,
                    "lastTimestamp" to now,
                    "updatedAt" to now,
                    "unreadCounts.$currentUserId" to 0,
                    "unreadCounts.$receiverId" to FieldValue.increment(1)
                )
            ).await()
        } catch (e: Exception) {
            // Si el documento no existe aún, crearlo con set merge
            chatRef.set(
                mapOf(
                    "participants" to listOf(currentUserId, receiverId),
                    "lastMessage" to lastMessage,
                    "lastMessageType" to lastMessageType,
                    "lastSenderId" to currentUserId,
                    "lastMessageSenderId" to currentUserId,
                    "lastTimestamp" to now,
                    "unreadCounts" to mapOf(
                        currentUserId to 0,
                        receiverId to 1
                    ),
                    "updatedAt" to now,
                    "createdAt" to now
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun deleteMessage(chatId: String, message: Message) {
        // 1. Borrar el binario de B2 (best effort, no bloquea el borrado local)
        if (message.type == "image" && message.imageKey.isNotBlank()) {
            runCatching { storage.deleteFile(message.imageKey) }
        }
        if (message.type == "voice" && message.voiceKey.isNotBlank()) {
            runCatching { storage.deleteFile(message.voiceKey) }
        }

        // 2. Borrar local + Firestore
        messageDao.deleteMessage(message.id)
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(message.id)
            .delete()
            .await()

        // 3. Recalcular la preview del chat con el último mensaje restante
        val latestRemaining = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        val latestSenderId = latestRemaining?.getString("senderId").orEmpty()
        val latestType = latestRemaining?.getString("type") ?: "text"
        val latestText = latestRemaining?.getString("text").orEmpty()
        val lastMessageDisplay = when (latestType) {
            "image" -> "Imagen"
            "voice" -> "🎙️ Nota de voz"
            "story_reply" -> "↳ Respondió a tu story"
            else -> latestText
        }

        firestore.collection("chats").document(chatId).set(
            mapOf(
                "lastMessage" to lastMessageDisplay,
                "lastMessageType" to latestType,
                "lastSenderId" to latestSenderId,
                "lastMessageSenderId" to latestSenderId,
                "lastTimestamp" to (latestRemaining?.getLong("timestamp") ?: System.currentTimeMillis()),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun markChatAsRead(chatId: String) {
        if (currentUserId.isBlank() || chatId.isBlank()) return
        try {
            firestore.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "unreadCounts.$currentUserId" to 0,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
        } catch (_: Exception) {
            firestore.collection("chats").document(chatId)
                .set(
                    mapOf(
                        "unreadCounts" to mapOf(currentUserId to 0),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
        }
        // Marca todos los mensajes recibidos como leídos (read receipts)
        markMessagesAsRead(chatId)
    }

    /**
     * Marca como leídos (isRead=true, isDelivered=true) todos los mensajes donde
     * el receiver es el usuario actual y aún no están leídos.
     */
    suspend fun markMessagesAsRead(chatId: String) {
        if (currentUserId.isBlank() || chatId.isBlank()) return
        try {
            val unread = firestore.collection("chats").document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("isRead", false)
                .get().await()
            if (unread.isEmpty) return
            val batch = firestore.batch()
            unread.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("isRead" to true, "isDelivered" to true))
            }
            batch.commit().await()
            // Update local Room
            unread.documents.forEach { doc ->
                repositoryScope.launch {
                    val id = doc.id
                    val text = doc.getString("text").orEmpty()
                    val sender = doc.getString("senderId").orEmpty()
                    val ts = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    val type = doc.getString("type") ?: "text"
                    messageDao.insertMessage(
                        MessageEntity(
                            id = id,
                            chatId = chatId,
                            senderId = sender,
                            text = text,
                            timestamp = ts,
                            isRead = true,
                            isDelivered = true,
                            type = type,
                            imageUrl = doc.getString("imageUrl").orEmpty(),
                            imageKey = doc.getString("imageKey").orEmpty(),
                            voiceUrl = doc.getString("voiceUrl").orEmpty(),
                            voiceKey = doc.getString("voiceKey").orEmpty(),
                            voiceDurationMs = doc.getLong("voiceDurationMs") ?: 0L,
                            replyToStoryId = doc.getString("replyToStoryId").orEmpty()
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Typing indicator — guarda timestamp del usuario que escribe.
     * El campo se llama `typing` : Map<userId, timestampMillis>.
     * Se borra después de 5s de inactividad (el cliente hace remove).
     */
    suspend fun setTyping(chatId: String, isTyping: Boolean) {
        if (currentUserId.isBlank() || chatId.isBlank()) return
        try {
            val ref = firestore.collection("chats").document(chatId)
            if (isTyping) {
                ref.set(
                    mapOf("typing" to mapOf(currentUserId to System.currentTimeMillis())),
                    SetOptions.merge()
                ).await()
            } else {
                ref.update("typing.$currentUserId", FieldValue.delete()).await()
            }
        } catch (_: Exception) {}
    }

    /**
     * Escucha el mapa typing del chat.
     */
    fun listenTyping(chatId: String, onTypingChanged: (Map<String, Long>) -> Unit): ListenerRegistration {
        return firestore.collection("chats").document(chatId)
            .addSnapshotListener { snap, _ ->
                val typing = snap?.get("typing") as? Map<String, Long> ?: run {
                    val raw = snap?.get("typing") as? Map<*, *>
                    raw?.mapNotNull { (k, v) ->
                        val key = k as? String ?: return@mapNotNull null
                        val ts = (v as? Long) ?: (v as? Number)?.toLong() ?: return@mapNotNull null
                        key to ts
                    }?.toMap() ?: emptyMap()
                }
                onTypingChanged(typing)
            }
    }

    /**
     * Marca un mensaje individual como entregado (cuando el receptor lo recibió)
     */
    suspend fun markMessageDelivered(chatId: String, messageId: String) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("isDelivered", true).await()
        } catch (_: Exception) {}
    }

    /**
     * Escucha los mensajes de un chat en tiempo real.
     * Ahora devuelve el [ListenerRegistration] para que el ViewModel pueda
     * removerlo al salir (antes el listener quedaba vivo para siempre: leak).
     */
    fun listenToMessages(chatId: String, onMessageEvent: (MessageChange) -> Unit): ListenerRegistration {
        return firestore.collection("chats")
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
                        isRead = change.document.getBoolean("isRead") ?: false,
                        isDelivered = change.document.getBoolean("isDelivered") ?: false,
                        reaction = change.document.getString("reaction").orEmpty(),
                        type = change.document.getString("type") ?: "text",
                        imageUrl = change.document.getString("imageUrl").orEmpty(),
                        imageKey = change.document.getString("imageKey").orEmpty(),
                        voiceUrl = change.document.getString("voiceUrl").orEmpty(),
                        voiceKey = change.document.getString("voiceKey").orEmpty(),
                        voiceDurationMs = change.document.getLong("voiceDurationMs") ?: 0L,
                        replyToStoryId = change.document.getString("replyToStoryId").orEmpty()
                    )

                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            repositoryScope.launch {
                                messageDao.insertMessage(
                                    MessageEntity(
                                        id = msg.id,
                                        chatId = chatId,
                                        senderId = msg.senderId,
                                        text = msg.text,
                                        timestamp = msg.timestamp,
                                        isRead = msg.isRead,
                                        isDelivered = msg.isDelivered,
                                        type = msg.type,
                                        imageUrl = msg.imageUrl,
                                        imageKey = msg.imageKey,
                                        voiceUrl = msg.voiceUrl,
                                        voiceKey = msg.voiceKey,
                                        voiceDurationMs = msg.voiceDurationMs,
                                        replyToStoryId = msg.replyToStoryId
                                    )
                                )
                            }
                            onMessageEvent(MessageChange.Upsert(msg))
                        }

                        DocumentChange.Type.REMOVED -> {
                            repositoryScope.launch {
                                messageDao.deleteMessage(msg.id)
                            }
                            onMessageEvent(MessageChange.Removed(msg.id))
                        }
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

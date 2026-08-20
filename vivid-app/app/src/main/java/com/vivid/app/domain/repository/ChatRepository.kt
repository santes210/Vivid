package com.vivid.app.domain.repository

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
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
import com.vivid.app.util.PushSender
import com.vivid.app.util.VideoCacheManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: StorageProvider,
    private val imageLoader: ImageLoader,
    @ApplicationContext private val appContext: Context
) {

    private val currentUserId get() = auth.currentUser?.uid.orEmpty()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getChatsFlow(): Flow<List<ChatEntity>> = chatDao.getAllChats()

    suspend fun createOrGetChat(
        otherUserId: String,
        otherUserName: String,
        avatarUrl: String,
        avatarBase64: String = ""
    ): String {
        val chatId = buildChatId(currentUserId, otherUserId)
        ensureChatExists(chatId, otherUserId, otherUserName, avatarUrl, avatarBase64)
        return chatId
    }

    suspend fun ensureChatExists(
        chatId: String,
        otherUserId: String,
        otherUserName: String,
        avatarUrl: String,
        avatarBase64: String = ""
    ) {
        val senderId = currentUserId
        if (senderId.isBlank() || otherUserId.isBlank() || chatId.isBlank()) return

        require(chatId == buildChatId(senderId, otherUserId)) {
            "El identificador de la conversación no coincide con sus participantes"
        }

        val currentUser = auth.currentUser
        val currentUserName = currentUser?.displayName
            ?: currentUser?.email?.substringBefore("@")
            ?: "Usuario"

        val currentAvatar = currentUser?.photoUrl?.toString().orEmpty()
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

        val chatRef = firestore.collection("chats").document(chatId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(chatRef)

            val profileFields = mapOf(
                "participantNames" to mapOf(
                    senderId to currentUserName,
                    otherUserId to otherUserName
                ),
                "participantAvatars" to mapOf(
                    senderId to currentAvatar,
                    otherUserId to avatarUrl
                ),
                "participantAvatarBase64s" to mapOf(
                    senderId to "",
                    otherUserId to avatarBase64
                ),
                "updatedAt" to now
            )

            if (snapshot.exists()) {
                val participants = snapshot.get("participants") as? List<*>

                check(
                    participants != null &&
                        participants.contains(senderId) &&
                        participants.contains(otherUserId)
                ) {
                    "La conversación existente no pertenece a estos usuarios"
                }

                transaction.set(chatRef, profileFields, SetOptions.merge())
            } else {
                transaction.set(
                    chatRef,
                    profileFields + mapOf(
                        "participants" to listOf(senderId, otherUserId),
                        "createdAt" to now,
                        "unreadCounts" to mapOf(senderId to 0, otherUserId to 0)
                    )
                )
            }

            null
        }.await()
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
                    reaction = entity.reaction,
                    type = entity.type,
                    imageUrl = entity.imageUrl,
                    imageKey = entity.imageKey,
                    voiceUrl = entity.voiceUrl,
                    voiceKey = entity.voiceKey,
                    voiceDurationMs = entity.voiceDurationMs,
                    replyToStoryId = entity.replyToStoryId,
                    lastEditedAt = entity.lastEditedAt
                )
            }.sortedBy { it.timestamp }
        }
    }

    suspend fun getLastCachedTimestamp(chatId: String): Long? =
        messageDao.getMaxTimestamp(chatId)

    private fun documentToMessage(
        doc: DocumentSnapshot
    ): Message = Message(
        id = doc.id,
        text = doc.getString("text").orEmpty(),
        senderId = doc.getString("senderId").orEmpty(),
        timestamp = doc.getLong("timestamp") ?: 0L,
        isRead = doc.getBoolean("isRead") ?: false,
        isDelivered = doc.getBoolean("isDelivered") ?: false,
        reaction = doc.getString("reaction").orEmpty(),
        type = doc.getString("type") ?: "text",
        imageUrl = doc.getString("imageUrl").orEmpty(),
        imageKey = doc.getString("imageKey").orEmpty(),
        voiceUrl = doc.getString("voiceUrl").orEmpty(),
        voiceKey = doc.getString("voiceKey").orEmpty(),
        voiceDurationMs = doc.getLong("voiceDurationMs") ?: 0L,
        replyToStoryId = doc.getString("replyToStoryId").orEmpty(),
        lastEditedAt = doc.getLong("lastEditedAt") ?: 0L
    )

    private fun Message.toEntity(chatId: String): MessageEntity =
        MessageEntity(
            id = id,
            chatId = chatId,
            senderId = senderId,
            text = text,
            timestamp = timestamp,
            isRead = isRead,
            isDelivered = isDelivered,
            type = type,
            imageUrl = imageUrl,
            imageKey = imageKey,
            voiceUrl = voiceUrl,
            voiceKey = voiceKey,
            voiceDurationMs = voiceDurationMs,
            replyToStoryId = replyToStoryId,
            reaction = reaction,
            lastEditedAt = lastEditedAt
        )

    private fun persistMessage(chatId: String, msg: Message) {
        repositoryScope.launch {
            messageDao.insertMessage(msg.toEntity(chatId))
        }
    }

    suspend fun backfillMessages(
        chatId: String,
        pageSize: Int = 300,
        maxPages: Int = 10
    ) {
        if (chatId.isBlank()) return

        val entities = mutableListOf<MessageEntity>()
        var lastDoc: DocumentSnapshot? = null

        for (page in 0 until maxPages) {
            var query = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (lastDoc != null) {
                query = query.startAfter(lastDoc)
            }

            val snapshot = query.limit(pageSize.toLong()).get().await()

            if (snapshot.documents.isEmpty()) break

            snapshot.documents.forEach { doc ->
                val msg = documentToMessage(doc)
                if (msg.id.isBlank()) return@forEach
                entities += msg.toEntity(chatId)
            }

            if (snapshot.documents.size < pageSize) break
            lastDoc = snapshot.documents.last()
        }

        if (entities.isNotEmpty()) {
            messageDao.insertMessages(entities)
        }
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        receiverId: String,
        replyToStoryId: String = ""
    ) {
        val senderId = currentUserId
        if (senderId.isBlank() || receiverId.isBlank() || text.isBlank()) return

        val now = System.currentTimeMillis()

        val messageId = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()
            .id

        val type = if (replyToStoryId.isNotBlank()) "story_reply" else "text"

        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = senderId,
            text = text,
            timestamp = now,
            type = type,
            replyToStoryId = replyToStoryId
        )

        val messageData = mapOf(
            "text" to text,
            "senderId" to senderId,
            "receiverId" to receiverId,
            "timestamp" to now,
            "type" to type,
            "isRead" to false,
            "isDelivered" to false,
            "replyToStoryId" to replyToStoryId
        )

        val preview = if (type == "story_reply") {
            "↳ Respondió a tu story"
        } else {
            text
        }

        persistOutgoingMessage(
            chatId = chatId,
            receiverId = receiverId,
            messageId = messageId,
            messageData = messageData,
            lastMessage = preview,
            lastMessageType = type,
            now = now
        )

        repositoryScope.launch {
            messageDao.insertMessage(message)
        }
    }

    suspend fun sendImageMessage(
        chatId: String,
        receiverId: String,
        imageUrl: String,
        imageKey: String
    ) {
        val senderId = currentUserId

        if (
            senderId.isBlank() ||
            receiverId.isBlank() ||
            imageUrl.isBlank() ||
            imageKey.isBlank()
        ) return

        val now = System.currentTimeMillis()

        val messageId = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()
            .id

        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = senderId,
            text = "",
            timestamp = now,
            type = "image",
            imageUrl = imageUrl,
            imageKey = imageKey
        )

        val messageData = mapOf(
            "text" to "",
            "senderId" to senderId,
            "receiverId" to receiverId,
            "timestamp" to now,
            "type" to "image",
            "isRead" to false,
            "isDelivered" to false,
            "imageUrl" to imageUrl,
            "imageKey" to imageKey
        )

        persistOutgoingMessage(
            chatId = chatId,
            receiverId = receiverId,
            messageId = messageId,
            messageData = messageData,
            lastMessage = "Imagen",
            lastMessageType = "image",
            now = now
        )

        repositoryScope.launch {
            messageDao.insertMessage(message)
        }
    }

    suspend fun sendVoiceMessage(
        chatId: String,
        receiverId: String,
        voiceUrl: String,
        voiceKey: String,
        durationMs: Long
    ) {
        val senderId = currentUserId

        if (
            senderId.isBlank() ||
            receiverId.isBlank() ||
            voiceUrl.isBlank() ||
            voiceKey.isBlank()
        ) return

        val now = System.currentTimeMillis()

        val messageId = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document()
            .id

        val message = MessageEntity(
            id = messageId,
            chatId = chatId,
            senderId = senderId,
            text = "",
            timestamp = now,
            type = "voice",
            voiceUrl = voiceUrl,
            voiceKey = voiceKey,
            voiceDurationMs = durationMs
        )

        val messageData = mapOf(
            "text" to "",
            "senderId" to senderId,
            "receiverId" to receiverId,
            "timestamp" to now,
            "type" to "voice",
            "isRead" to false,
            "isDelivered" to false,
            "voiceUrl" to voiceUrl,
            "voiceKey" to voiceKey,
            "voiceDurationMs" to durationMs
        )

        persistOutgoingMessage(
            chatId = chatId,
            receiverId = receiverId,
            messageId = messageId,
            messageData = messageData,
            lastMessage = "🎙️ Nota de voz ${formatDuration(durationMs)}",
            lastMessageType = "voice",
            now = now
        )

        repositoryScope.launch {
            messageDao.insertMessage(message)
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000).toInt()
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private suspend fun persistOutgoingMessage(
        chatId: String,
        receiverId: String,
        messageId: String,
        messageData: Map<String, Any>,
        lastMessage: String,
        lastMessageType: String,
        now: Long
    ) {
        val senderId = currentUserId

        require(senderId.isNotBlank() && receiverId.isNotBlank()) {
            "Sesión de chat no válida"
        }

        require(chatId == buildChatId(senderId, receiverId)) {
            "El identificador de la conversación no coincide con sus participantes"
        }

        val chatRef = firestore.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document(messageId)

        firestore.runTransaction { transaction ->
            val chatSnapshot = transaction.get(chatRef)

            if (chatSnapshot.exists()) {
                val participants = chatSnapshot.get("participants") as? List<*>

                check(
                    participants != null &&
                        participants.size == 2 &&
                        participants.contains(senderId) &&
                        participants.contains(receiverId)
                ) {
                    "La conversación no tiene participantes válidos"
                }

                val previewUpdate = mutableMapOf<String, Any>(
                    "lastMessage" to lastMessage,
                    "lastMessageType" to lastMessageType,
                    "lastSenderId" to senderId,
                    "lastMessageSenderId" to senderId,
                    "lastTimestamp" to now,
                    "updatedAt" to now,
                    "unreadCounts.$senderId" to 0
                )

                if (receiverId != senderId) {
                    previewUpdate["unreadCounts.$receiverId"] =
                        FieldValue.increment(1)
                }

                transaction.update(chatRef, previewUpdate)
            } else {
                transaction.set(
                    chatRef,
                    mapOf(
                        "participants" to listOf(senderId, receiverId),
                        "lastMessage" to lastMessage,
                        "lastMessageType" to lastMessageType,
                        "lastSenderId" to senderId,
                        "lastMessageSenderId" to senderId,
                        "lastTimestamp" to now,
                        "unreadCounts" to if (receiverId == senderId) {
                            mapOf(senderId to 0)
                        } else {
                            mapOf(senderId to 0, receiverId to 1)
                        },
                        "updatedAt" to now,
                        "createdAt" to now
                    )
                )
            }

            transaction.set(messageRef, messageData)
            null
        }.await()

        PushSender.message(chatId, messageId)
    }

    /**
     * Borra un mensaje propio, su caché privada y el archivo remoto B2.
     *
     * No elimina el archivo original de Fotos/Galería.
     */
    suspend fun deleteMessage(chatId: String, message: Message) {
        val actorUid = currentUserId

        require(actorUid.isNotBlank()) {
            "Necesitas iniciar sesión para borrar un mensaje"
        }

        require(chatId.isNotBlank() && message.id.isNotBlank()) {
            "Mensaje inválido"
        }

        require(message.senderId.isBlank() || message.senderId == actorUid) {
            "Solo puedes borrar tus propios mensajes"
        }

        val chatRef = firestore.collection("chats").document(chatId)
        val messageRef = chatRef.collection("messages").document(message.id)

        val messageSnapshot = messageRef.get().await()

        if (!messageSnapshot.exists()) {
            messageDao.deleteMessage(message.id)
            return
        }

        require(messageSnapshot.getString("senderId") == actorUid) {
            "Solo puedes borrar tus propios mensajes"
        }

        val mediaKeys = (
            message.mediaStorageKeys() +
                mediaStorageKeys(messageSnapshot)
            ).distinct()

        val mediaUrls = mediaUrls(message, messageSnapshot)

        // Primero se borra Firestore: la interfaz no depende de B2.
        messageRef.delete().await()
        messageDao.deleteMessage(message.id)

        // Limpia caché privada de Vivid.
        purgeLocalMediaCache(mediaKeys, mediaUrls)

        // Preview del chat: no bloquea un borrado ya exitoso.
        runCatching {
            refreshChatPreviewAfterMessageDelete(chatRef)
        }.onFailure { error ->
            Log.w(
                TAG,
                "Mensaje ${message.id} borrado, pero no se pudo actualizar la preview",
                error
            )
        }

        // B2 se limpia después y sin bloquear la interfaz.
        mediaKeys.forEach { key ->
            repositoryScope.launch {
                val deleted = runCatching {
                    storage.deleteFile(key)
                }.onFailure { error ->
                    Log.w(
                        TAG,
                        "No se pudo limpiar el adjunto $key de ${message.id}",
                        error
                    )
                }.getOrDefault(false)

                if (!deleted) {
                    Log.w(
                        TAG,
                        "El mensaje ${message.id} se borró, pero B2 no confirmó borrar $key"
                    )
                }
            }
        }
    }

    /**
     * Recupera claves de adjuntos de documentos antiguos y nuevos.
     */
    private fun mediaStorageKeys(message: DocumentSnapshot): List<String> =
        listOf(
            "imageKey",
            "voiceKey",
            "videoKey",
            "storageKey",
            "thumbnailKey"
        ).mapNotNull { field ->
            message.getString(field)?.takeIf { it.isNotBlank() }
        }.distinct()

    private fun mediaUrls(
        message: Message,
        snapshot: DocumentSnapshot
    ): List<String> =
        listOf(
            message.imageUrl,
            message.voiceUrl,
            snapshot.getString("imageUrl").orEmpty(),
            snapshot.getString("voiceUrl").orEmpty(),
            snapshot.getString("videoUrl").orEmpty(),
            snapshot.getString("thumbnailUrl").orEmpty()
        ).filter { it.isNotBlank() }
            .distinct()

    /**
     * Borra solamente caché privado de la app.
     *
     * No borra archivos originales de Fotos/Galería.
     */
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    private fun purgeLocalMediaCache(
        mediaKeys: List<String>,
        mediaUrls: List<String>
    ) {
        runCatching {
            if (mediaUrls.isNotEmpty()) {
                imageLoader.memoryCache?.clear()
                imageLoader.diskCache?.clear()
            }

            mediaUrls.forEach { url ->
                VideoCacheManager.removeCachedMedia(appContext, url)
            }

            mediaKeys
                .filter { it.startsWith("chat_images/") }
                .forEach { key ->
                    val uploadId = key
                        .substringAfterLast('/')
                        .substringBeforeLast('.')

                    if (uploadId.isNotBlank()) {
                        File(
                            appContext.cacheDir,
                            "chat_img_$uploadId.jpg"
                        ).delete()
                    }
                }
        }.onFailure { error ->
            Log.w(
                TAG,
                "No se pudo limpiar por completo el caché local del mensaje",
                error
            )
        }
    }

    private suspend fun refreshChatPreviewAfterMessageDelete(
        chatRef: com.google.firebase.firestore.DocumentReference
    ) {
        val latestRemaining = chatRef.collection("messages")
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
            "video" -> "Video"
            "voice" -> "🎙️ Nota de voz"
            "story_reply" -> "↳ Respondió a tu story"
            else -> latestText
        }

        chatRef.set(
            mapOf(
                "lastMessage" to lastMessageDisplay,
                "lastMessageType" to latestType,
                "lastSenderId" to latestSenderId,
                "lastMessageSenderId" to latestSenderId,
                "lastTimestamp" to (
                    latestRemaining?.getLong("timestamp")
                        ?: System.currentTimeMillis()
                    ),
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun editMessage(
        chatId: String,
        messageId: String,
        newText: String
    ) {
        val senderId = currentUserId

        if (senderId.isBlank() || chatId.isBlank() || messageId.isBlank()) return
        if (newText.isBlank()) return

        try {
            val ref = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)

            val snap = ref.get().await()

            if (!snap.exists()) return
            if (snap.getString("senderId") != senderId) return
            if (snap.getString("type") != "text") return

            val now = System.currentTimeMillis()

            ref.update(
                mapOf(
                    "text" to newText,
                    "lastEditedAt" to now
                )
            ).await()

            messageDao.updateText(messageId, newText, now)
        } catch (_: Exception) {
        }
    }

    suspend fun markChatAsRead(chatId: String) {
        if (currentUserId.isBlank() || chatId.isBlank()) return

        try {
            firestore.collection("chats")
                .document(chatId)
                .update(
                    mapOf(
                        "unreadCounts.$currentUserId" to 0,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (_: Exception) {
            firestore.collection("chats")
                .document(chatId)
                .set(
                    mapOf(
                        "unreadCounts" to mapOf(currentUserId to 0),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
                .await()
        }

        markMessagesAsDelivered(chatId)
        markMessagesAsRead(chatId)
    }

    suspend fun markMessagesAsDelivered(chatId: String) {
        if (currentUserId.isBlank() || chatId.isBlank()) return

        try {
            val undelivered = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("isDelivered", false)
                .get()
                .await()

            if (undelivered.isEmpty) return

            val batch = firestore.batch()

            undelivered.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf("isDelivered" to true)
                )
            }

            batch.commit().await()

            undelivered.documents.forEach { doc ->
                repositoryScope.launch {
                    messageDao.updateReadState(
                        doc.id,
                        isRead = doc.getBoolean("isRead") ?: false,
                        isDelivered = true
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun markMessagesAsRead(chatId: String) {
        if (currentUserId.isBlank() || chatId.isBlank()) return

        try {
            val unread = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", currentUserId)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (unread.isEmpty) return

            val batch = firestore.batch()

            unread.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "isRead" to true,
                        "isDelivered" to true
                    )
                )
            }

            batch.commit().await()

            unread.documents.forEach { doc ->
                repositoryScope.launch {
                    messageDao.insertMessage(
                        documentToMessage(doc)
                            .toEntity(chatId)
                            .copy(
                                isRead = true,
                                isDelivered = true
                            )
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun setTyping(chatId: String, isTyping: Boolean) {
        if (currentUserId.isBlank() || chatId.isBlank()) return

        try {
            val ref = firestore.collection("chats").document(chatId)

            if (isTyping) {
                ref.set(
                    mapOf(
                        "typing" to mapOf(
                            currentUserId to System.currentTimeMillis()
                        )
                    ),
                    SetOptions.merge()
                ).await()
            } else {
                ref.update(
                    "typing.$currentUserId",
                    FieldValue.delete()
                ).await()
            }
        } catch (_: Exception) {
        }
    }

    fun listenTyping(
        chatId: String,
        onTypingChanged: (Map<String, Long>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .addSnapshotListener { snap, _ ->
                val typing = snap?.get("typing") as? Map<String, Long> ?: run {
                    val raw = snap?.get("typing") as? Map<*, *>

                    raw?.mapNotNull { (key, value) ->
                        val userId = key as? String
                            ?: return@mapNotNull null

                        val timestamp = (value as? Long)
                            ?: (value as? Number)?.toLong()
                            ?: return@mapNotNull null

                        userId to timestamp
                    }?.toMap() ?: emptyMap()
                }

                onTypingChanged(typing)
            }
    }

    suspend fun markMessageDelivered(
        chatId: String,
        messageId: String
    ) {
        try {
            firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .update("isDelivered", true)
                .await()
        } catch (_: Exception) {
        }
    }

    fun listenToMessages(
        chatId: String,
        onMessageEvent: (MessageChange) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val msg = documentToMessage(change.document)

                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            persistMessage(chatId, msg)
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

    fun listenToNewMessages(
        chatId: String,
        afterTimestamp: Long,
        onMessageEvent: (MessageChange) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereGreaterThan("timestamp", afterTimestamp)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val msg = documentToMessage(change.document)

                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            persistMessage(chatId, msg)
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

    fun listenToReactions(
        chatId: String,
        onMessageEvent: (MessageChange) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereNotEqualTo("reaction", "")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    val doc = change.document

                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            val msg = documentToMessage(doc)
                            persistMessage(chatId, msg)
                            onMessageEvent(MessageChange.Upsert(msg))
                        }

                        DocumentChange.Type.REMOVED -> {
                            repositoryScope.launch {
                                val stillExists = runCatching {
                                    doc.reference.get().await().exists()
                                }.getOrDefault(true)

                                if (stillExists) {
                                    val msg = documentToMessage(doc)
                                        .copy(reaction = "")

                                    persistMessage(chatId, msg)
                                    onMessageEvent(MessageChange.Upsert(msg))
                                } else {
                                    messageDao.deleteMessage(doc.id)
                                    onMessageEvent(MessageChange.Removed(doc.id))
                                }
                            }
                        }
                    }
                }
            }
    }

    fun listenToEdits(
        chatId: String,
        onMessageEvent: (MessageChange) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereGreaterThan("lastEditedAt", 0)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (
                        change.type == DocumentChange.Type.ADDED ||
                        change.type == DocumentChange.Type.MODIFIED
                    ) {
                        val msg = documentToMessage(change.document)
                        persistMessage(chatId, msg)
                        onMessageEvent(MessageChange.Upsert(msg))
                    }
                }
            }
    }

    fun listenToDeliveryReceipts(
        chatId: String,
        onMessageDelivered: (messageId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val senderId = currentUserId

        if (senderId.isBlank()) {
            return firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .addSnapshotListener { _, _ -> }
        }

        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereEqualTo("isDelivered", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.REMOVED) {
                        val doc = change.document

                        if (doc.getString("senderId") == senderId) {
                            val id = doc.id

                            repositoryScope.launch {
                                messageDao.updateReadState(
                                    id,
                                    isRead = doc.getBoolean("isRead") ?: false,
                                    isDelivered = true
                                )
                            }

                            onMessageDelivered(id)
                        }
                    }
                }
            }
    }

    fun listenToReadReceipts(
        chatId: String,
        onMessageRead: (messageId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val senderId = currentUserId

        if (senderId.isBlank()) {
            return firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .addSnapshotListener { _, _ -> }
        }

        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.REMOVED) {
                        val doc = change.document
                        val wasMine = doc.getString("senderId") == senderId

                        if (wasMine) {
                            val id = doc.id

                            repositoryScope.launch {
                                messageDao.updateReadState(
                                    id,
                                    isRead = true,
                                    isDelivered = true
                                )
                            }

                            onMessageRead(id)
                        }
                    }
                }
            }
    }

    sealed class MessageChange {
        data class Upsert(val message: Message) : MessageChange()
        data class Removed(val messageId: String) : MessageChange()
    }

    fun cacheChatPreview(
        chatId: String,
        otherUserId: String,
        otherUserName: String,
        lastMessage: String,
        lastMessageType: String,
        lastSenderId: String,
        timestamp: Long,
        unreadCount: Int,
        avatarUrl: String,
        avatarBase64: String
    ) {
        repositoryScope.launch {
            chatDao.insertOrUpdateChat(
                ChatEntity(
                    chatId = chatId,
                    otherUserId = otherUserId,
                    otherUserName = otherUserName,
                    otherUserAvatar = avatarUrl,
                    lastMessage = lastMessage,
                    lastMessageTimestamp = timestamp,
                    unreadCount = unreadCount,
                    lastMessageSenderId = lastSenderId,
                    lastMessageType = lastMessageType,
                    avatarBase64 = avatarBase64,
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun isChatCacheFresh(): Boolean {
        val lastCached = chatDao.getLastCachedAt() ?: return false

        return (
            System.currentTimeMillis() - lastCached
            ) < 7L * 24L * 60L * 60L * 1000L
    }

    suspend fun updateReactionInCache(
        messageId: String,
        reaction: String
    ) {
        messageDao.updateReaction(messageId, reaction)
    }

    companion object {
        private const val TAG = "ChatRepository"

        fun buildChatId(userA: String, userB: String): String =
            listOf(userA, userB)
                .sorted()
                .joinToString("_")
    }
}

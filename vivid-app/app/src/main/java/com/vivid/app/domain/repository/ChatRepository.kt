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
import com.vivid.app.util.PushSender
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
                // No reescribimos participants ni createdAt. Las reglas nuevas protegen
                // ese conjunto y así una actualización de perfil no rompe chats antiguos.
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
                    replyToStoryId = entity.replyToStoryId
                )
            }.sortedBy { it.timestamp }
        }
    }

    /** Último timestamp de mensaje cacheado (para sync incremental). */
    suspend fun getLastCachedTimestamp(chatId: String): Long? =
        messageDao.getMaxTimestamp(chatId)

    /** Convierte un DocumentSnapshot de Firestore a [Message]. */
    private fun documentToMessage(doc: com.google.firebase.firestore.DocumentSnapshot): Message =
        Message(
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

    /** Persiste un [Message] en la caché Room del chat. */
    private fun persistMessage(chatId: String, msg: Message) {
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
                    replyToStoryId = msg.replyToStoryId,
                    reaction = msg.reaction
                )
            )
        }
    }

    /**
     * Backfill único: trae los mensajes del chat en PÁGINAS y los guarda en
     * Room. Se usa cuando la caché está vacía o caducó (7 días).
     *
     * Antes solo traía los últimos 200 mensajes: tras caducar la caché, el
     * historial anterior desaparecía de la vista. Ahora pagina hacia atrás
     * (startAfter) hasta agotar el historial o [maxPages] páginas, lo que
     * ocurra primero (tope de seguridad contra chats gigantes).
     */
    suspend fun backfillMessages(
        chatId: String,
        pageSize: Int = 300,
        maxPages: Int = 10
    ) {
        if (chatId.isBlank()) return
        val entities = mutableListOf<MessageEntity>()
        var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null

        for (page in 0 until maxPages) {
            var query = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
            if (lastDoc != null) query = query.startAfter(lastDoc)

            val snapshot = query.limit(pageSize.toLong()).get().await()
            if (snapshot.documents.isEmpty()) break

            snapshot.documents.forEach { doc ->
                val msg = documentToMessage(doc)
                if (msg.id.isBlank()) return@forEach
                entities += MessageEntity(
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
                    replyToStoryId = msg.replyToStoryId,
                    reaction = msg.reaction
                )
            }

            if (snapshot.documents.size < pageSize) break
            lastDoc = snapshot.documents.last()
        }

        if (entities.isNotEmpty()) {
            messageDao.insertMessages(entities)
        }
    }

    suspend fun sendMessage(chatId: String, text: String, receiverId: String, replyToStoryId: String = "") {
        val senderId = currentUserId
        if (senderId.isBlank() || receiverId.isBlank() || text.isBlank()) return

        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
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
        val preview = if (type == "story_reply") "↳ Respondió a tu story" else text

        persistOutgoingMessage(
            chatId = chatId,
            receiverId = receiverId,
            messageId = messageId,
            messageData = messageData,
            lastMessage = preview,
            lastMessageType = type,
            now = now
        )
        // El mensaje local solo se inserta después de que Firestore confirma la
        // transacción. Así no quedan mensajes fantasma cuando faltan permisos.
        repositoryScope.launch { messageDao.insertMessage(message) }
    }

    /**
     * Envía un mensaje de imagen. El binario YA está en B2; aquí solo se guarda
     * la URL firmada + la key remota en Firestore (documento ligero, sin Base64).
     */
    suspend fun sendImageMessage(chatId: String, receiverId: String, imageUrl: String, imageKey: String) {
        val senderId = currentUserId
        if (senderId.isBlank() || receiverId.isBlank() || imageUrl.isBlank() || imageKey.isBlank()) return

        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
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
        repositoryScope.launch { messageDao.insertMessage(message) }
    }

    /**
     * Envía una nota de voz. El audio YA está en B2; guarda URL + duración.
     */
    suspend fun sendVoiceMessage(chatId: String, receiverId: String, voiceUrl: String, voiceKey: String, durationMs: Long) {
        val senderId = currentUserId
        if (senderId.isBlank() || receiverId.isBlank() || voiceUrl.isBlank() || voiceKey.isBlank()) return
        val now = System.currentTimeMillis()
        val messageId = firestore.collection("chats").document(chatId).collection("messages").document().id
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
        repositoryScope.launch { messageDao.insertMessage(message) }
    }

    private fun formatDuration(ms: Long): String {
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }

    /**
     * Guarda el mensaje y la preview del chat en una sola transacción.
     *
     * Antes eran dos escrituras independientes: el mensaje podía existir aunque
     * la actualización de la preview fuera rechazada por las reglas nuevas, y la
     * UI mostraba la imagen/voz como fallida. Además, el primer envío podía correr
     * antes de que openChat terminara de crear el documento padre.
     */
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
        require(senderId.isNotBlank() && receiverId.isNotBlank()) { "Sesión de chat no válida" }
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
                ) { "La conversación no tiene participantes válidos" }

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
                    previewUpdate["unreadCounts.$receiverId"] = FieldValue.increment(1)
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

    /**
     * Edita el texto de un mensaje propio (solo type=text). Marca
     * `lastEditedAt` con el timestamp del momento; el resto de campos
     * (senderId, isRead, etc.) NO se modifican.
     *
     * El listener del chat propaga el cambio a la UI automáticamente
     * porque la query escucha cualquier MODIFIED.
     */
    suspend fun editMessage(chatId: String, messageId: String, newText: String) {
        val senderId = currentUserId
        if (senderId.isBlank() || chatId.isBlank() || messageId.isBlank()) return
        if (newText.isBlank()) return
        try {
            val ref = firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
            val snap = ref.get().await()
            if (!snap.exists()) return
            // Solo el emisor puede editar (las reglas también lo bloquean,
            // pero el chequeo local evita un round-trip inútil).
            if (snap.getString("senderId") != senderId) return
            if (snap.getString("type") != "text") return
            val now = System.currentTimeMillis()
            ref.update(
                mapOf(
                    "text" to newText,
                    "lastEditedAt" to now
                )
            ).await()
            // El listener ya va a repintar la UI, pero actualizamos el caché
            // local también para que al volver al chat no se vea el texto viejo.
            repositoryScope.launch {
                val existing = messageDao.getMessagesForChat(chatId)
                // getMessagesForChat devuelve Flow; aquí no podemos leer de él
                // directamente. Como el listener se va a disparar igualmente,
                // dejamos que Room se actualice por la ruta del listener.
            }
        } catch (_: Exception) {
            // Silencioso: la UI muestra snackbar de error en ChatViewModel.
        }
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
                            replyToStoryId = doc.getString("replyToStoryId").orEmpty(),
                            reaction = doc.getString("reaction").orEmpty()
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
     * Escucha los mensajes de un chat en tiempo real (SYNC COMPLETO).
     * Se usa cuando la caché Room está vacía o caducó (7 días): trae todo el
     * historial para repoblar el caché local.
     */
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
                            repositoryScope.launch { messageDao.deleteMessage(msg.id) }
                            onMessageEvent(MessageChange.Removed(msg.id))
                        }
                    }
                }
            }
    }

    /**
     * Escucha SOLO mensajes nuevos (SYNC INCREMENTAL).
     *
     * Cuando el chat ya tiene caché local, el servidor solo envía los mensajes
     * más recientes que el último cacheado — el historial viejo se sirve de
     * Room y el servidor hace mucho menos esfuerzo.
     */
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
                            repositoryScope.launch { messageDao.deleteMessage(msg.id) }
                            onMessageEvent(MessageChange.Removed(msg.id))
                        }
                    }
                }
            }
    }

    /**
     * Listener de REACCIONES (complementa al sync incremental).
     *
     * En modo incremental el listener principal solo ve mensajes con
     * `timestamp > últimoCacheado`; una reacción sobre un mensaje VIEJO no
     * cae en esa query y antes se perdía para siempre. Esta query escucha
     * todos los mensajes con reacción (ADDED/MODIFIED) y, cuando el doc sale
     * de la query (REMOVED), distingue entre "reacción quitada" y "mensaje
     * borrado" con una lectura puntual del doc.
     */
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
                            // El doc dejó de cumplir la query: o le quitaron
                            // la reacción o el mensaje fue borrado.
                            repositoryScope.launch {
                                val stillExists = runCatching {
                                    doc.reference.get().await().exists()
                                }.getOrDefault(true)
                                if (stillExists) {
                                    val msg = documentToMessage(doc).copy(reaction = "")
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

    /**
     * Listeners de read receipts (SYNC LIGERO).
     *
     * Escucha los mensajes NO LEÍDOS del chat (query de un solo campo, sin
     * índice compuesto). Cuando un mensaje pasa a leído, deja de cumplir la
     * query y Firestore emite un evento REMOVED; si el mensaje era mío,
     * actualizamos el caché local — sin re-leer el historial completo.
     */
    fun listenToReadReceipts(
        chatId: String,
        onMessageRead: (messageId: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration {
        val senderId = currentUserId
        if (senderId.isBlank()) return firestore.collection("chats").document(chatId)
            .collection("messages").addSnapshotListener { _, _ -> }
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
                    // Un doc que deja de cumplir la query (isRead pasó a true)
                    // llega como REMOVED. Solo nos interesan los míos.
                    if (change.type == DocumentChange.Type.REMOVED) {
                        val doc = change.document
                        val wasMine = doc.getString("senderId") == senderId
                        if (wasMine) {
                            val id = doc.id
                            repositoryScope.launch {
                                messageDao.updateReadState(id, isRead = true, isDelivered = true)
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

    /**
     * Guarda en Room la preview de un chat (para la lista de chats cacheada).
     * Se llama desde el snapshot listener de ChatListScreen.
     */
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
                    // Antes quedaba en 0 y isChatCacheFresh() siempre daba
                    // "vencido". Ahora la vigencia de 7 días funciona.
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Indica si la caché de chats/mensajes sigue vigente (menos de 7 días). */
    suspend fun isChatCacheFresh(): Boolean {
        val lastCached = chatDao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < 7L * 24L * 60L * 60L * 1000L
    }

    /** Guarda la reacción de un mensaje en la caché Room. */
    suspend fun updateReactionInCache(messageId: String, reaction: String) {
        messageDao.updateReaction(messageId, reaction)
    }

    companion object {
        fun buildChatId(userA: String, userB: String): String =
            listOf(userA, userB).sorted().joinToString("_")
    }
}

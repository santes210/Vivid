package com.vivid.app.presentation.messages

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.storage.MAX_SIGNED_TTL_SEC
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.domain.repository.ChatRepository
import com.vivid.app.util.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Estado de una imagen en proceso de envío.
 */
data class ImageUpload(
    val localId: String,
    val phase: Phase = Phase.COMPRESSING,
    val progress: Int = 0,
    val error: String? = null,
    val uri: Uri? = null
) {
    enum class Phase {
        COMPRESSING,
        UPLOADING,
        DONE,
        FAILED
    }
}

data class VoiceUpload(
    val localId: String,
    val phase: ImageUpload.Phase = ImageUpload.Phase.UPLOADING,
    val progress: Int = 0,
    val error: String? = null,
    val durationMs: Long = 0L,
    val localFilePath: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val storage: StorageProvider,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChatsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _canMessage = MutableStateFlow(true)
    val canMessage: StateFlow<Boolean> = _canMessage.asStateFlow()

    private val _imageUploads = MutableStateFlow<List<ImageUpload>>(emptyList())
    val imageUploads: StateFlow<List<ImageUpload>> = _imageUploads.asStateFlow()

    private val _voiceUploads = MutableStateFlow<List<VoiceUpload>>(emptyList())
    val voiceUploads: StateFlow<List<VoiceUpload>> = _voiceUploads.asStateFlow()

    private val _isOtherTyping = MutableStateFlow(false)
    val isOtherTyping: StateFlow<Boolean> = _isOtherTyping.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private var loadedChatId: String? = null
    private var messagesListener: ListenerRegistration? = null
    private var reactionsListener: ListenerRegistration? = null
    private var editsListener: ListenerRegistration? = null
    private var deliveryListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var readReceiptsListener: ListenerRegistration? = null

    private var typingJob: Job? = null
    private var recordingTickerJob: Job? = null

    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    /**
     * Abre un chat nuevo o existente.
     *
     * Primero crea/verifica el documento padre de chats y sus participantes.
     * Solo después inicia listeners de mensajes. Esto evita PERMISSION_DENIED
     * en conversaciones nuevas.
     */
    fun openChat(
        chatId: String,
        receiverId: String,
        receiverName: String
    ) {
        viewModelScope.launch {
            try {
                val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
                val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""

                // IMPORTANTE:
                // Las reglas requieren que chats/{chatId} exista antes de
                // escuchar chats/{chatId}/messages.
                chatRepository.ensureChatExists(
                    chatId = chatId,
                    otherUserId = receiverId,
                    otherUserName = receiverName,
                    avatarUrl = avatarUrl,
                    avatarBase64 = avatarBase64
                )

                _canMessage.value = computeCanMessage(receiverId)

                // Solo después de verificar el padre del chat iniciamos
                // listeners, typing y acuses.
                loadMessages(chatId)
                observeTyping(chatId, receiverId)
                chatRepository.markChatAsRead(chatId)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo preparar el chat $chatId", error)
                showError(
                    readableError(
                        error,
                        "No se pudo abrir la conversación"
                    )
                )
            }
        }
    }

    private suspend fun computeCanMessage(receiverId: String): Boolean {
        if (receiverId.isBlank()) return false
        if (receiverId == currentUserId) return true
        if (currentUserId.isBlank()) return false

        return try {
            val userSnapshot = firestore.collection("users")
                .document(receiverId)
                .get()
                .await()

            val isPrivate = userSnapshot.getBoolean("isPrivate") ?: false

            if (!isPrivate) {
                return true
            }

            firestore.collection("users")
                .document(currentUserId)
                .collection("following")
                .document(receiverId)
                .get()
                .await()
                .exists()
        } catch (_: Exception) {
            false
        }
    }

    fun loadMessages(chatId: String) {
        if (loadedChatId == chatId) return

        loadedChatId = chatId
        _messages.value = emptyList()

        viewModelScope.launch {
            chatRepository.getMessagesFlow(chatId).collect { messages ->
                _messages.value = messages

                if (messages.any { it.senderId != currentUserId && !it.isRead }) {
                    launch {
                        chatRepository.markMessagesAsRead(chatId)
                    }
                }
            }
        }

        messagesListener?.remove()
        readReceiptsListener?.remove()
        reactionsListener?.remove()
        editsListener?.remove()
        deliveryListener?.remove()

        reactionsListener = null
        editsListener = null
        deliveryListener = null

        viewModelScope.launch {
            val lastCachedTimestamp = runCatching {
                chatRepository.getLastCachedTimestamp(chatId)
            }.getOrNull()

            val idleDays = lastCachedTimestamp?.let {
                (System.currentTimeMillis() - it) /
                    (24L * 60L * 60L * 1000L)
            } ?: Long.MAX_VALUE

            val useFullSync =
                lastCachedTimestamp == null || idleDays >= 7L

            if (useFullSync) {
                runCatching {
                    chatRepository.backfillMessages(chatId)
                }

                messagesListener = chatRepository.listenToMessages(
                    chatId = chatId,
                    onMessageEvent = { event ->
                        handleMessageEvent(chatId, event)
                    },
                    onError = { error ->
                        Log.e(
                            TAG,
                            "Listener de mensajes rechazado para $chatId",
                            error
                        )
                        showError(
                            readableError(
                                error,
                                "No se pudieron cargar los mensajes"
                            )
                        )
                    }
                )
            } else {
                messagesListener = chatRepository.listenToNewMessages(
                    chatId = chatId,
                    afterTimestamp = lastCachedTimestamp ?: 0L,
                    onMessageEvent = { event ->
                        handleMessageEvent(chatId, event)
                    },
                    onError = { error ->
                        Log.e(
                            TAG,
                            "Listener incremental rechazado para $chatId",
                            error
                        )
                        showError(
                            readableError(
                                error,
                                "No se pudieron cargar los mensajes"
                            )
                        )
                    }
                )

                readReceiptsListener = chatRepository.listenToReadReceipts(
                    chatId = chatId,
                    onMessageRead = { messageId ->
                        _messages.value = _messages.value.map {
                            if (it.id == messageId) {
                                it.copy(isRead = true, isDelivered = true)
                            } else {
                                it
                            }
                        }
                    },
                    onError = { error ->
                        Log.w(
                            TAG,
                            "Listener de read receipts rechazado: ${error.message}"
                        )
                    }
                )

                reactionsListener = chatRepository.listenToReactions(
                    chatId = chatId,
                    onMessageEvent = { event ->
                        handleMessageEvent(chatId, event)
                    },
                    onError = { error ->
                        Log.w(
                            TAG,
                            "Listener de reacciones rechazado: ${error.message}"
                        )
                    }
                )

                editsListener = chatRepository.listenToEdits(
                    chatId = chatId,
                    onMessageEvent = { event ->
                        handleMessageEvent(chatId, event)
                    },
                    onError = { error ->
                        Log.w(
                            TAG,
                            "Listener de ediciones rechazado: ${error.message}"
                        )
                    }
                )

                deliveryListener = chatRepository.listenToDeliveryReceipts(
                    chatId = chatId,
                    onMessageDelivered = { messageId ->
                        _messages.value = _messages.value.map {
                            if (it.id == messageId) {
                                it.copy(isDelivered = true)
                            } else {
                                it
                            }
                        }
                    },
                    onError = { error ->
                        Log.w(
                            TAG,
                            "Listener de entregas rechazado: ${error.message}"
                        )
                    }
                )
            }
        }
    }

    private fun handleMessageEvent(
        chatId: String,
        event: ChatRepository.MessageChange
    ) {
        when (event) {
            is ChatRepository.MessageChange.Upsert -> {
                val currentMessages = _messages.value.toMutableList()
                val index = currentMessages.indexOfFirst {
                    it.id == event.message.id
                }

                if (index >= 0) {
                    currentMessages[index] = event.message
                } else {
                    currentMessages.add(event.message)
                }

                _messages.value = currentMessages.sortedBy {
                    it.timestamp
                }

                if (event.message.senderId != currentUserId) {
                    viewModelScope.launch {
                        if (!event.message.isDelivered) {
                            chatRepository.markMessageDelivered(
                                chatId,
                                event.message.id
                            )
                        }

                        if (!event.message.isRead) {
                            chatRepository.markMessagesAsRead(chatId)
                        }
                    }
                }
            }

            is ChatRepository.MessageChange.Removed -> {
                _messages.value = _messages.value.filterNot {
                    it.id == event.messageId
                }
            }
        }
    }

    fun sendMessage(
        chatId: String,
        text: String,
        receiverId: String
    ) {
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(chatId, text, receiverId)
                setTyping(chatId, false)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar mensaje en $chatId", error)
                showError(
                    readableError(
                        error,
                        "No se pudo enviar el mensaje"
                    )
                )
            }
        }
    }

    private fun observeTyping(
        chatId: String,
        otherUserId: String
    ) {
        typingListener?.remove()

        typingListener = chatRepository.listenTyping(chatId) { typingMap ->
            val timestamp = typingMap[otherUserId] ?: 0L
            val now = System.currentTimeMillis()

            _isOtherTyping.value =
                timestamp != 0L && (now - timestamp) < 6000L

            if (_isOtherTyping.value) {
                viewModelScope.launch {
                    delay(6500)

                    if (System.currentTimeMillis() - timestamp >= 6000L) {
                        _isOtherTyping.value = false
                    }
                }
            }
        }
    }

    fun setTyping(chatId: String, isTyping: Boolean) {
        viewModelScope.launch {
            chatRepository.setTyping(chatId, isTyping)
        }

        if (isTyping) {
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(4000)
                chatRepository.setTyping(chatId, false)
            }
        } else {
            typingJob?.cancel()
        }
    }

    fun onTextChanged(chatId: String, text: String) {
        if (text.isNotBlank()) {
            setTyping(chatId, true)
        } else {
            setTyping(chatId, false)
        }
    }

    fun sendImage(
        chatId: String,
        receiverId: String,
        uri: Uri
    ) {
        if (
            receiverId.isBlank() ||
            currentUserId.isBlank() ||
            chatId.isBlank()
        ) {
            return
        }

        val upload = ImageUpload(
            localId = UUID.randomUUID().toString(),
            uri = uri
        )

        _imageUploads.value = _imageUploads.value + upload
        launchImageUpload(chatId, receiverId, upload)
    }

    fun retryImageUpload(
        chatId: String,
        receiverId: String,
        localId: String
    ) {
        val current = _imageUploads.value.firstOrNull {
            it.localId == localId
        } ?: return

        if (current.uri == null) return

        val retry = current.copy(
            phase = ImageUpload.Phase.COMPRESSING,
            progress = 0,
            error = null
        )

        _imageUploads.value = _imageUploads.value.map {
            if (it.localId == localId) retry else it
        }

        launchImageUpload(chatId, receiverId, retry)
    }

    fun dismissImageUpload(localId: String) {
        _imageUploads.value = _imageUploads.value.filterNot {
            it.localId == localId
        }
    }

    private fun launchImageUpload(
        chatId: String,
        receiverId: String,
        upload: ImageUpload
    ) {
        val uri = upload.uri ?: return

        viewModelScope.launch {
            var tempFile: File? = null
            var uploadedKey: String? = null

            try {
                val processedFile = withContext(Dispatchers.IO) {
                    val destination = File(
                        context.cacheDir,
                        "chat_img_${upload.localId}.jpg"
                    )

                    val compressed = ImageCompressor.compressToFile(
                        uri,
                        context,
                        destination
                    )

                    check(compressed && destination.exists() && destination.length() > 0L) {
                        "No se pudo procesar la imagen"
                    }

                    destination
                }

                tempFile = processedFile

                updateUpload(upload.localId) {
                    it.copy(
                        phase = ImageUpload.Phase.UPLOADING,
                        progress = 5
                    )
                }

                val key = "chat_images/$chatId/${upload.localId}.jpg"

                val imageUrl = storage.uploadFile(
                    processedFile.absolutePath,
                    key
                ) { progress ->
                    updateUpload(upload.localId) {
                        it.copy(
                            phase = ImageUpload.Phase.UPLOADING,
                            progress = progress.coerceIn(5, 98)
                        )
                    }
                }

                check(imageUrl.isNotBlank()) {
                    "El servidor no devolvió la imagen"
                }

                uploadedKey = key

                chatRepository.sendImageMessage(
                    chatId,
                    receiverId,
                    imageUrl,
                    key
                )

                updateUpload(upload.localId) {
                    it.copy(
                        phase = ImageUpload.Phase.DONE,
                        progress = 100
                    )
                }

                withContext(Dispatchers.IO) {
                    processedFile.delete()
                }

                delay(1500)
                dismissImageUpload(upload.localId)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar imagen en $chatId", error)

                uploadedKey?.let { key ->
                    runCatching {
                        storage.deleteFile(key)
                    }
                }

                withContext(Dispatchers.IO) {
                    tempFile?.delete()
                }

                val message = readableError(
                    error,
                    "No se pudo enviar la imagen"
                )

                updateUpload(upload.localId) {
                    it.copy(
                        phase = ImageUpload.Phase.FAILED,
                        error = message
                    )
                }

                showError(message)
            }
        }
    }

    private fun updateUpload(
        localId: String,
        transform: (ImageUpload) -> ImageUpload
    ) {
        _imageUploads.value = _imageUploads.value.map {
            if (it.localId == localId) transform(it) else it
        }
    }

    private var voiceRecorder: com.vivid.app.util.VoiceRecorder? = null

    fun startVoiceRecording() {
        if (_isRecording.value) return

        if (voiceRecorder == null) {
            voiceRecorder = com.vivid.app.util.VoiceRecorder(context)
        }

        val file = voiceRecorder?.startRecording()

        if (file == null) {
            showError("No se pudo iniciar el micrófono")
            return
        }

        _isRecording.value = true
        _recordingDurationMs.value = 0L

        recordingTickerJob?.cancel()

        recordingTickerJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(200)
                _recordingDurationMs.value =
                    voiceRecorder?.getElapsedMs() ?: 0L
            }
        }
    }

    fun stopVoiceRecording(cancel: Boolean = false): File? {
        recordingTickerJob?.cancel()

        val file = voiceRecorder?.stopRecording(cancel)

        _isRecording.value = false
        _recordingDurationMs.value = 0L

        if (cancel) return null

        if (file == null) {
            showError(
                "La nota de voz es demasiado corta o no se pudo guardar"
            )
        }

        return file
    }

    fun sendVoice(
        chatId: String,
        receiverId: String,
        file: File,
        durationMs: Long
    ) {
        if (
            receiverId.isBlank() ||
            currentUserId.isBlank() ||
            chatId.isBlank()
        ) {
            return
        }

        if (!file.exists() || file.length() == 0L) {
            showError("La grabación está vacía")
            return
        }

        val upload = VoiceUpload(
            localId = UUID.randomUUID().toString(),
            progress = 5,
            durationMs = durationMs,
            localFilePath = file.absolutePath
        )

        _voiceUploads.value = _voiceUploads.value + upload
        launchVoiceUpload(chatId, receiverId, upload)
    }

    private fun launchVoiceUpload(
        chatId: String,
        receiverId: String,
        upload: VoiceUpload
    ) {
        val file = upload.localFilePath?.let(::File) ?: return

        viewModelScope.launch {
            var uploadedKey: String? = null

            try {
                check(file.exists() && file.length() > 0L) {
                    "La grabación ya no está disponible"
                }

                val key = "chat_voice/$chatId/${upload.localId}.m4a"

                val voiceUrl = storage.uploadFile(
                    file.absolutePath,
                    key
                ) { progress ->
                    _voiceUploads.value = _voiceUploads.value.map {
                        if (it.localId == upload.localId) {
                            it.copy(
                                phase = ImageUpload.Phase.UPLOADING,
                                progress = progress
                            )
                        } else {
                            it
                        }
                    }
                }

                check(voiceUrl.isNotBlank()) {
                    "El servidor no devolvió el audio"
                }

                uploadedKey = key

                chatRepository.sendVoiceMessage(
                    chatId,
                    receiverId,
                    voiceUrl,
                    key,
                    upload.durationMs
                )

                _voiceUploads.value = _voiceUploads.value.map {
                    if (it.localId == upload.localId) {
                        it.copy(
                            progress = 100,
                            phase = ImageUpload.Phase.DONE,
                            error = null
                        )
                    } else {
                        it
                    }
                }

                delay(1200)

                _voiceUploads.value = _voiceUploads.value.filterNot {
                    it.localId == upload.localId
                }

                withContext(Dispatchers.IO) {
                    file.delete()
                }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar nota de voz en $chatId", error)

                uploadedKey?.let { key ->
                    runCatching {
                        storage.deleteFile(key)
                    }
                }

                val message = readableError(
                    error,
                    "No se pudo enviar la nota de voz"
                )

                _voiceUploads.value = _voiceUploads.value.map {
                    if (it.localId == upload.localId) {
                        it.copy(
                            phase = ImageUpload.Phase.FAILED,
                            error = message
                        )
                    } else {
                        it
                    }
                }

                showError(message)
            }
        }
    }

    fun retryVoiceUpload(
        chatId: String,
        receiverId: String,
        localId: String
    ) {
        val failed = _voiceUploads.value.firstOrNull {
            it.localId == localId
        } ?: return

        val path = failed.localFilePath ?: return

        if (!File(path).exists()) {
            dismissVoiceUpload(localId)
            showError("La grabación ya no está disponible")
            return
        }

        val retry = failed.copy(
            phase = ImageUpload.Phase.UPLOADING,
            progress = 5,
            error = null
        )

        _voiceUploads.value = _voiceUploads.value.map {
            if (it.localId == localId) retry else it
        }

        launchVoiceUpload(chatId, receiverId, retry)
    }

    fun dismissVoiceUpload(localId: String) {
        _voiceUploads.value.firstOrNull {
            it.localId == localId
        }?.localFilePath
            ?.let(::File)
            ?.delete()

        _voiceUploads.value = _voiceUploads.value.filterNot {
            it.localId == localId
        }
    }

    fun refreshImageUrl(
        messageId: String,
        imageKey: String
    ) {
        val chatId = loadedChatId ?: return
        if (imageKey.isBlank()) return

        viewModelScope.launch {
            try {
                val freshUrl = storage.signDownloadUrl(
                    imageKey,
                    MAX_SIGNED_TTL_SEC
                )

                check(freshUrl.isNotBlank()) {
                    "No se pudo renovar la imagen"
                }

                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("imageUrl", freshUrl)
                    .await()

                _messages.value = _messages.value.map {
                    if (it.id == messageId) {
                        it.copy(imageUrl = freshUrl)
                    } else {
                        it
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun refreshVoiceUrl(
        messageId: String,
        voiceKey: String
    ) {
        val chatId = loadedChatId ?: return
        if (voiceKey.isBlank()) return

        viewModelScope.launch {
            try {
                val freshUrl = storage.signDownloadUrl(
                    voiceKey,
                    MAX_SIGNED_TTL_SEC
                )

                check(freshUrl.isNotBlank()) {
                    "No se pudo renovar el audio"
                }

                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("voiceUrl", freshUrl)
                    .await()

                _messages.value = _messages.value.map {
                    if (it.id == messageId) {
                        it.copy(voiceUrl = freshUrl)
                    } else {
                        it
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun deleteMessage(
        chatId: String,
        message: Message
    ) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(chatId, message)

                _messages.value = _messages.value.filterNot {
                    it.id == message.id
                }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo borrar ${message.id}", error)

                showError(
                    readableError(
                        error,
                        "No se pudo eliminar el mensaje"
                    )
                )
            }
        }
    }

    fun editMessage(
        chatId: String,
        messageId: String,
        newText: String
    ) {
        if (newText.isBlank()) return

        val now = System.currentTimeMillis()

        _messages.value = _messages.value.map { message ->
            if (message.id == messageId) {
                message.copy(
                    text = newText,
                    lastEditedAt = now
                )
            } else {
                message
            }
        }

        viewModelScope.launch {
            try {
                chatRepository.editMessage(
                    chatId,
                    messageId,
                    newText
                )
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo editar $messageId", error)

                showError(
                    readableError(
                        error,
                        "No se pudo editar el mensaje"
                    )
                )
            }
        }
    }

    fun reactToMessage(
        chatId: String,
        messageId: String,
        reaction: String
    ) {
        viewModelScope.launch {
            _messages.value = _messages.value.map { message ->
                if (message.id == messageId) {
                    message.copy(reaction = reaction)
                } else {
                    message
                }
            }

            try {
                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("reaction", reaction)
                    .await()

                runCatching {
                    chatRepository.updateReactionInCache(
                        messageId,
                        reaction
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo reaccionar a $messageId", error)

                showError(
                    readableError(
                        error,
                        "No se pudo guardar la reacción"
                    )
                )
            }
        }
    }

    fun onMicrophonePermissionDenied() {
        showError(
            "Necesitas permitir el micrófono para enviar notas de voz"
        )
    }

    fun consumeUserMessage(message: String) {
        if (_userMessage.value == message) {
            _userMessage.value = null
        }
    }

    private fun showError(message: String) {
        _userMessage.value = message
    }

    private fun readableError(
        error: Throwable,
        fallback: String
    ): String {
        return when {
            error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "$fallback: permisos de la conversación rechazados"

            error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "$fallback: vuelve a iniciar sesión"

            error is FirebaseFirestoreException &&
                error.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                "$fallback: revisa tu conexión"

            error.message?.contains(
                "b2_authorize_account",
                ignoreCase = true
            ) == true ->
                "$fallback: el almacenamiento no pudo iniciar sesión"

            error.message?.contains(
                "b2_",
                ignoreCase = true
            ) == true ->
                "$fallback: el almacenamiento rechazó la subida"

            error.message?.contains(
                "Unable to resolve host",
                ignoreCase = true
            ) == true ->
                "$fallback: revisa tu conexión"

            else -> fallback
        }
    }

    override fun onCleared() {
        messagesListener?.remove()
        reactionsListener?.remove()
        readReceiptsListener?.remove()
        typingListener?.remove()

        messagesListener = null
        reactionsListener = null
        readReceiptsListener = null
        typingListener = null

        voiceRecorder?.stopRecording(cancel = true)

        super.onCleared()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}

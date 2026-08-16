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
import com.vivid.app.data.storage.BackblazeStorageProvider
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
    enum class Phase { COMPRESSING, UPLOADING, DONE, FAILED }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    private var typingListener: ListenerRegistration? = null
    private var typingJob: Job? = null
    private var recordingTickerJob: Job? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun openChat(chatId: String, receiverId: String, receiverName: String) {
        viewModelScope.launch {
            try {
                val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
                val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""
                chatRepository.ensureChatExists(chatId, receiverId, receiverName, avatarUrl, avatarBase64)
                chatRepository.markChatAsRead(chatId)
                _canMessage.value = computeCanMessage(receiverId)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo preparar el chat $chatId", error)
                showError(readableError(error, "No se pudo abrir la conversación"))
            }
        }
        loadMessages(chatId)
        observeTyping(chatId, receiverId)
        // Mark delivered + read when opening
        viewModelScope.launch {
            delay(500)
            chatRepository.markMessagesAsRead(chatId)
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
            if (!isPrivate) return true
            val followingSnapshot = firestore.collection("users")
                .document(currentUserId)
                .collection("following")
                .document(receiverId)
                .get()
                .await()
            followingSnapshot.exists()
        } catch (_: Exception) {
            false
        }
    }

    private var readReceiptsListener: ListenerRegistration? = null

    fun loadMessages(chatId: String) {
        if (loadedChatId == chatId) return
        loadedChatId = chatId
        _messages.value = emptyList()

        viewModelScope.launch {
            chatRepository.getMessagesFlow(chatId).collect { msgs ->
                _messages.value = msgs
                // Si hay nuevos mensajes del otro, marcar como leídos automáticamente
                if (msgs.any { it.senderId != currentUserId && !it.isRead }) {
                    launch { chatRepository.markMessagesAsRead(chatId) }
                }
            }
        }

        messagesListener?.remove()
        readReceiptsListener?.remove()
        reactionsListener?.remove()
        reactionsListener = null

        // ── Estrategia de caché (para que el servidor no lea todo el historial) ──
        // Caché vacía → sync completo (backfill del historial).
        // Caché vigente → sync incremental: el servidor solo envía mensajes más
        // nuevos que el último cacheado; el historial se sirve desde Room.
        // Caché con >7 días sin actividad → sync completo ocasional (también
        // sincroniza borrados del otro lado y read receipts de mensajes viejos).
        viewModelScope.launch {
            val lastCachedTs = runCatching {
                chatRepository.getLastCachedTimestamp(chatId)
            }.getOrNull()

            val idleDays = lastCachedTs?.let {
                (System.currentTimeMillis() - it) / (24L * 60L * 60L * 1000L)
            } ?: Long.MAX_VALUE
            val useFullSync = lastCachedTs == null || idleDays >= 7L

            if (useFullSync) {
                // Historial completo: repobla la caché Room
                runCatching { chatRepository.backfillMessages(chatId) }
                messagesListener = chatRepository.listenToMessages(
                    chatId = chatId,
                    onMessageEvent = { event -> handleMessageEvent(chatId, event) },
                    onError = { error ->
                        Log.e(TAG, "Listener de mensajes rechazado para $chatId", error)
                        showError(readableError(error, "No se pudieron cargar los mensajes"))
                    }
                )
            } else {
                // Sync incremental: solo mensajes nuevos + read receipts ligeros
                messagesListener = chatRepository.listenToNewMessages(
                    chatId = chatId,
                    afterTimestamp = lastCachedTs ?: 0L,
                    onMessageEvent = { event -> handleMessageEvent(chatId, event) },
                    onError = { error ->
                        Log.e(TAG, "Listener incremental rechazado para $chatId", error)
                        showError(readableError(error, "No se pudieron cargar los mensajes"))
                    }
                )
                readReceiptsListener = chatRepository.listenToReadReceipts(
                    chatId = chatId,
                    onMessageRead = { messageId ->
                        _messages.value = _messages.value.map {
                            if (it.id == messageId) it.copy(isRead = true, isDelivered = true) else it
                        }
                    },
                    onError = { error ->
                        // Índice ausente o permisos: los read receipts se degradan
                        // silenciosamente (los mensajes siguen funcionando).
                        Log.w(TAG, "Listener de read receipts rechazado: ${error.message}")
                    }
                )

                // En modo incremental el listener principal no ve reacciones
                // sobre mensajes viejos; este listener las cubre (y también
                // los borrados de mensajes antiguos).
                reactionsListener?.remove()
                reactionsListener = chatRepository.listenToReactions(
                    chatId = chatId,
                    onMessageEvent = { event -> handleMessageEvent(chatId, event) },
                    onError = { error ->
                        // Se degrada silenciosamente: solo se pierde el refresh
                        // de reacciones, los mensajes siguen funcionando.
                        Log.w(TAG, "Listener de reacciones rechazado: ${error.message}")
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
                val current = _messages.value.toMutableList()
                val index = current.indexOfFirst { it.id == event.message.id }
                if (index >= 0) {
                    current[index] = event.message
                } else {
                    current.add(event.message)
                }
                _messages.value = current.sortedBy { it.timestamp }
                if (event.message.senderId != currentUserId && !event.message.isRead) {
                    viewModelScope.launch { chatRepository.markMessagesAsRead(chatId) }
                }
            }
            is ChatRepository.MessageChange.Removed -> {
                _messages.value = _messages.value.filterNot { it.id == event.messageId }
            }
        }
    }

    fun sendMessage(chatId: String, text: String, receiverId: String) {
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(chatId, text, receiverId)
                setTyping(chatId, false)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar mensaje en $chatId", error)
                showError(readableError(error, "No se pudo enviar el mensaje"))
            }
        }
    }

    // ── Typing indicator ──────────────────────────
    private fun observeTyping(chatId: String, otherUserId: String) {
        typingListener?.remove()
        typingListener = chatRepository.listenTyping(chatId) { typingMap ->
            val ts = typingMap[otherUserId] ?: 0L
            val now = System.currentTimeMillis()
            // Consider typing if timestamp < 6 seconds ago
            val isTyping = ts != 0L && (now - ts) < 6000
            _isOtherTyping.value = isTyping
            // Poll to auto-hide after 5s
            if (isTyping) {
                viewModelScope.launch {
                    delay(6500)
                    if (System.currentTimeMillis() - ts >= 6000) {
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

    // ── Imágenes ──────────────────────────────────
    fun sendImage(chatId: String, receiverId: String, uri: Uri) {
        if (receiverId.isBlank() || currentUserId.isBlank() || chatId.isBlank()) return
        val upload = ImageUpload(localId = UUID.randomUUID().toString(), uri = uri)
        _imageUploads.value = _imageUploads.value + upload
        launchImageUpload(chatId, receiverId, upload)
    }

    fun retryImageUpload(chatId: String, receiverId: String, localId: String) {
        val current = _imageUploads.value.firstOrNull { it.localId == localId } ?: return
        if (current.uri == null) return
        val retry = current.copy(phase = ImageUpload.Phase.COMPRESSING, progress = 0, error = null)
        _imageUploads.value = _imageUploads.value.map {
            if (it.localId == localId) retry else it
        }
        launchImageUpload(chatId, receiverId, retry)
    }

    fun dismissImageUpload(localId: String) {
        _imageUploads.value = _imageUploads.value.filterNot { it.localId == localId }
    }

    private fun launchImageUpload(chatId: String, receiverId: String, upload: ImageUpload) {
        val uri = upload.uri ?: return
        viewModelScope.launch {
            var tempFile: File? = null
            var uploadedKey: String? = null
            try {
                val processedFile = withContext(Dispatchers.IO) {
                    val dest = File(context.cacheDir, "chat_img_${upload.localId}.jpg")
                    val compressed = ImageCompressor.compressToFile(uri, context, dest)
                    check(compressed && dest.exists() && dest.length() > 0L) {
                        "No se pudo procesar la imagen"
                    }
                    dest
                }
                tempFile = processedFile
                updateUpload(upload.localId) {
                    it.copy(phase = ImageUpload.Phase.UPLOADING, progress = 5)
                }
                val key = "chat_images/$chatId/${upload.localId}.jpg"
                val imageUrl = storage.uploadFile(processedFile.absolutePath, key) { pct ->
                    updateUpload(upload.localId) {
                        it.copy(phase = ImageUpload.Phase.UPLOADING, progress = pct.coerceIn(5, 98))
                    }
                }
                check(imageUrl.isNotBlank()) { "El servidor no devolvió la imagen" }
                uploadedKey = key
                chatRepository.sendImageMessage(chatId, receiverId, imageUrl, key)
                updateUpload(upload.localId) { it.copy(phase = ImageUpload.Phase.DONE, progress = 100) }
                withContext(Dispatchers.IO) { processedFile.delete() }
                delay(1500)
                dismissImageUpload(upload.localId)
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar imagen en $chatId", error)
                uploadedKey?.let { key -> runCatching { storage.deleteFile(key) } }
                withContext(Dispatchers.IO) { tempFile?.delete() }
                val message = readableError(error, "No se pudo enviar la imagen")
                updateUpload(upload.localId) {
                    it.copy(phase = ImageUpload.Phase.FAILED, error = message)
                }
                showError(message)
            }
        }
    }

    private fun updateUpload(localId: String, transform: (ImageUpload) -> ImageUpload) {
        _imageUploads.value = _imageUploads.value.map {
            if (it.localId == localId) transform(it) else it
        }
    }

    // ── Notas de voz ──────────────────────────────
    private var voiceRecorder: com.vivid.app.util.VoiceRecorder? = null

    fun startVoiceRecording() {
        if (_isRecording.value) return
        if (voiceRecorder == null) voiceRecorder = com.vivid.app.util.VoiceRecorder(context)
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
                _recordingDurationMs.value = voiceRecorder?.getElapsedMs() ?: 0L
            }
        }
    }

    fun stopVoiceRecording(cancel: Boolean = false): File? {
        recordingTickerJob?.cancel()
        val file = voiceRecorder?.stopRecording(cancel)
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        if (cancel) return null
        if (file == null) showError("La nota de voz es demasiado corta o no se pudo guardar")
        return file
    }

    fun sendVoice(chatId: String, receiverId: String, file: File, durationMs: Long) {
        if (receiverId.isBlank() || currentUserId.isBlank() || chatId.isBlank()) return
        if (!file.exists() || file.length() == 0L) {
            showError("La grabación está vacía")
            return
        }
        val localId = UUID.randomUUID().toString()
        val upload = VoiceUpload(
            localId = localId,
            progress = 5,
            durationMs = durationMs,
            localFilePath = file.absolutePath
        )
        _voiceUploads.value = _voiceUploads.value + upload
        launchVoiceUpload(chatId, receiverId, upload)
    }

    private fun launchVoiceUpload(chatId: String, receiverId: String, upload: VoiceUpload) {
        val file = upload.localFilePath?.let(::File) ?: return
        viewModelScope.launch {
            var uploadedKey: String? = null
            try {
                check(file.exists() && file.length() > 0L) { "La grabación ya no está disponible" }
                val key = "chat_voice/$chatId/${upload.localId}.m4a"
                val voiceUrl = storage.uploadFile(file.absolutePath, key) { pct ->
                    _voiceUploads.value = _voiceUploads.value.map {
                        if (it.localId == upload.localId) {
                            it.copy(phase = ImageUpload.Phase.UPLOADING, progress = pct)
                        } else {
                            it
                        }
                    }
                }
                check(voiceUrl.isNotBlank()) { "El servidor no devolvió el audio" }
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
                        it.copy(progress = 100, phase = ImageUpload.Phase.DONE, error = null)
                    } else {
                        it
                    }
                }
                delay(1200)
                _voiceUploads.value = _voiceUploads.value.filterNot { it.localId == upload.localId }
                withContext(Dispatchers.IO) { file.delete() }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo enviar nota de voz en $chatId", error)
                uploadedKey?.let { key -> runCatching { storage.deleteFile(key) } }
                val message = readableError(error, "No se pudo enviar la nota de voz")
                _voiceUploads.value = _voiceUploads.value.map {
                    if (it.localId == upload.localId) {
                        it.copy(phase = ImageUpload.Phase.FAILED, error = message)
                    } else {
                        it
                    }
                }
                showError(message)
            }
        }
    }

    fun retryVoiceUpload(chatId: String, receiverId: String, localId: String) {
        val failed = _voiceUploads.value.firstOrNull { it.localId == localId } ?: return
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
        _voiceUploads.value = _voiceUploads.value.map { if (it.localId == localId) retry else it }
        launchVoiceUpload(chatId, receiverId, retry)
    }

    fun dismissVoiceUpload(localId: String) {
        _voiceUploads.value.firstOrNull { it.localId == localId }
            ?.localFilePath
            ?.let(::File)
            ?.delete()
        _voiceUploads.value = _voiceUploads.value.filterNot { it.localId == localId }
    }

    fun refreshImageUrl(messageId: String, imageKey: String) {
        val chatId = loadedChatId ?: return
        if (imageKey.isBlank()) return
        viewModelScope.launch {
            try {
                val freshUrl = storage.signDownloadUrl(
                    imageKey,
                    BackblazeStorageProvider.MAX_SIGNED_TTL_SEC
                )
                check(freshUrl.isNotBlank()) { "No se pudo renovar la imagen" }
                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("imageUrl", freshUrl)
                    .await()
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(imageUrl = freshUrl) else it
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshVoiceUrl(messageId: String, voiceKey: String) {
        val chatId = loadedChatId ?: return
        if (voiceKey.isBlank()) return
        viewModelScope.launch {
            try {
                val freshUrl = storage.signDownloadUrl(voiceKey, BackblazeStorageProvider.MAX_SIGNED_TTL_SEC)
                check(freshUrl.isNotBlank()) { "No se pudo renovar el audio" }
                firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .update("voiceUrl", freshUrl)
                    .await()
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(voiceUrl = freshUrl) else it
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteMessage(chatId: String, message: Message) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(chatId, message)
                _messages.value = _messages.value.filterNot { it.id == message.id }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo borrar ${message.id}", error)
                showError(readableError(error, "No se pudo eliminar el mensaje"))
            }
        }
    }

    fun reactToMessage(chatId: String, messageId: String, reaction: String) {
        viewModelScope.launch {
            _messages.value = _messages.value.map { msg ->
                if (msg.id == messageId) msg.copy(reaction = reaction) else msg
            }
            try {
                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("reaction", reaction)
                    .await()
                // Guardar la reacción en el caché Room
                runCatching { chatRepository.updateReactionInCache(messageId, reaction) }
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo reaccionar a $messageId", error)
                showError(readableError(error, "No se pudo guardar la reacción"))
            }
        }
    }

    fun onMicrophonePermissionDenied() {
        showError("Necesitas permitir el micrófono para enviar notas de voz")
    }

    fun consumeUserMessage(message: String) {
        if (_userMessage.value == message) _userMessage.value = null
    }

    private fun showError(message: String) {
        _userMessage.value = message
    }

    private fun readableError(error: Throwable, fallback: String): String {
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
            error.message?.contains("b2_authorize_account", ignoreCase = true) == true ->
                "$fallback: el almacenamiento no pudo iniciar sesión"
            error.message?.contains("b2_", ignoreCase = true) == true ->
                "$fallback: el almacenamiento rechazó la subida"
            error.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
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

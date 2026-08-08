package com.vivid.app.presentation.messages

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    val durationMs: Long = 0L
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

    private var loadedChatId: String? = null
    private var otherUserIdCached: String = ""
    private var messagesListener: ListenerRegistration? = null
    private var typingListener: ListenerRegistration? = null
    private var typingJob: Job? = null
    private var recordingTickerJob: Job? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun openChat(chatId: String, receiverId: String, receiverName: String) {
        otherUserIdCached = receiverId
        viewModelScope.launch {
            val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
            val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""
            chatRepository.ensureChatExists(chatId, receiverId, receiverName, avatarUrl, avatarBase64)
            chatRepository.markChatAsRead(chatId)
            _canMessage.value = computeCanMessage(receiverId)
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
        messagesListener = chatRepository.listenToMessages(chatId) { event ->
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
                    // Auto mark as read if message is from other user
                    if (event.message.senderId != currentUserId && !event.message.isRead) {
                        viewModelScope.launch { chatRepository.markMessagesAsRead(chatId) }
                    }
                }
                is ChatRepository.MessageChange.Removed -> {
                    _messages.value = _messages.value.filterNot { it.id == event.messageId }
                }
            }
        }
    }

    fun sendMessage(chatId: String, text: String, receiverId: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, text, receiverId)
            setTyping(chatId, false)
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
            try {
                tempFile = withContext(Dispatchers.IO) {
                    val dest = File(context.cacheDir, "chat_img_${upload.localId}.jpg")
                    ImageCompressor.compressToFile(uri, context, dest)
                    dest
                }
                if (!tempFile!!.exists() || tempFile!!.length() == 0L) {
                    throw IllegalStateException("No se pudo procesar la imagen")
                }
                updateUpload(upload.localId) {
                    it.copy(phase = ImageUpload.Phase.UPLOADING, progress = 5)
                }
                val key = "chat_images/$chatId/${upload.localId}.jpg"
                val imageUrl = storage.uploadFile(tempFile!!.absolutePath, key) { pct ->
                    updateUpload(upload.localId) {
                        it.copy(phase = ImageUpload.Phase.UPLOADING, progress = pct.coerceIn(5, 98))
                    }
                }
                chatRepository.sendImageMessage(chatId, receiverId, imageUrl, key)
                updateUpload(upload.localId) { it.copy(phase = ImageUpload.Phase.DONE, progress = 100) }
                withContext(Dispatchers.IO) { tempFile?.delete() }
                delay(1500)
                dismissImageUpload(upload.localId)
            } catch (e: Exception) {
                updateUpload(upload.localId) {
                    it.copy(
                        phase = ImageUpload.Phase.FAILED,
                        error = e.message ?: "No se pudo enviar la imagen"
                    )
                }
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
        if (file != null) {
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
    }

    fun stopVoiceRecording(cancel: Boolean = false): File? {
        recordingTickerJob?.cancel()
        val file = voiceRecorder?.stopRecording(cancel)
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        if (cancel) return null
        return file
    }

    fun sendVoice(chatId: String, receiverId: String, file: File, durationMs: Long) {
        if (receiverId.isBlank() || currentUserId.isBlank() || chatId.isBlank()) return
        if (!file.exists() || file.length() == 0L) return
        val localId = UUID.randomUUID().toString()
        val upload = VoiceUpload(localId = localId, progress = 5, durationMs = durationMs)
        _voiceUploads.value = _voiceUploads.value + upload
        viewModelScope.launch {
            try {
                val key = "chat_voice/$chatId/$localId.m4a"
                val voiceUrl = storage.uploadFile(file.absolutePath, key) { pct ->
                    _voiceUploads.value = _voiceUploads.value.map {
                        if (it.localId == localId) it.copy(progress = pct) else it
                    }
                }
                chatRepository.sendVoiceMessage(chatId, receiverId, voiceUrl, key, durationMs)
                _voiceUploads.value = _voiceUploads.value.map {
                    if (it.localId == localId) it.copy(progress = 100, phase = ImageUpload.Phase.DONE) else it
                }
                delay(1200)
                _voiceUploads.value = _voiceUploads.value.filterNot { it.localId == localId }
                withContext(Dispatchers.IO) { file.delete() }
            } catch (e: Exception) {
                _voiceUploads.value = _voiceUploads.value.map {
                    if (it.localId == localId) it.copy(phase = ImageUpload.Phase.FAILED, error = e.message) else it
                }
            }
        }
    }

    fun retryVoiceUpload(chatId: String, receiverId: String, localId: String) {
        // Not implemented for brevity — voice retry requires file retention
        _voiceUploads.value = _voiceUploads.value.filterNot { it.localId == localId }
    }

    fun dismissVoiceUpload(localId: String) {
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
                firestore.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                    .update("imageUrl", freshUrl)
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
                firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .update("voiceUrl", freshUrl)
                _messages.value = _messages.value.map {
                    if (it.id == messageId) it.copy(voiceUrl = freshUrl) else it
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteMessage(chatId: String, message: Message) {
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, message)
            _messages.value = _messages.value.filterNot { it.id == message.id }
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
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        messagesListener?.remove()
        typingListener?.remove()
        messagesListener = null
        typingListener = null
        voiceRecorder?.stopRecording(cancel = true)
        super.onCleared()
    }
}

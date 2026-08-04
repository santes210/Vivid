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
 * La imagen vive en el ViewModel (no como "Message" aún) hasta que termina
 * de subirse a B2; recién entonces se inserta el mensaje con su URL.
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

    private var loadedChatId: String? = null
    private var messagesListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun openChat(chatId: String, receiverId: String, receiverName: String) {
        viewModelScope.launch {
            val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
            val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""
            chatRepository.ensureChatExists(chatId, receiverId, receiverName, avatarUrl, avatarBase64)
            chatRepository.markChatAsRead(chatId)
            _canMessage.value = computeCanMessage(receiverId)
        }
        loadMessages(chatId)
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
            }
        }

        // Remover listener previo (si lo hay) antes de suscribir uno nuevo
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
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    //  Envío de imágenes: comprimir → subir a B2 → guardar URL en Firestore
    // ──────────────────────────────────────────────────────────────────────

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
                // 1. Comprimir a JPEG en cache (máx 1280px, ~550 KB)
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

                // 2. Subir a B2 (el binario NUNCA toca Firestore)
                val key = "chat_images/$chatId/${upload.localId}.jpg"
                val imageUrl = storage.uploadFile(tempFile!!.absolutePath, key) { pct ->
                    updateUpload(upload.localId) {
                        it.copy(phase = ImageUpload.Phase.UPLOADING, progress = pct.coerceIn(5, 98))
                    }
                }

                // 3. Guardar el mensaje (solo URL + key remota)
                chatRepository.sendImageMessage(chatId, receiverId, imageUrl, key)
                updateUpload(upload.localId) { it.copy(phase = ImageUpload.Phase.DONE, progress = 100) }

                withContext(Dispatchers.IO) { tempFile?.delete() }

                // La burbuja "enviado" desaparece sola; el mensaje real ya está en la lista
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

    /**
     * Las URLs firmadas de B2 caducan (máx 7 días). Cuando una imagen falla al
     * cargar (403), se re-firma con la key remota y se actualiza el mensaje
     * en Firestore + estado local para que el otro participante también la
     * recupere.
     */
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
            } catch (_: Exception) {
                // Sin conexión o B2 caído: se reintentará en el próximo render
            }
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
            // Actualizar localmente para feedback instantáneo
            _messages.value = _messages.value.map { msg ->
                if (msg.id == messageId) msg.copy(reaction = reaction) else msg
            }
            // Guardar en Firestore
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
        messagesListener = null
        super.onCleared()
    }
}

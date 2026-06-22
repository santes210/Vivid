package com.vivid.app.presentation.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChatsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _canMessage = MutableStateFlow(true)
    val canMessage: StateFlow<Boolean> = _canMessage.asStateFlow()

    private var loadedChatId: String? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun openChat(chatId: String, receiverId: String, receiverName: String) {
        viewModelScope.launch {
            val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
            val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""
            chatRepository.ensureChatExists(chatId, receiverId, receiverName, avatarUrl, avatarBase64)
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

        chatRepository.listenToMessages(chatId) { event ->
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

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId)
            _messages.value = _messages.value.filterNot { it.id == messageId }
        }
    }
}

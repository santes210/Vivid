package com.vivid.app.presentation.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    fun openChat(chatId: String, receiverId: String, receiverName: String) {
        viewModelScope.launch {
            val avatarBase64 = savedStateHandle.get<String>("avatarBase64") ?: ""
            val avatarUrl = savedStateHandle.get<String>("avatarUrl") ?: ""
            chatRepository.ensureChatExists(chatId, receiverId, receiverName, avatarUrl, avatarBase64)

            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("users").document(receiverId).get().addOnSuccessListener { snapshot ->
                val isPrivate = snapshot.getBoolean("isPrivate") ?: false
                if (isPrivate) {
                    _canMessage.value = false
                } else {
                    _canMessage.value = true
                }
            }
        }
        loadMessages(chatId)
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

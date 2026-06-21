package com.vivid.app.presentation.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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

        chatRepository.listenToMessages(chatId) { newMessage ->
            val current = _messages.value.toMutableList()
            if (current.none { it.id == newMessage.id }) {
                current.add(newMessage)
                _messages.value = current.sortedBy { it.timestamp }
            }
        }
    }

    fun sendMessage(chatId: String, text: String, receiverId: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(chatId, text, receiverId)
        }
    }
}

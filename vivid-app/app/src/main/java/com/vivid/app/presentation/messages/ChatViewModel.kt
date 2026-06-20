package com.vivid.app.presentation.messages

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
    private val chatRepository: ChatRepository
) : ViewModel() {

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChatsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    fun loadMessages(chatId: String) {
        viewModelScope.launch {
            chatRepository.getMessagesFlow(chatId).collect { msgs ->
                _messages.value = msgs
            }
        }
        
        // Real-time listener
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

    fun createChat(otherUserId: String, name: String, avatar: String): String {
        var chatId = ""
        viewModelScope.launch {
            chatId = chatRepository.createOrGetChat(otherUserId, name, avatar)
        }
        return chatId
    }
}
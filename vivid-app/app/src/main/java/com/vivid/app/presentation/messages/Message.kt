package com.vivid.app.presentation.messages

data class Message(
    val id: String = "",
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

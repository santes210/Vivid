package com.vivid.app.presentation.messages

import android.content.SharedPreferences
import java.util.UUID

/** Private, account-scoped drafts. Keep the operation ID across retries/navigation. */
internal class ChatDraftStore(private val preferences: SharedPreferences) {
    private fun key(uid: String, chatId: String) = "${uid.length}:$uid:$chatId"

    fun text(uid: String, chatId: String): String =
        if (uid.isBlank()) "" else preferences.getString(key(uid, chatId) + ":text", "").orEmpty()

    fun save(uid: String, chatId: String, text: String): String {
        require(uid.isNotBlank() && chatId.isNotBlank())
        val key = key(uid, chatId)
        val currentId = preferences.getString(key + ":id", null)
        if (this.text(uid, chatId) == text && currentId != null) return currentId
        val id = UUID.randomUUID().toString()
        preferences.edit().putString(key + ":text", text).putString(key + ":id", id).apply()
        return id
    }

    fun clearIfAcknowledged(uid: String, chatId: String, operationId: String) {
        val key = key(uid, chatId)
        if (preferences.getString(key + ":id", null) == operationId) {
            preferences.edit().remove(key + ":text").remove(key + ":id").apply()
        }
    }
}

enum class TextSendPhase { IDLE, SENDING, SENT, FAILED }

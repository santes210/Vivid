package com.vivid.app.presentation.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import javax.inject.Inject

/** Reuse the chat repository's atomic delivery and stable-ID retries for story replies. */
@HiltViewModel
class StoryInteractionViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val attempts = mutableMapOf<List<String>, String>()
    private var sending = false

    fun reply(storyId: String, ownerId: String, text: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid.orEmpty()
        val message = text.trim()
        if (sending || uid.isBlank() || message.isBlank() || message.length > 2000 || uid == ownerId) {
            onResult(false)
            return
        }
        val key = listOf(uid, storyId, ownerId, message)
        val id = attempts.getOrPut(key) { UUID.randomUUID().toString() }
        sending = true
        viewModelScope.launch {
            try {
                withTimeout(15_000) {
                    chats.sendMessage(ChatRepository.buildChatId(uid, ownerId), message, ownerId,
                        replyToStoryId = storyId, messageId = id)
                }
                attempts.remove(key)
                onResult(true)
            } catch (_: TimeoutCancellationException) { onResult(false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onResult(false) }
            finally { sending = false }
        }
    }
}

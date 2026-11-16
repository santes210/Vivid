package com.vivid.app.presentation.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.UUID
import javax.inject.Inject

data class ShareRecipient(val chatId: String, val uid: String, val name: String, val avatarUrl: String = "")
enum class ReelShareStatus { SENDING, SENT, FAILED }

@HiltViewModel
class ReelShareViewModel @Inject constructor(
    private val chats: ChatRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    private val _recipients = MutableStateFlow<List<ShareRecipient>>(emptyList())
    val recipients = _recipients.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _loadFailed = MutableStateFlow(false)
    val loadFailed = _loadFailed.asStateFlow()
    private val _statuses = MutableStateFlow<Map<String, ReelShareStatus>>(emptyMap())
    val statuses = _statuses.asStateFlow()
    private val operationIds = mutableMapOf<String, String>()
    private val operationTexts = mutableMapOf<String, String>()
    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    val notes = _notes.asStateFlow()
    private val sendSlots = Semaphore(3)
    private var listener: com.google.firebase.firestore.ListenerRegistration? = null
    private var owner: String? = null

    fun loadRecipients() {
        listener?.remove()
        val uid = auth.currentUser?.uid
        if (uid != owner) { _statuses.value = emptyMap(); operationIds.clear(); operationTexts.clear(); _notes.value = emptyMap() }
        owner = uid
        _recipients.value = emptyList()
        _loadFailed.value = false
        _loading.value = true
        if (uid == null) { _loading.value = false; _loadFailed.value = true; return }
        listener = firestore.collection("chats").whereArrayContains("participants", uid)
            .orderBy("lastTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(30).addSnapshotListener { snapshot, error ->
                if (auth.currentUser?.uid != uid) { _recipients.value = emptyList(); _loading.value = false; _loadFailed.value = true; return@addSnapshotListener }
                _loading.value = false
                _loadFailed.value = error != null
                _recipients.value = snapshot?.documents?.mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    val other = participants.filterIsInstance<String>().firstOrNull { it != uid } ?: return@mapNotNull null
                    val names = doc.get("participantNames") as? Map<*, *>
                    val avatars = doc.get("participantAvatars") as? Map<*, *>
                    ShareRecipient(doc.id, other, names?.get(other) as? String ?: "Usuario",
                        (avatars?.get(other) as? String).orEmpty().takeIf { it.startsWith("https://") }.orEmpty())
                }.orEmpty()
            }
    }

    fun sendSelection(contentLink: String, selectedIds: Set<String>, baseText: String, note: String) {
        val sender = owner ?: return
        if (auth.currentUser?.uid != sender) return
        val selected = _recipients.value.filter { it.chatId in selectedIds }.distinctBy { it.chatId }
        if (selected.isEmpty()) return
        if (contentLink !in _notes.value) {
            _notes.value = _notes.value + (contentLink to note.trim().take(ShareSelection.MAX_NOTE_LENGTH))
        }
        val payload = ShareSelection.payload(baseText, _notes.value[contentLink].orEmpty())
        selected.forEach { send(contentLink, it, payload, sender) }
    }

    private fun send(contentLink: String, recipient: ShareRecipient, text: String, sender: String) {
        val key = "$contentLink:${recipient.chatId}"
        if (_statuses.value[key] in listOf(ReelShareStatus.SENDING, ReelShareStatus.SENT)) return
        val id = operationIds.getOrPut(key) { UUID.randomUUID().toString() }
        // Freeze the payload with the operation ID: an ambiguous retry cannot
        // silently change the original note or create a second message.
        val originalText = operationTexts.getOrPut(key) { text }
        _statuses.value = _statuses.value + (key to ReelShareStatus.SENDING)
        fun update(status: ReelShareStatus) {
            if (owner == sender && auth.currentUser?.uid == sender) {
                _statuses.value = _statuses.value + (key to status)
            }
        }
        viewModelScope.launch {
            try {
                sendSlots.withPermit {
                    withTimeout(30_000) {
                        check(auth.currentUser?.uid == sender)
                        val me = firestore.collection("users").document(sender)
                            .get(com.google.firebase.firestore.Source.SERVER).await()
                        val target = firestore.collection("users").document(recipient.uid)
                            .get(com.google.firebase.firestore.Source.SERVER).await()
                        check(target.exists())
                        check(!(me.get("blockedUsers") as? List<*>).orEmpty().contains(recipient.uid))
                        check(!(target.get("blockedUsers") as? List<*>).orEmpty().contains(sender))
                        if (target.getBoolean("isPrivate") != false) {
                            check(firestore.collection("users").document(sender).collection("following")
                                .document(recipient.uid).get(com.google.firebase.firestore.Source.SERVER).await().exists())
                        }
                        check(auth.currentUser?.uid == sender)
                        chats.sendMessage(recipient.chatId, originalText, recipient.uid, messageId = id)
                    }
                }
                update(ReelShareStatus.SENT)
            } catch (_: TimeoutCancellationException) {
                update(ReelShareStatus.FAILED)
            } catch (cancelled: CancellationException) {
                update(ReelShareStatus.FAILED)
                throw cancelled
            } catch (_: Exception) {
                update(ReelShareStatus.FAILED)
            }
        }
    }

    fun stopRecipients() { listener?.remove(); listener = null }

    override fun onCleared() { stopRecipients() }
}

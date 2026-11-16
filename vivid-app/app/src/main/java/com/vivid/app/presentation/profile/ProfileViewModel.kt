package com.vivid.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: StorageProvider
) : ViewModel() {

    private val _pinBusy = MutableStateFlow(false)
    val pinBusy: StateFlow<Boolean> = _pinBusy

    fun togglePinned(post: ProfilePost, onResult: (String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (_pinBusy.value) return
        _pinBusy.value = true
        viewModelScope.launch {
            try {
                require(post.collection == "posts" || post.collection == "reels")
                val userRef = firestore.collection("users").document(uid)
                val contentRef = firestore.collection(post.collection).document(post.id)
                firestore.runTransaction { txn ->
                    val user = txn.get(userRef)
                    val existing = (user.get("pinnedContent") as? List<*>)?.filterIsInstance<String>().orEmpty()
                    // Allow unpinning even if the original content was deleted.
                    if (post.contentKey() !in existing) {
                        check(txn.get(contentRef).getString("userId") == uid) { "not_owner" }
                    }
                    txn.update(userRef, "pinnedContent", updatedProfilePins(existing, post.contentKey()))
                    null
                }.await()
                onResult(null)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onResult(if (error.message?.contains("pin_limit") == true) "limit" else "error")
            } finally { _pinBusy.value = false }
        }
    }

    fun removePinned(key: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        if (_pinBusy.value) return
        _pinBusy.value = true
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("pinnedContent", com.google.firebase.firestore.FieldValue.arrayRemove(key)).await()
                onResult(true)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { onResult(false) }
            finally { _pinBusy.value = false }
        }
    }

    fun organizeSaved(postId: String, name: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        viewModelScope.launch {
            try {
                val normalized = normalizedSavedCollection(name)
                // update, not set: never resurrect a post that was unsaved elsewhere.
                kotlinx.coroutines.withTimeout(15_000) {
                    firestore.collection("users").document(uid).collection("savedPosts").document(postId)
                    .update("collectionName", normalized).await()
                }
                onResult(true)
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { onResult(false) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { onResult(false) }
        }
    }

    private val _relationshipState = MutableStateFlow(FollowRelationshipState())
    val relationshipState: StateFlow<FollowRelationshipState> = _relationshipState

    private val _isFollowActionLoading = MutableStateFlow(false)
    val isFollowActionLoading: StateFlow<Boolean> = _isFollowActionLoading

    private val _followActionError = MutableStateFlow<String?>(null)
    val followActionError: StateFlow<String?> = _followActionError

    private val _followActionMessage = MutableStateFlow<String?>(null)
    val followActionMessage: StateFlow<String?> = _followActionMessage

    private val currentUserId = auth.currentUser?.uid ?: ""

    fun checkFollowStatus(targetUserId: String) {
        if (targetUserId == currentUserId) return
        viewModelScope.launch {
            runCatching {
                followRepository.getRelationshipState(targetUserId)
            }.onSuccess {
                _relationshipState.value = it
            }.onFailure {
                _followActionError.value = it.message ?: "No se pudo consultar el seguimiento."
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        if (_isFollowActionLoading.value) return

        viewModelScope.launch {
            _isFollowActionLoading.value = true
            _followActionError.value = null
            _followActionMessage.value = null

            runCatching {
                followRepository.toggleFollow(targetUserId)
            }.onSuccess { action ->
                _relationshipState.value = followRepository.getRelationshipState(targetUserId)
                _followActionMessage.value = when (action) {
                    FollowActionResult.FOLLOWED -> "Ahora sigues a esta cuenta."
                    FollowActionResult.UNFOLLOWED -> "Dejaste de seguir esta cuenta."
                    FollowActionResult.REQUESTED -> "Solicitud enviada."
                    FollowActionResult.REQUEST_CANCELLED -> "Solicitud cancelada."
                }
            }.onFailure {
                _followActionError.value = it.message ?: "No se pudo actualizar el seguimiento."
            }

            _isFollowActionLoading.value = false
        }
    }

    fun blockUser(targetUserId: String) {
        if (_isFollowActionLoading.value) return
        viewModelScope.launch {
            _isFollowActionLoading.value = true
            _followActionError.value = null
            runCatching { followRepository.blockUser(targetUserId) }
                .onSuccess {
                    _relationshipState.value = followRepository.getRelationshipState(targetUserId)
                    _followActionMessage.value = "Cuenta bloqueada."
                }
                .onFailure {
                    _followActionError.value = it.message ?: "No se pudo bloquear la cuenta."
                }
            _isFollowActionLoading.value = false
        }
    }

    fun unblockUser(targetUserId: String) {
        if (_isFollowActionLoading.value) return
        viewModelScope.launch {
            _isFollowActionLoading.value = true
            _followActionError.value = null
            runCatching { followRepository.unblockUser(targetUserId) }
                .onSuccess {
                    _relationshipState.value = followRepository.getRelationshipState(targetUserId)
                    _followActionMessage.value = "Cuenta desbloqueada."
                }
                .onFailure {
                    _followActionError.value = it.message ?: "No se pudo desbloquear la cuenta."
                }
            _isFollowActionLoading.value = false
        }
    }

    fun clearFollowActionError() {
        _followActionError.value = null
    }

    fun clearFollowActionMessage() {
        _followActionMessage.value = null
    }

    /**
     * Regenera la URL firmada de una imagen de post en B2 (TTL 7 días).
     * Devuelve null si falla para conservar la URL guardada.
     */
    suspend fun refreshSignedUrl(storageKey: String): String? = try {
        storage.signDownloadUrl(storageKey)
    } catch (e: Exception) {
        null
    }
}

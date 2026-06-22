package com.vivid.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.FollowActionResult
import com.vivid.app.domain.repository.FollowRelationshipState
import com.vivid.app.domain.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val followRepository: FollowRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

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
}

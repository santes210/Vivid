package com.vivid.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.domain.repository.FollowRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private val currentUserId = auth.currentUser?.uid ?: ""

    fun checkFollowStatus(targetUserId: String) {
        if (targetUserId == currentUserId) return
        viewModelScope.launch {
            _isFollowing.value = followRepository.isFollowing(targetUserId)
        }
    }

    fun toggleFollow(targetUserId: String) {
        viewModelScope.launch {
            if (_isFollowing.value) {
                followRepository.unfollowUser(targetUserId)
                _isFollowing.value = false
            } else {
                followRepository.followUser(targetUserId)
                _isFollowing.value = true
            }
        }
    }
}

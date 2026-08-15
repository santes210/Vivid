package com.vivid.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.app.domain.repository.FollowRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedUsersState(
    val userIds: Set<String> = emptySet(),
    val isLoaded: Boolean = false
)

/**
 * Fuente única y en tiempo real de las cuentas bloqueadas por la sesión actual.
 * Las pantallas esperan la primera lectura antes de mostrar contenido para evitar
 * que una cuenta bloqueada aparezca brevemente mientras Firestore responde.
 */
@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    followRepository: FollowRepository
) : ViewModel() {
    private val _state = MutableStateFlow(BlockedUsersState())
    val state: StateFlow<BlockedUsersState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            followRepository.observeBlockedUserIds().collect { ids ->
                _state.value = BlockedUsersState(userIds = ids, isLoaded = true)
            }
        }
    }
}

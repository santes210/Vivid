package com.vivid.app.presentation.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

sealed interface ChatReelState {
    data object Loading : ChatReelState
    data class Unavailable(val reason: MediaOpenFailure) : ChatReelState
    data class Ready(val video: ChatReelVideo) : ChatReelState
}

@HiltViewModel
class ChatReelViewModel @Inject constructor(private val repository: ChatReelRepository) : ViewModel() {
    private val _state = MutableStateFlow<ChatReelState>(ChatReelState.Loading)
    val state = _state.asStateFlow()
    private var request: Job? = null

    fun load(id: String, collection: String = "reels") {
        request?.cancel()
        _state.value = ChatReelState.Loading
        request = viewModelScope.launch {
            try {
                _state.value = ChatReelState.Ready(withTimeout(15_000) { repository.load(id, collection) })
            } catch (_: TimeoutCancellationException) {
                _state.value = ChatReelState.Unavailable(MediaOpenFailure.NETWORK)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = ChatReelState.Unavailable(mediaOpenFailure(error))
            }
        }
    }

    fun close() {
        request?.cancel()
        _state.value = ChatReelState.Loading
    }
}

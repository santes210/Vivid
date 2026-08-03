package com.vivid.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bus de deep links provenientes de notificaciones (FCM o locales).
 *
 * MainActivity escribe aquí tanto en `onCreate` (arranque en frío)
 * como en `onNewIntent` (app ya abierta). Compose recolecta estos
 * StateFlows y navega; así los toques de notificación también
 * funcionan cuando la app ya está en primer plano.
 */
object DeepLinkBus {

    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId.asStateFlow()

    private val _reelId = MutableStateFlow<String?>(null)
    val reelId: StateFlow<String?> = _reelId.asStateFlow()

    private val _profileUserId = MutableStateFlow<String?>(null)
    val profileUserId: StateFlow<String?> = _profileUserId.asStateFlow()

    fun emitChat(chatId: String) { _chatId.value = chatId }
    fun emitReel(reelId: String) { _reelId.value = reelId }
    fun emitProfile(userId: String) { _profileUserId.value = userId }

    /** Limpia el valor después de navegar para permitir nuevos eventos iguales. */
    fun clearChat() { _chatId.value = null }
    fun clearReel() { _reelId.value = null }
    fun clearProfile() { _profileUserId.value = null }
}

package com.vivid.shared.repository

import com.vivid.shared.model.Chat
import com.vivid.shared.model.Message
import com.vivid.shared.model.MessageType
import kotlinx.coroutines.flow.Flow

/**
 * Interfaz del repositorio de chat.
 * Define el contrato que ambas plataformas (Android/iOS) deben implementar.
 *
 * Las implementaciones usan Firebase Firestore para la sincronización en
 * tiempo real y una base de datos local para el caché offline.
 */
interface ChatRepository {

    /** Flujo de todos los chats del usuario actual, ordenados por actividad. */
    fun getChatsFlow(): Flow<List<Chat>>

    /** Flujo de mensajes de un chat específico, ordenados cronológicamente. */
    fun getMessagesFlow(chatId: String): Flow<List<Message>>

    /**
     * Crea o recupera un chat entre el usuario actual y otro usuario.
     * @return El ID del chat (formato: "uidA_uidB" ordenados alfabéticamente).
     */
    suspend fun createOrGetChat(
        otherUserId: String,
        otherUserName: String,
        avatarUrl: String,
        avatarBase64: String = ""
    ): String

    /**
     * Envía un mensaje de texto a un chat.
     */
    suspend fun sendMessage(
        chatId: String,
        text: String,
        receiverId: String,
        replyToStoryId: String = ""
    )

    /**
     * Envía una nota de voz a un chat.
     */
    suspend fun sendVoiceMessage(
        chatId: String,
        voiceFilePath: String,
        voiceDurationMs: Long,
        receiverId: String
    )

    /**
     * Envía una imagen a un chat.
     */
    suspend fun sendImageMessage(
        chatId: String,
        imageFilePath: String,
        receiverId: String
    )

    /**
     * Marca un mensaje como leído.
     */
    suspend fun markMessageAsRead(chatId: String, messageId: String)

    /**
     * Marca todos los mensajes de un chat como leídos.
     */
    suspend fun markAllMessagesAsRead(chatId: String)

    /**
     * Añade o quita una reacción a un mensaje.
     */
    suspend fun toggleReaction(chatId: String, messageId: String, reaction: String)

    /**
     * Edita el texto de un mensaje existente.
     */
    suspend fun editMessage(chatId: String, messageId: String, newText: String)

    /**
     * Elimina un mensaje (solo el emisor puede eliminar los suyos).
     */
    suspend fun deleteMessage(chatId: String, messageId: String)

    /**
     * Carga mensajes históricos desde Firestore para un chat.
     */
    suspend fun backfillMessages(chatId: String, pageSize: Int = 300, maxPages: Int = 10)

    /**
     * Construye el ID de un chat a partir de dos UIDs.
     * Siempre ordena alfabéticamente para garantizar consistencia.
     */
    companion object {
        fun buildChatId(userA: String, userB: String): String =
            listOf(userA, userB).sorted().joinToString("_")
    }
}

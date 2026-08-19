package com.vivid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vivid.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearMessages(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    /** Último timestamp de mensaje cacheado para el chat (sync incremental). */
    @Query("SELECT MAX(timestamp) FROM messages WHERE chatId = :chatId")
    suspend fun getMaxTimestamp(chatId: String): Long?

    /** Actualiza estado de leído/entregado (read receipts). */
    @Query("UPDATE messages SET isRead = :isRead, isDelivered = :isDelivered WHERE id = :messageId")
    suspend fun updateReadState(messageId: String, isRead: Boolean, isDelivered: Boolean)

    /** Actualiza la reacción de un mensaje cacheado. */
    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateReaction(messageId: String, reaction: String)

    /** Persiste una edición de texto (type=text) con su marca lastEditedAt. */
    @Query("UPDATE messages SET text = :text, lastEditedAt = :lastEditedAt WHERE id = :messageId")
    suspend fun updateText(messageId: String, text: String, lastEditedAt: Long)
}
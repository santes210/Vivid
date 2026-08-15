package com.vivid.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.dao.ReelDao
import com.vivid.app.data.local.dao.StoryDao
import com.vivid.app.data.local.dao.UserDao
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.data.local.entity.ReelEntity
import com.vivid.app.data.local.entity.StoryEntity
import com.vivid.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        PostEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        StoryEntity::class,
        ReelEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class VividDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun storyDao(): StoryDao
    abstract fun reelDao(): ReelDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN imageKey TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN isDelivered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN voiceUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN voiceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN voiceDurationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN replyToStoryId TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Expandir tabla de posts cacheados con columnas de B2/música
                db.execSQL("ALTER TABLE posts ADD COLUMN storageKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN videoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN isVideo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE posts ADD COLUMN musicTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN musicArtist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN musicAssetFile TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN musicUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN musicStorageKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE posts ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
                // Crear tabla de stories cacheadas
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stories (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL DEFAULT '',
                        username TEXT NOT NULL DEFAULT '',
                        avatarUrl TEXT NOT NULL DEFAULT '',
                        avatarBase64 TEXT NOT NULL DEFAULT '',
                        mediaUrl TEXT NOT NULL DEFAULT '',
                        mediaBase64 TEXT NOT NULL DEFAULT '',
                        videoUrl TEXT NOT NULL DEFAULT '',
                        thumbnailUrl TEXT NOT NULL DEFAULT '',
                        type TEXT NOT NULL DEFAULT 'photo',
                        caption TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        expiresAt INTEGER NOT NULL DEFAULT 0,
                        isPrivate INTEGER NOT NULL DEFAULT 0,
                        storageKey TEXT NOT NULL DEFAULT '',
                        viewersCount INTEGER NOT NULL DEFAULT 0,
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                // Crear tabla de reels cacheados
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reels (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL DEFAULT '',
                        username TEXT NOT NULL DEFAULT '',
                        userAvatar TEXT NOT NULL DEFAULT '',
                        videoUrl TEXT NOT NULL DEFAULT '',
                        thumbnailUrl TEXT NOT NULL DEFAULT '',
                        caption TEXT NOT NULL DEFAULT '',
                        likes INTEGER NOT NULL DEFAULT 0,
                        commentsCount INTEGER NOT NULL DEFAULT 0,
                        timestamp INTEGER NOT NULL DEFAULT 0,
                        isPrivate INTEGER NOT NULL DEFAULT 0,
                        storageKey TEXT NOT NULL DEFAULT '',
                        cachedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
        /**
         * v4 → v5: caché de mensajes/chats.
         * - messages: columna reaction (emoji del mensaje)
         * - chats: columnas lastMessageSenderId, lastMessageType, avatarBase64, cachedAt
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reaction TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageSenderId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN lastMessageType TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE chats ADD COLUMN avatarBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN cachedAt INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}

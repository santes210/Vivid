package com.vivid.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.dao.UserDao
import com.vivid.app.data.local.entity.ChatEntity
import com.vivid.app.data.local.entity.MessageEntity
import com.vivid.app.data.local.entity.PostEntity
import com.vivid.app.data.local.entity.UserEntity

@Database(
    entities = [UserEntity::class, PostEntity::class, ChatEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VividDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
package com.vivid.app.di

import android.content.Context
import androidx.room.Room
import com.vivid.app.data.local.VividDatabase
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VividDatabase {
        return Room.databaseBuilder(
            context,
            VividDatabase::class.java,
            "vivid_database"
        ).build()
    }

    @Provides
    fun provideUserDao(db: VividDatabase): UserDao = db.userDao()

    @Provides
    fun providePostDao(db: VividDatabase): PostDao = db.postDao()

    @Provides
    fun provideChatDao(db: VividDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: VividDatabase): MessageDao = db.messageDao()
}
package com.vivid.app.di

import android.content.Context
import androidx.room.Room
import com.vivid.app.data.local.VividDatabase
import com.vivid.app.data.local.dao.ChatDao
import com.vivid.app.data.local.dao.MessageDao
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.dao.ReelDao
import com.vivid.app.data.local.dao.StoryDao
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
        )
            .addMigrations(
                VividDatabase.MIGRATION_1_2,
                VividDatabase.MIGRATION_2_3,
                VividDatabase.MIGRATION_3_4,
                VividDatabase.MIGRATION_4_5,
                VividDatabase.MIGRATION_5_6
            )
            // OJO: el fallback destructivo borra TODO el caché local ante una
            // migración desconocida. Es aceptable para contenido cacheable,
            // pero perder el historial de chats ofende; con exportSchema=true
            // y tests de migración se puede eliminar este fallback.
            .fallbackToDestructiveMigrationOnDowngrade()
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(db: VividDatabase): UserDao = db.userDao()

    @Provides
    fun providePostDao(db: VividDatabase): PostDao = db.postDao()

    @Provides
    fun provideChatDao(db: VividDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: VividDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideStoryDao(db: VividDatabase): StoryDao = db.storyDao()

    @Provides
    fun provideReelDao(db: VividDatabase): ReelDao = db.reelDao()
}

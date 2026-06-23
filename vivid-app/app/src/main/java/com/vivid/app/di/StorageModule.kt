package com.vivid.app.di

import com.vivid.app.BuildConfig
import com.vivid.app.data.storage.CloudFunctionsStorageProvider
import com.vivid.app.data.storage.StorageProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Proveedor de [StorageProvider].
 *
 * Usa CloudFunctionsStorageProvider con la URL definida en BuildConfig.
 *
 * CF_BASE_URL se lee de BuildConfig (que viene de BuildConfigSecrets.kt).
 * En modo inseguro esta URL esta hardcodeada; en modo seguro se inyecta
 * desde GitHub Secrets via gradle.properties.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideStorageProvider(): StorageProvider {
        return CloudFunctionsStorageProvider(
            functionBaseUrl = BuildConfig.CF_BASE_URL
        )
    }
}

package com.vivid.app.di

import com.vivid.app.data.storage.BackblazeStorageProvider
import com.vivid.app.data.storage.StorageProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Proveedor de [StorageProvider].
 *
 * Usa BackblazeStorageProvider en MODO DIRECTO con credenciales embebidas.
 * Funciona con bucket PRIVADO (genera URLs firmadas, no necesita bucket
 * público ni tarjeta de crédito en Backblaze).
 *
 * Las claves vienen de BuildConfigSecrets.kt.
 *
 * Para migrar a MODO SEGURO: desplegar la Cloud Function de
 * /cloud-function y cambiar la implementación aquí a
 * CloudFunctionsStorageProvider(BuildConfig.CF_BASE_URL).
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideStorageProvider(): StorageProvider {
        return BackblazeStorageProvider(
            keyId = BuildConfigSecrets.B2_KEY_ID,
            applicationKey = BuildConfigSecrets.B2_APPLICATION_KEY,
            bucketId = BuildConfigSecrets.B2_BUCKET_ID,
            bucketName = BuildConfigSecrets.B2_BUCKET_NAME
        )
    }
}

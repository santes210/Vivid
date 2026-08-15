package com.vivid.app.di

import com.vivid.app.BuildConfig
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
 * Usa BackblazeStorageProvider en MODO DIRECTO (temporal). Las claves B2 ya
 * NO se commitearn: las inyecta Gradle en [BuildConfig] desde variables de
 * entorno, -P o local.properties (ver app/build.gradle.kts y
 * BuildConfigSecrets.kt.example). Cualquier APK construido así sigue
 * conteniendo las claves, así que:
 *   - nunca subir APKs como artefacto público de GitHub Actions, y
 *   - migrar a la Cloud Function de /cloud-function cuanto antes.
 *
 * Para migrar a MODO SEGURO: desplegar la Cloud Function y cambiar la
 * implementación aquí a CloudFunctionsStorageProvider(BuildConfig.CF_BASE_URL).
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideStorageProvider(): StorageProvider {
        require(
            BuildConfig.B2_KEY_ID.isNotBlank() &&
            BuildConfig.B2_APPLICATION_KEY.isNotBlank() &&
            BuildConfig.B2_BUCKET_ID.isNotBlank() &&
            BuildConfig.B2_BUCKET_NAME.isNotBlank()
        ) {
            "Claves B2 no configuradas. Define B2_KEY_ID, B2_APPLICATION_KEY, " +
            "B2_BUCKET_ID y B2_BUCKET_NAME como variables de entorno, como " +
            "propiedades de Gradle (-Pb2KeyId=...) o en local.properties " +
            "(b2.keyId, b2.applicationKey, b2.bucketId, b2.bucketName). " +
            "Ver SECURITY.md."
        }

        return BackblazeStorageProvider(
            keyId = BuildConfig.B2_KEY_ID,
            applicationKey = BuildConfig.B2_APPLICATION_KEY,
            bucketId = BuildConfig.B2_BUCKET_ID,
            bucketName = BuildConfig.B2_BUCKET_NAME
        )
    }
}

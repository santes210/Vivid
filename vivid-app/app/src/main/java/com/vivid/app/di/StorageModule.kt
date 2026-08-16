package com.vivid.app.di

import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.BuildConfig
import com.vivid.app.data.storage.StorageProvider
import com.vivid.app.data.storage.WorkerStorageProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Proveedor de [StorageProvider].
 *
 * MODO SEGURO: la app no contiene ninguna credencial de Backblaze B2.
 * Las claves viven como secretos cifrados dentro del Cloudflare Worker, que
 * autentica cada petición con el ID token de Firebase del usuario.
 *
 * La URL del Worker se inyecta en tiempo de compilación:
 *   ./gradlew assembleRelease -PvividWorkerUrl=https://vivid-push.<cuenta>.workers.dev
 * o mediante la variable de entorno VIVID_WORKER_URL en GitHub Actions.
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideStorageProvider(auth: FirebaseAuth): StorageProvider {
        check(BuildConfig.WORKER_URL.isNotBlank()) {
            "WORKER_URL no está configurada. Compila con -PvividWorkerUrl=https://... " +
                "o define VIVID_WORKER_URL en el entorno."
        }
        return WorkerStorageProvider(
            workerBaseUrl = BuildConfig.WORKER_URL,
            auth = auth
        )
    }
}

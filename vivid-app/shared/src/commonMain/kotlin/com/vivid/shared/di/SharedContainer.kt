package com.vivid.shared.di

import com.vivid.shared.repository.AuthRepository
import com.vivid.shared.repository.ChatRepository
import com.vivid.shared.repository.ContentRepository
import com.vivid.shared.repository.FollowRepository
import com.vivid.shared.repository.StorageProvider
import com.vivid.shared.repository.StoryRepository
import com.vivid.shared.repository.UserRepository

/**
 * Contenedor de dependencias del módulo compartido.
 *
 * Cada plataforma inyecta sus implementaciones concretas al crear la instancia.
 * Android usa Hilt; iOS usa una inicialización manual desde Swift.
 *
 * En Android, este container se crea dentro de un @Module de Hilt.
 * En iOS, se crea en AppDelegate/SceneDelegate con las implementaciones
 * que envuelven los SDKs nativos de Firebase.
 */
class SharedContainer(
    val authRepository: AuthRepository,
    val userRepository: UserRepository,
    val chatRepository: ChatRepository,
    val followRepository: FollowRepository,
    val contentRepository: ContentRepository,
    val storyRepository: StoryRepository,
    val storageProvider: StorageProvider
) {
    companion object {
        /** Instancia global (singleton) accesible desde ambas plataformas. */
        @Volatile
        private var instance: SharedContainer? = null

        fun initialize(container: SharedContainer): SharedContainer {
            instance = container
            return container
        }

        fun get(): SharedContainer =
            instance ?: error("SharedContainer no ha sido inicializado. Llama a SharedContainer.initialize() primero.")
    }
}

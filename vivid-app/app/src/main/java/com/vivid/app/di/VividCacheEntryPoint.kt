package com.vivid.app.di

import coil.ImageLoader
import com.vivid.app.data.local.VividDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Punto de acceso a dependencias singleton (Database + ImageLoader) para
 * contextos no-Hilt, como el botón "Borrar caché" de los ajustes.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VividCacheEntryPoint {
    fun database(): VividDatabase
    fun imageLoader(): ImageLoader
}
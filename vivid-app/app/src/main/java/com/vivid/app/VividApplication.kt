package com.vivid.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import com.vivid.app.util.SettingsManager
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
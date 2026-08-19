package com.vivid.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.vivid.app.util.NetworkMonitor
import com.vivid.app.util.PushSender
import com.vivid.app.util.SettingsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        // LocaleManager carga idioma y escala tipográfica desde SharedPreferences.
        // MainActivity.attachBaseContext() los lee en cada arranque para
        // servir los recursos en el idioma elegido y escalar la tipografía.
        com.vivid.app.util.LocaleManager.init(this)

        // Crashlytics + Performance Monitoring (gratis en el plan Spark de
        // Firebase; sin pasos de pago). La captura de datos queda activa solo
        // en builds release para no mezclar sesiones de desarrollo con las
        // métricas de producción. Los crashes fatales se capturan solos;
        // los errores no fatales pasan por util/CrashReporter.
        val telemetryEnabled = !BuildConfig.DEBUG
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(telemetryEnabled)
        FirebasePerformance.getInstance().isPerformanceCollectionEnabled = telemetryEnabled

        // Monitor de conectividad para los banners "Sin conexión" de la UI.
        // Vive en el applicationContext: no depende del ciclo de vida de
        // ninguna Activity.
        NetworkMonitor.init(this)

        PushSender.initialize(this)
    }

    override fun onTerminate() {
        com.vivid.app.util.ExoPlayerPool.releaseAll()
        super.onTerminate()
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}

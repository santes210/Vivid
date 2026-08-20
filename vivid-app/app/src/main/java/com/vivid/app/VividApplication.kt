package com.vivid.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.vivid.app.util.NetworkMonitor
import com.vivid.app.util.PushSender
import com.vivid.app.util.SettingsManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), SingletonImageLoader.Factory {

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

    // Coil 3 detecta esta factory desde Application y la usa también para
    // AsyncImage y las precargas. Devolvemos el singleton gestionado por Hilt
    // para que toda la app comparta el mismo caché de memoria y disco.
    override fun newImageLoader(context: Context): ImageLoader = imageLoader
}

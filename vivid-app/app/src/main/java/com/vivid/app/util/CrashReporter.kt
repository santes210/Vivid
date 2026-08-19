package com.vivid.app.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.vivid.app.BuildConfig

/**
 * Puente hacia Firebase Crashlytics para errores NO fatales.
 *
 * Los crashes fatales los captura Crashlytics solo (sin tocar este archivo);
 * esto sirve para los `catch` que hoy solo escriben a Logcat y desaparecen.
 *
 * - Debug: solo Logcat (no ensucia la consola de producción con builds de desarrollo).
 * - Release: Logcat + Crashlytics (recordException / log).
 *
 * El `runCatching` alrededor de Firebase es a propósito: en tests de JVM no
 * hay FirebaseApp inicializado y reportar un error no debe romper el flujo
 * que ya venía fallando.
 */
object CrashReporter {

    fun recordNonFatal(tag: String, throwable: Throwable, context: String? = null) {
        Log.e(tag, context ?: "Error no fatal", throwable)
        if (BuildConfig.DEBUG) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            context?.let { crashlytics.log("$tag: $it") }
            crashlytics.recordException(throwable)
        }
    }

    fun log(tag: String, message: String) {
        if (BuildConfig.DEBUG) return
        runCatching { FirebaseCrashlytics.getInstance().log("$tag: $message") }
    }
}

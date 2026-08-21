package com.vivid.shared.util

import android.os.Build
import android.util.Log
import java.util.UUID

actual class Platform actual constructor() {
    actual val name: String = "Android"
    actual val version: String = "${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
    actual val deviceId: String = Build.BOARD + "-" + Build.DEVICE + "-" + Build.SERIAL
}

actual object Clock {
    actual fun currentTimeMillis(): Long = System.currentTimeMillis()
}

actual object VividLog {
    actual fun d(tag: String, message: String) { Log.d(tag, message) }
    actual fun i(tag: String, message: String) { Log.i(tag, message) }
    actual fun w(tag: String, message: String) { Log.w(tag, message) }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}

actual fun generateUUID(): String = UUID.randomUUID().toString()

actual fun encodeBase64(bytes: ByteArray): String =
    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

actual fun decodeBase64(encoded: String): ByteArray =
    android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)

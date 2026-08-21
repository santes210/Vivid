package com.vivid.shared.util

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi

actual class Platform actual constructor() {
    actual val name: String = "iOS"
    actual val version: String = UIDevice.currentDevice.systemVersion
    actual val deviceId: String = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown"
}

actual object Clock {
    actual fun currentTimeMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000).toLong()
}

actual object VividLog {
    actual fun d(tag: String, message: String) { println("[$tag] DEBUG: $message") }
    actual fun i(tag: String, message: String) { println("[$tag] INFO: $message") }
    actual fun w(tag: String, message: String) { println("[$tag] WARN: $message") }
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("[$tag] ERROR: $message")
        throwable?.let { println("[$tag] ${it.message}") }
    }
}

actual fun generateUUID(): String = NSUUID().UUIDString

@OptIn(ExperimentalNativeApi::class)
actual fun encodeBase64(bytes: ByteArray): String {
    val nsData = kotlinx.cinterop.allocArrayOf(bytes).let { ptr ->
        platform.Foundation.NSData.create(bytes = ptr, length = bytes.size.toULong())
    }
    return nsData.base64EncodedStringWithOptions(0u)
}

@OptIn(ExperimentalNativeApi::class)
actual fun decodeBase64(encoded: String): ByteArray {
    val nsData = platform.Foundation.NSData.create(
        base64EncodedString = encoded,
        options = 0u
    )
    val length = nsData.length.toInt()
    val bytes = ByteArray(length)
    kotlinx.cinterop.memScoped {
        val ptr = nsData.bytes
        for (i in 0 until length) {
            bytes[i] = ptr!!.reinterpret<kotlinx.cinterop.ByteVar>()[i]
        }
    }
    return bytes
}

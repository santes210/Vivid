package com.vivid.app.util

import android.content.Context
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Helper OPCIONAL para whitelist de batería vía Shizuku.
 *
 * Requiere que el usuario instale Shizuku:
 *   https://shizuku.rikka.app/
 *
 * Una vez instalado y autorizado, ejecuta comandos con privilegios
 * ADB para que Android no mate el proceso de notificaciones.
 *
 * Si Shizuku no está disponible, las notificaciones funcionan igual
 * mediante el Foreground Service (NotificationForegroundService).
 */
object ShizukuBatteryHelper {
    private const val TAG = "ShizukuBattery"

    private val batteryWhitelistListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            Log.d(TAG, "Shizuku permiso result: requestCode=$requestCode grantResult=$grantResult")
        }
    }

    /**
     * Intenta whitelistear la app de optimización de batería.
     * Se llama desde NotificationForegroundService.onStartCommand()
     */
    fun tryWhitelist(context: Context) {
        try {
            // ── Verificar Shizuku ──
            if (!isShizukuReady()) {
                Log.i(TAG, "Shizuku no disponible. Las notificaciones usan solo Foreground Service.")
                logBatteryStatus(context)
                return
            }

            val pkg = context.packageName

            // ── Whitelist de Doze ──
            runShizukuCommand("dumpsys deviceidle whitelist +$pkg") { result ->
                Log.i(TAG, "Doze whitelist: $result")
            }

            // ── Standby bucket activo (Android 11+) ──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runShizukuCommand("am set-standby-bucket $pkg active") { result ->
                    Log.i(TAG, "Standby bucket: $result")
                }
            }

            Log.i(TAG, "✅ Whitelist de batería aplicado vía Shizuku")

        } catch (e: Exception) {
            Log.w(TAG, "Error en tryWhitelist: ${e.message}")
        }
    }

    private fun isShizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() &&
            (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED)
        } catch (e: Exception) {
            false
        }
    }

    private fun runShizukuCommand(command: String, onResult: (String) -> Unit) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            onResult(output)
        } catch (e: Exception) {
            Log.w(TAG, "Comando Shizuku falló: $command — ${e.message}")
        }
    }

    private fun logBatteryStatus(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val ignored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            Log.i(TAG, "Battery optimization ignored: $ignored")
        } catch (_: Exception) {}
    }
}

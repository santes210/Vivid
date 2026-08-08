package com.vivid.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Grabador de notas de voz — Material You 3 ready, simple y robusto.
 *
 * Usa MediaRecorder con AAC (M4A) para buena compresión y compatibilidad.
 * El archivo vive en cacheDir y se borra tras subir a B2 (igual que imágenes).
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(): File? {
        try {
            stopRecording(cancel = true)
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            r.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = r
            outputFile = file
            startTimeMs = System.currentTimeMillis()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            recorder?.release()
            recorder = null
            outputFile = null
            return null
        }
    }

    /**
     * Detiene la grabación.
     * @param cancel si es true borra el archivo.
     * @return archivo si es grabación válida (> 800ms y existe)
     */
    fun stopRecording(cancel: Boolean = false): File? {
        val r = recorder
        val f = outputFile
        recorder = null
        outputFile = null
        try {
            r?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            try { r?.release() } catch (_: Exception) {}
        }
        if (cancel) {
            f?.delete()
            return null
        }
        if (f == null || !f.exists() || f.length() < 1024) {
            f?.delete()
            return null
        }
        val elapsed = System.currentTimeMillis() - startTimeMs
        if (elapsed < 800) {
            f.delete()
            return null
        }
        return f
    }

    fun isRecording(): Boolean = recorder != null

    fun getElapsedMs(): Long =
        if (isRecording()) System.currentTimeMillis() - startTimeMs else 0L
}

/**
 * Helper para formatear duración mm:ss
 */
fun formatVoiceDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

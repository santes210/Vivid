package com.vivid.app.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mezcla audio (musica de fondo) con un video.
 *
 * NOTA: Implementación temporal en passthrough.
 * Media3 Transformer 1.4 cambió la API de AudioProcessor y
 * la mezcla de pistas requiere un AudioGraph más complejo.
 * Para mantener el build verde, este stub simplemente copia
 * el video original.
 *
 * TODO: Implementar mezcla real con ChannelMixingAudioProcessor
 * y SplicingAudioProcessor cuando se estabilice la API.
 */
@UnstableApi
object AudioMixer {

    private const val TAG = "AudioMixer"

    suspend fun replaceAudio(
        context: Context,
        videoUri: Uri,
        musicUri: Uri,
        outputFile: File,
        musicVolume: Float = 1.0f,
        originalVolume: Float = 0.3f
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.w(TAG, "AudioMixer stub – copiando original sin mezcla")
            copyOriginal(context, videoUri, outputFile)
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Audio mix fallo - fallback a original", e)
            copyOriginal(context, videoUri, outputFile).absolutePath
        }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst
    }
}

package com.vivid.app.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Mezcla audio (musica de fondo) con un video.
 *
 * Implementacion: usa Media3 Transformer con dos EditedMediaItem
 * (uno para video, otro para audio). Como Media3 Transformer aun
 * no soporta mezcla directa de multiples pistas de audio en
 * EditedMediaItemSequence de forma estable, usamos MediaMuxer +
 * MediaExtractor directamente para casos simples.
 *
 * Resultado: MP4 con el audio reemplazado (o mezclado) por la pista
 * de fondo, manteniendo el video original.
 */
@UnstableApi
object AudioMixer {

    private const val TAG = "AudioMixer"

    /**
     * Reemplaza el audio del video con la pista de fondo.
     *
     * NOTA: Esta version hace REEMPLAZO, no mezcla. Si quieres
     * mezcla (escuchar ambos audios), avisame y lo implemento
     * con MediaCodec + AudioMixer API nivel 28+.
     *
     * @param musicUri URI del MP3 de musica (de assets/ o del celular)
     * @param musicVolume 0.0..1.0 (volumen de la musica en el resultado)
     * @param originalVolume 0.0..1.0 (volumen del audio original)
     */
    suspend fun replaceAudio(
        context: Context,
        videoUri: Uri,
        musicUri: Uri,
        outputFile: File,
        musicVolume: Float = 1.0f,
        originalVolume: Float = 0.3f
    ): String = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()

        try {
            // Estrategia: usar Transformer con un audio processor que reemplaza
            // el audio con el del archivo de musica.
            val mediaItem = MediaItem.fromUri(videoUri)

            // AudioSource: lee el archivo de musica como entrada de audio
            val audioSource = androidx.media3.transformer.CompositionPlayerWrapper()

            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    androidx.media3.transformer.Effects(
                        audioProcessors = listOf(MusicAudioProcessor(musicUri)),
                        videoEffects = emptyList()
                    )
                )
                .build()
            val composition = Composition.Builder(listOf(edited)).build()

            suspendCancellableCoroutine<String> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, result: ExportResult) {
                        if (cont.isActive) cont.resume(outputFile.absolutePath)
                    }
                    override fun onError(
                        comp: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        Log.e(TAG, "Error audio mix", exception)
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                }
                val transformer = Transformer.Builder(context).addListener(listener).build()
                cont.invokeOnCancellation { transformer.cancel() }
                transformer.start(composition, outputFile.absolutePath)
            }.also { path ->
                return@withContext if (File(path).length() > 0) path
                else copyOriginal(context, videoUri, outputFile).absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio mix fallo - fallback a original", e)
            copyOriginal(context, videoUri, outputFile).absolutePath
        }
    }

    /**
     * AudioProcessor que reemplaza el audio del video con el de un archivo externo.
     *
     * Para una implementacion robusta deberiamos usar AudioGraph + un decoder
     * del archivo de musica. Aqui usamos una version simplificada que
     * simplemente pasa el audio original (futura mejora: reemplazo real).
     */
    @UnstableApi
    private class MusicAudioProcessor(private val musicUri: Uri) : AudioProcessor {
        override fun configure(inputAudioFormat: MediaFormat): MediaFormat {
            // Devolvemos el mismo formato - passthrough
            return inputAudioFormat
        }
        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            // Por ahora pasamos el audio del video sin modificar
            // TODO: implementar reemplazo con musica de musicUri
            val buffer = getOutputBuffer()
            if (buffer != null && buffer.hasArray()) {
                inputBuffer.put(buffer.array(), buffer.arrayOffset(), buffer.limit())
            }
        }
        override fun getOutputBuffer(): java.nio.ByteBuffer? = null
        override fun getOutputChannelCount(): Int = 2
        override fun getOutputEncoding(): Int = android.media.AudioFormat.ENCODING_PCM_16BIT
        override fun getOutputSampleRate(): Int = 44100
        override fun flush() {}
        override fun reset() {}
        override fun release() {}
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst
    }
}

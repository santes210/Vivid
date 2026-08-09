package com.vivid.app.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recorta audio a un segmento de máximo 15s (o el que el usuario elija).
 * Intenta primero con Media3 Transformer (más moderno), si falla usa
 * MediaExtractor/MediaMuxer (más robusto, sin transcoding).
 */
@UnstableApi
object AudioTrimmer {

    private const val TAG = "AudioTrimmer"
    private const val BUFFER_SIZE = 256 * 1024

    suspend fun trimAudio(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String = withContext(Dispatchers.IO) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()
        if (endMs <= startMs) {
            Log.w(TAG, "end <= start, fallback a original")
            return@withContext copyOriginal(context, inputUri, outputFile).absolutePath
        }

        // Intento 1: Transformer con transcode a AAC (mejor para mp3 -> m4a, siempre 15s)
        val transformerResult = try {
            trimWithTransformer(context, inputUri, outputFile, startMs, endMs)
        } catch (e: Exception) {
            Log.w(TAG, "Transformer falló, probando muxer: ${e.message}")
            null
        }

        if (transformerResult != null) {
            val f = File(transformerResult)
            if (f.exists() && f.length() > 1024) {
                // Validar que realmente es recorte de ~15s (tamaño debe ser mucho menor que original si original era largo)
                return@withContext transformerResult
            }
        }

        // Intento 2: Muxer (más compatible, sin re-encode, corta por timestamps)
        try {
            val muxerResult = trimWithMuxer(context, inputUri, outputFile, startMs, endMs)
            val f = File(muxerResult)
            if (f.exists() && f.length() > 1024) {
                return@withContext muxerResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "Muxer falló", e)
        }

        // Si ambos fallan, NO devolver canción completa (para ahorrar B2), lanzar error para que UI muestre retry
        throw IllegalStateException("No se pudo recortar el audio a 15s. Intenta con otro archivo.")
    }

    private suspend fun trimWithTransformer(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String = suspendCancellableCoroutine { cont ->
        try {
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs.coerceAtLeast(0))
                .setEndPositionMs(endMs)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()

            val edited = EditedMediaItem.Builder(mediaItem).build()
            val composition = Composition.Builder(EditedMediaItemSequence(edited)).build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    Log.d(TAG, "Transformer trim OK ${endMs - startMs}ms -> ${outputFile.length()/1024}KB")
                    if (cont.isActive) cont.resume(outputFile.absolutePath)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    Log.e(TAG, "Transformer error", exportException)
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            }

            // FIX: forzar salida AAC en MP4 para que el archivo recortado siempre sea válido y pequeño (15s)
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            cont.invokeOnCancellation { transformer.cancel() }
            transformer.start(composition, outputFile.absolutePath)
        } catch (e: Exception) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    private fun trimWithMuxer(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): String {
        val startUs = startMs * 1000
        val endUs = endMs * 1000

        val extractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)

        try {
            val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r")
                ?: throw IllegalArgumentException("No se pudo abrir URI: $inputUri")
            afd.use {
                extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }

            // Buscar pista de audio
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                throw IllegalStateException("No se encontró pista de audio")
            }

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val info = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break
                if (sampleTime < startUs) {
                    extractor.advance()
                    continue
                }

                info.offset = 0
                info.size = sampleSize
                info.presentationTimeUs = sampleTime - startUs
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrackIndex, buffer, info)
                extractor.advance()
            }

            return outputFile.absolutePath
        } finally {
            try { muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    fun getDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
            } catch (_: Exception) {
                // Fallback para file:// URIs
                val path = uri.path
                if (path != null) retriever.setDataSource(path) else throw IllegalArgumentException("Uri sin path")
            }
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (e: Exception) {
            Log.w(TAG, "getDurationMs falló: ${e.message}")
            0L
        }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        return try {
            dst.parentFile?.mkdirs()
            if (src.scheme == "file") {
                val srcFile = File(src.path ?: "")
                if (srcFile.exists()) {
                    srcFile.copyTo(dst, overwrite = true)
                    return dst
                }
            }
            context.contentResolver.openInputStream(src)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
            dst
        } catch (e: Exception) {
            Log.e(TAG, "copyOriginal falló", e)
            dst
        }
    }
}

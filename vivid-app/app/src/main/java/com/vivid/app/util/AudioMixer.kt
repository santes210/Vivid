package com.vivid.app.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reemplaza el audio de un video por una pista externa.
 *
 * Nota:
 * - Esta implementación ya no es stub: genera un MP4 nuevo con el video original
 *   y la pista de audio elegida por el usuario.
 * - `musicVolume` y `originalVolume` se conservan en la firma por compatibilidad,
 *   pero la mezcla de volumenes finos queda fuera del alcance del muxing simple.
 * - Si el audio externo dura menos que el video, el video continuará en silencio
 *   al terminar la pista.
 */
@UnstableApi
object AudioMixer {

    private const val TAG = "AudioMixer"
    private const val BUFFER_SIZE = 512 * 1024

    suspend fun replaceAudio(
        context: Context,
        videoUri: Uri,
        musicUri: Uri,
        outputFile: File,
        musicVolume: Float = 1.0f,
        originalVolume: Float = 0.3f
    ): String = withContext(Dispatchers.IO) {
        try {
            if (outputFile.exists()) outputFile.delete()
            Log.d(
                TAG,
                "replaceAudio -> musicVolume=$musicVolume, originalVolume=$originalVolume"
            )
            muxVideoWithExternalAudio(context, videoUri, musicUri, outputFile)
            if (outputFile.exists() && outputFile.length() > 0L) {
                outputFile.absolutePath
            } else {
                Log.w(TAG, "Output vacío; fallback a original")
                copyOriginal(context, videoUri, outputFile).absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio replace falló - fallback a original", e)
            copyOriginal(context, videoUri, outputFile).absolutePath
        }
    }

    private fun muxVideoWithExternalAudio(
        context: Context,
        videoUri: Uri,
        musicUri: Uri,
        outputFile: File
    ) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val buffer = ByteArray(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()

        try {
            setExtractorDataSource(context, videoExtractor, videoUri)
            setExtractorDataSource(context, audioExtractor, musicUri)

            val videoTrackIndex = findTrack(videoExtractor, "video/")
            val audioTrackIndex = findTrack(audioExtractor, "audio/")

            if (videoTrackIndex == -1 || audioTrackIndex == -1) {
                error("No se encontraron pistas compatibles de video/audio")
            }

            videoExtractor.selectTrack(videoTrackIndex)
            audioExtractor.selectTrack(audioTrackIndex)

            val muxerVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrackIndex))
            val muxerAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrackIndex))
            applyOrientationHintIfPossible(context, videoUri, muxer)

            muxer.start()

            val videoDurationUs = extractDurationUs(videoExtractor.getTrackFormat(videoTrackIndex))
            copyTrack(videoExtractor, muxer, muxerVideoTrack, buffer, info, maxDurationUs = Long.MAX_VALUE)
            copyTrack(audioExtractor, muxer, muxerAudioTrack, buffer, info, maxDurationUs = videoDurationUs)
        } finally {
            try {
                muxer.stop()
            } catch (_: Exception) {
            }
            try {
                muxer.release()
            } catch (_: Exception) {
            }
            try {
                videoExtractor.release()
            } catch (_: Exception) {
            }
            try {
                audioExtractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        buffer: ByteArray,
        info: MediaCodec.BufferInfo,
        maxDurationUs: Long
    ) {
        while (true) {
            val sampleSize = extractor.readSampleData(java.nio.ByteBuffer.wrap(buffer), 0)
            if (sampleSize < 0) break

            val sampleTime = extractor.sampleTime
            if (sampleTime < 0L) break
            if (sampleTime > maxDurationUs) break

            info.offset = 0
            info.size = sampleSize
            info.presentationTimeUs = sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, java.nio.ByteBuffer.wrap(buffer, 0, sampleSize), info)
            extractor.advance()
        }
    }

    private fun setExtractorDataSource(context: Context, extractor: MediaExtractor, uri: Uri) {
        val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: error("No se pudo abrir URI: $uri")
        afd.use {
            extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return index
        }
        return -1
    }

    private fun extractDurationUs(format: MediaFormat): Long {
        return if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else {
            Long.MAX_VALUE
        }
    }

    private fun applyOrientationHintIfPossible(context: Context, videoUri: Uri, muxer: MediaMuxer) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            retriever.release()
            if (rotation != 0) muxer.setOrientationHint(rotation)
        }.onFailure {
            Log.w(TAG, "No se pudo leer orientación del video", it)
        }
    }

    private fun copyOriginal(context: Context, src: Uri, dst: File): File {
        context.contentResolver.openInputStream(src)?.use { input ->
            dst.outputStream().use { output -> input.copyTo(output) }
        }
        return dst
    }
}

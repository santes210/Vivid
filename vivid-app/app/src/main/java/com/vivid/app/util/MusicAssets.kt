package com.vivid.app.util

import android.content.res.AssetManager
import java.io.InputStream

/**
 * Packed soundtrack helpers.
 *
 * Demo tracks used to live as uncompressed WAV in `assets/music/`, which
 * bloated the APK. They now ship as MP3 and any leftover `.wav` path
 * (Firestore / Room / older posts) is remapped to the compressed file.
 */
object MusicAssets {
    const val COMPRESSED_EXTENSION = "mp3"

    private val packedExtensions = setOf("ogg", "mp3", "m4a", "aac", "wav")

    fun isPackedAudio(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in packedExtensions
    }

    /**
     * Maps a historical `.wav` asset path to the compressed file we ship.
     * Other extensions are returned unchanged.
     */
    fun resolvePackedPath(path: String): String {
        val trimmed = path.trim().trimStart('/')
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.endsWith(".wav", ignoreCase = true)) {
            trimmed.substring(0, trimmed.length - 4) + ".$COMPRESSED_EXTENSION"
        } else {
            trimmed
        }
    }

    /**
     * Opens [path] from assets, falling back to the original name if the
     * remapped file is missing (keeps custom/debug WAV drops working).
     */
    fun openAsset(assets: AssetManager, path: String): InputStream {
        val resolved = resolvePackedPath(path)
        return try {
            assets.open(resolved)
        } catch (first: Exception) {
            if (resolved != path) assets.open(path) else throw first
        }
    }
}

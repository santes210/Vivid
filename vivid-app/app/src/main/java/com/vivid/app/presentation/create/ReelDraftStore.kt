package com.vivid.app.presentation.create

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal data class ReelDraft(
    val video: Uri, val caption: String, val trimStart: Long, val trimEnd: Long,
    val cover: Long?, val watermark: Boolean, val music: Uri?, val musicAsset: String?,
    val musicTitle: String, val musicArtist: String, val musicVolume: Float, val originalVolume: Float,
    val visibility: String = "public", val altText: String = ""
)

/** One explicitly saved draft per account and content type. Private local files, excluded from cloud backup. */
internal class ReelDraftStore(context: Context, uid: String, private val forPost: Boolean = false) {
    private val context = context.applicationContext
    private val root: File
    init {
        require(uid.isNotBlank())
        val account = java.security.MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
        root = File(context.noBackupFilesDir, "${if (forPost) "post_drafts" else "reel_drafts"}/$account")
    }
    private val manifest get() = AtomicFile(File(root, "draft.json"))

    suspend fun load(): ReelDraft? = withContext(Dispatchers.IO) { lock.withLock {
        if (!File(root, "draft.json").exists() && !File(root, "draft.json.bak").exists()) return@withLock null
        val json = JSONObject(manifest.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        fun media(name: String): Uri? {
            val value = json.optString(name)
            if (value.isBlank()) return null
            val file = File(root, value)
            check(file.canonicalFile.parentFile == root.canonicalFile && file.isFile && file.length() > 0)
            return Uri.fromFile(file)
        }
        ReelDraft(checkNotNull(media("video")), json.optString("caption"), json.optLong("trimStart"),
            json.optLong("trimEnd", -1), if (json.has("cover")) json.getLong("cover") else null,
            json.optBoolean("watermark", true), media("music"), json.optString("musicAsset").takeIf { it.isNotBlank() },
            json.optString("musicTitle"), json.optString("musicArtist"),
            json.optDouble("musicVolume", 1.0).toFloat(), json.optDouble("originalVolume", 0.3).toFloat(),
            json.optString("visibility", "public"), json.optString("altText"))
    } }

    suspend fun save(draft: ReelDraft) = withContext(Dispatchers.IO) { lock.withLock {
        check(root.isDirectory || root.mkdirs())
        val created = mutableListOf<File>()
        var committed = false
        suspend fun copy(uri: Uri, suffix: String): String {
            val file = File(root, "${UUID.randomUUID()}.$suffix")
            created += file
            context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Unreadable media" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        check(total <= 512L * 1024 * 1024) { "Draft media exceeds 512 MB" }
                        output.write(buffer, 0, size)
                    }
                    check(total > 0)
                    output.fd.sync()
                }
            }
            return file.name
        }
        try {
            val video = copy(draft.video, if (forPost) "image" else "mp4")
            val music = draft.music?.let { copy(it, "media") }
            val json = JSONObject().put("video", video).put("caption", draft.caption)
                .put("trimStart", draft.trimStart).put("trimEnd", draft.trimEnd)
                .put("visibility", draft.visibility).put("altText", draft.altText)
                .put("watermark", draft.watermark).put("music", music ?: "")
                .put("musicAsset", draft.musicAsset ?: "").put("musicTitle", draft.musicTitle)
                .put("musicArtist", draft.musicArtist).put("musicVolume", draft.musicVolume.toDouble())
                .put("originalVolume", draft.originalVolume.toDouble())
            draft.cover?.let { json.put("cover", it) }
            coroutineContext.ensureActive()
            val output = manifest.startWrite()
            try {
                output.write(json.toString().toByteArray(Charsets.UTF_8))
                manifest.finishWrite(output)
                committed = true
            } catch (error: Throwable) { manifest.failWrite(output); throw error }
            // Only remove old media after the replacement manifest is safely written.
            root.listFiles()?.filter { it.extension in setOf("mp4", "media", "image") && it !in created }?.forEach { it.delete() }
        } finally { if (!committed) created.forEach { it.delete() } }
    } }

    suspend fun delete() = withContext(Dispatchers.IO) { lock.withLock {
        check(!root.exists() || root.deleteRecursively())
    } }

    companion object { private val lock = Mutex() }
}

package com.vivid.app.data.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import android.webkit.MimeTypeMap

class CloudFunctionsStorageProvider(
    private val functionBaseUrl: String,
    private val okHttp: OkHttpClient = defaultClient()
) : StorageProvider {

    private val json = org.json.JSONObject()

    override suspend fun uploadFile(
        localFilePath: String,
        remoteKey: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        require(file.exists()) { "No existe: $localFilePath" }

        onProgress(5)
        val contentType = guessContentType(remoteKey)
        val presign = callFunction("uploadReel", mapOf(
            "key" to remoteKey,
            "contentType" to contentType
        ))

        val uploadUrl = presign.optString("uploadUrl")
        val uploadAuthToken = presign.optString("uploadAuthToken")
        val signedDownloadUrl = presign.optString("signedDownloadUrl")

        if (uploadUrl.isBlank() || uploadAuthToken.isBlank()) {
            error("Respuesta de Cloud Function inválida: $presign")
        }
        onProgress(20)

        val putReq = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadAuthToken)
            .header("Content-Type", contentType)
            .header("X-Bz-File-Name", b2EncodeFileName(remoteKey))
            .header("X-Bz-Content-Sha1", sha1Hex(file))
            .post(file.asRequestBody(contentType.toMediaType()))
            .build()

        okHttp.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) error("POST a B2 falló (${resp.code}): ${resp.body?.string()}")
        }
        onProgress(90)

        if (remoteKey.startsWith("reels/")) {
            val thumbKey = presign.optString("thumbnailKey")
            val thumbUploadUrl = presign.optString("thumbnailUploadUrl")
            val thumbUploadToken = presign.optString("thumbnailUploadAuthToken")

            if (thumbUploadUrl.isNotBlank() && thumbUploadToken.isNotBlank()) {
                val thumbFile = File(localFilePath).parentFile?.let { dir ->
                    File(dir, "thumb_${File(localFilePath).nameWithoutExtension}.jpg")
                }
                if (thumbFile != null && thumbFile.exists()) {
                    val thumbPutReq = Request.Builder()
                        .url(thumbUploadUrl)
                        .header("Authorization", thumbUploadToken)
                        .header("Content-Type", "image/jpeg")
                        .header("X-Bz-File-Name", b2EncodeFileName(thumbKey))
                        .header("X-Bz-Content-Sha1", sha1Hex(thumbFile))
                        .post(thumbFile.asRequestBody(THUMB_MEDIA))
                        .build()
                    okHttp.newCall(thumbPutReq).execute().use { resp ->
                        if (!resp.isSuccessful) Log.w(TAG, "Thumbnail POST falló: ${resp.code}")
                    }
                }
            }
        }

        onProgress(100)
        Log.d(TAG, "Subida OK → $signedDownloadUrl")
        signedDownloadUrl
    }

    override suspend fun deleteFile(remoteKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$functionBaseUrl/deleteFile")
                .delete("""{"key":"$remoteKey"}""".toRequestBody(JSON_MEDIA))
                .build()
            okHttp.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Log.e(TAG, "deleteFile vía CF falló", e)
            false
        }
    }

    override suspend fun signDownloadUrl(remoteKey: String, ttlSec: Int): String =
        renewSignedUrl(remoteKey, ttlSec) ?: ""

    suspend fun renewSignedUrl(remoteKey: String, ttlSec: Int = 3600): String? =
        withContext(Dispatchers.IO) {
            try {
                val resp = callFunction("signDownload", emptyMap(), queryParams = mapOf(
                    "key" to remoteKey,
                    "ttl" to ttlSec.toString()
                ))
                resp.optString("signedUrl").takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.e(TAG, "renewSignedUrl falló", e)
                null
            }
        }

    private fun callFunction(
        name: String,
        body: Map<String, Any>,
        queryParams: Map<String, String> = emptyMap()
    ): JSONObject {
        val urlBuilder = StringBuilder("$functionBaseUrl/$name")
        if (queryParams.isNotEmpty()) {
            urlBuilder.append("?")
            queryParams.forEach { (k, v) -> urlBuilder.append("$k=$v&") }
            urlBuilder.setLength(urlBuilder.length - 1)
        }

        val req = Request.Builder()
            .url(urlBuilder.toString())
            .header("Content-Type", "application/json")
            .post(JSONObject(body).toString().toRequestBody(JSON_MEDIA))
            .build()

        okHttp.newCall(req).execute().use { resp ->
            val respBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("$name → ${resp.code}: $respBody")
            return JSONObject(respBody)
        }
    }

    private fun sha1Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun b2EncodeFileName(fileName: String): String =
        URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

    private fun guessContentType(key: String): String {
        val ext = MimeTypeMap.getFileExtensionFromUrl(key)
        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        if (!fromMap.isNullOrBlank()) return fromMap
        return when {
            key.endsWith(".m4a", true) -> "audio/mp4"
            key.endsWith(".aac", true) -> "audio/aac"
            key.endsWith(".webp", true) -> "image/webp"
            key.endsWith(".jpg", true) || key.endsWith(".jpeg", true) -> "image/jpeg"
            key.endsWith(".png", true) -> "image/png"
            key.endsWith(".mp4", true) -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private const val TAG = "CFStorage"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val THUMB_MEDIA = "image/jpeg".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

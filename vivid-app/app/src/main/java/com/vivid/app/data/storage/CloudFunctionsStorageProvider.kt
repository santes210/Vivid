package com.vivid.app.data.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Implementación SEGURA de [StorageProvider] que usa Firebase Cloud
 * Functions como proxy a Backblaze B2.
 *
 * Por qué esta es la implementación correcta:
 *   - Tu bucket es PRIVADO (no público).
 *   - ExoPlayer necesita URLs firmadas para reproducir desde bucket privado.
 *   - Las claves de B2 viven solo en Cloud Functions, NO en el APK.
 *
 * Flujo:
 *   1. App llama a /uploadReel → recibe uploadUrl + signedDownloadUrl
 *   2. App PUT el archivo directo a B2
 *   3. App guarda el signedDownloadUrl en Firestore
 *   4. Antes de que expire (1h), ReelsScreen llama a /signDownload para renovar
 *
 * El cambio a "signed" es transparente: el StorageProvider devuelve
 * URLs que ya vienen firmadas, así que ExoPlayer las usa sin cambios.
 */
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

        // 1. Pedir uploadUrl + signedDownloadUrl a la Cloud Function
        onProgress(5)
        val presign = callFunction("uploadReel", mapOf(
            "key" to remoteKey,
            "contentType" to "video/mp4"
        ))

        val uploadUrl = presign.optString("uploadUrl")
        val uploadAuthToken = presign.optString("uploadAuthToken")
        val signedDownloadUrl = presign.optString("signedDownloadUrl")

        if (uploadUrl.isBlank() || uploadAuthToken.isBlank()) {
            error("Respuesta de Cloud Function inválida: $presign")
        }
        onProgress(20)

        // 2. PUT directo del archivo a B2
        val bytes = file.readBytes()
        val putReq = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadAuthToken)
            .header("Content-Type", "video/mp4")
            .header("X-Bz-File-Name", remoteKey)
            .put(bytes.toRequestBody(BINARY_MEDIA))
            .build()

        okHttp.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) error("PUT a B2 falló (${resp.code}): ${resp.body?.string()}")
        }
        onProgress(90)

        // 3. Si es un reel, también subir thumbnail (si se generó)
        if (remoteKey.startsWith("reels/")) {
            val thumbKey = presign.optString("thumbnailKey")
            val thumbUploadUrl = presign.optString("thumbnailUploadUrl")
            val thumbUploadToken = presign.optString("thumbnailUploadAuthToken")

            if (thumbUploadUrl.isNotBlank() && thumbUploadToken.isNotBlank()) {
                val thumbFile = File(localFilePath).parentFile?.let { dir ->
                    File(dir, "thumb_${File(localFilePath).nameWithoutExtension}.jpg")
                }
                if (thumbFile != null && thumbFile.exists()) {
                    val thumbBytes = thumbFile.readBytes()
                    val thumbPutReq = Request.Builder()
                        .url(thumbUploadUrl)
                        .header("Authorization", thumbUploadToken)
                        .header("Content-Type", "image/jpeg")
                        .header("X-Bz-File-Name", thumbKey)
                        .put(thumbBytes.toRequestBody(THUMB_MEDIA))
                        .build()
                    okHttp.newCall(thumbPutReq).execute().use { resp ->
                        if (!resp.isSuccessful) Log.w(TAG, "Thumbnail PUT falló: ${resp.code}")
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

    /**
     * Renueva una URL firmada. Útil cuando el signedDownloadUrl está por expirar.
     */
    override suspend fun signDownloadUrl(remoteKey: String, ttlSec: Int): String =
        renewSignedUrl(remoteKey, ttlSec) ?: ""

    /**
     * Renueva una URL firmada. Útil cuando el signedDownloadUrl está por expirar.
     */
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
            urlBuilder.setLength(urlBuilder.length - 1) // quitar último &
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

    companion object {
        private const val TAG = "CFStorage"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val BINARY_MEDIA = "video/mp4".toMediaType()
        private val THUMB_MEDIA = "image/jpeg".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

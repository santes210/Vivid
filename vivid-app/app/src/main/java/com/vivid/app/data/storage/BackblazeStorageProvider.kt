package com.vivid.app.data.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/**
 * Implementación directa de [StorageProvider] usando la API nativa de
 * Backblaze B2 (no la S3-compatible, va más ligera y no requiere AWS SDK).
 *
 * Funciona con bucket PRIVADO usando URLs firmadas
 * (b2_get_download_authorization). NO necesita bucket público ni tarjeta
 * de crédito en Backblaze.
 *
 * ⚠️ MODO DIRECT / INSEGURO
 * -------------------------
 * Esta implementación embebe el `keyId` + `applicationKey` en el APK.
 * Cualquiera puede decompilar el APK (jadx, apktool) y robar las claves.
 * Para producción migra a la Cloud Function incluida en
 * `/cloud-function/index.js` y reemplaza esta clase en [StorageModule].
 *
 * Flujo de B2:
 *   1. b2_authorize_account        → apiUrl + authToken + downloadUrl
 *   2. b2_get_upload_url           → uploadUrl + uploadAuthToken (1 por archivo)
 *   3. b2_upload_file              → POST del binario con SHA1
 *   4. b2_get_download_authorization → token de descarga firmado (TTL máx 7d)
 *      → URL reproducible = downloadUrl + "/file/" + bucketName + "/" + key
 *                            + "?Authorization=" + {token}
 *
 * Documentación: https://www.backblaze.com/docs/cloud-storage-native-api
 */
class BackblazeStorageProvider(
    private val keyId: String,
    private val applicationKey: String,
    private val bucketId: String,
    private val bucketName: String
) : StorageProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES) // videos grandes
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // Cache de la sesión (la auth expira a las 24h)
    private data class Session(
        val apiUrl: String,
        val authToken: String,
        val downloadUrl: String
    )

    private var cachedSession: Session? = null

    override suspend fun uploadFile(
        localFilePath: String,
        remoteKey: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        require(file.exists()) { "No existe el archivo: $localFilePath" }
        val sha1 = sha1Hex(file)

        Log.d(TAG, "Subiendo ${file.name} (${file.length() / 1024} KB) → $remoteKey")

        onProgress(5)

        // 1. Autorizar cuenta
        val session = cachedSession ?: authorize().also { cachedSession = it }
        onProgress(15)

        // 2. Obtener URL de subida (debe ser fresca para CORS)
        val (uploadUrl, uploadAuthToken) = getUploadUrl(session)
        onProgress(25)

        // 3. Subir archivo
        val contentType = guessContentType(remoteKey)
        uploadBinary(uploadUrl, uploadAuthToken, file, sha1, remoteKey, contentType)
        onProgress(95)

        // 4. Generar URL FIRMADA (funciona en bucket privado, TTL 7 días = máx de B2)
        val signedUrl = authorizeDownloadUrl(session, remoteKey, MAX_SIGNED_TTL_SEC)
        onProgress(100)
        Log.d(TAG, "Subida completada: $signedUrl")
        signedUrl
    }

    override suspend fun deleteFile(remoteKey: String): Boolean = withContext(Dispatchers.IO) {
        // b2_delete_file_version requiere fileId (no solo el nombre).
        // Cuando migres a Cloud Function, este método tendrá implementación real.
        Log.w(TAG, "deleteFile no implementado en modo direct (migra a Cloud Function)")
        false
    }

    // ----------------------------------------------------------------------
    // Pasos del protocolo B2
    // ----------------------------------------------------------------------

    private fun authorize(): Session {
        val basic = "Basic " + android.util.Base64.encodeToString(
            "$keyId:$applicationKey".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val req = Request.Builder()
            .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
            .header("Authorization", basic)
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("b2_authorize_account falló (${resp.code}): $body")
            val obj = json.parseToJsonElement(body).jsonObject
            return Session(
                apiUrl = obj["apiUrl"]!!.jsonPrimitive.content,
                authToken = obj["authorizationToken"]!!.jsonPrimitive.content,
                downloadUrl = obj["downloadUrl"]!!.jsonPrimitive.content
            )
        }
    }

    private fun getUploadUrl(session: Session): Pair<String, String> {
        val payload = JSONObject().apply {
            put("bucketId", bucketId)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/v2/b2_get_upload_url")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("b2_get_upload_url falló (${resp.code}): $body")
            val obj = json.parseToJsonElement(body).jsonObject
            return obj["uploadUrl"]!!.jsonPrimitive.content to
                    obj["authorizationToken"]!!.jsonPrimitive.content
        }
    }

    /**
     * Genera (o renueva) una URL firmada para reproducir un archivo ya subido.
     * Útil cuando la URL original de un reel expira (TTL 7 días).
     *
     * Funciona en buckets PRIVADOS.
     */
    override suspend fun signDownloadUrl(remoteKey: String, ttlSec: Int): String =
        withContext(Dispatchers.IO) {
            val session = cachedSession ?: authorize().also { cachedSession = it }
            authorizeDownloadUrl(session, remoteKey, ttlSec)
        }

    /**
     * Pide a B2 un token de descarga firmado (b2_get_download_authorization).
     * TTL válido entre 1 segundo y 7 días (604800s).
     */
    private fun authorizeDownloadUrl(session: Session, fileName: String, ttlSec: Int): String {
        val validTtl = ttlSec.coerceIn(1, MAX_SIGNED_TTL_SEC)
        val payload = JSONObject().apply {
            put("bucketId", bucketId)
            put("fileNamePrefix", fileName)
            put("validDurationInSeconds", validTtl)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/v2/b2_get_download_authorization")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("b2_get_download_authorization falló (${resp.code}): $body")
            val obj = json.parseToJsonElement(body).jsonObject
            val token = obj["authorizationToken"]!!.jsonPrimitive.content
            return "${session.downloadUrl}/file/$bucketName/$fileName?Authorization=$token"
        }
    }

    private fun uploadBinary(
        uploadUrl: String,
        uploadAuthToken: String,
        file: File,
        sha1: String,
        remoteKey: String,
        contentType: String
    ) {
        val media = contentType.toMediaType()
        val req = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadAuthToken)
            .header("X-Bz-File-Name", b2EncodeFileName(remoteKey))
            .header("Content-Type", contentType)
            .header("X-Bz-Content-Sha1", sha1)
            // La API nativa de Backblaze B2 usa POST para b2_upload_file.
            // PUT provoca 405 Method Not Allowed.
            .post(file.asRequestBody(media))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("b2_upload_file falló (${resp.code}): $body")
            Log.d(TAG, "b2_upload_file OK (${file.length()} bytes)")
        }
    }

    private fun guessContentType(key: String): String = when {
        key.endsWith(".jpg", true) || key.endsWith(".jpeg", true) -> "image/jpeg"
        key.endsWith(".png", true) -> "image/png"
        key.endsWith(".gif", true) -> "image/gif"
        key.endsWith(".webp", true) -> "image/webp"
        else -> "video/mp4"
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

    companion object {
        private const val TAG = "BackblazeStorage"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // TTL máximo de B2 para URLs firmadas = 7 días (604800s)
        const val MAX_SIGNED_TTL_SEC = 604_800
    }
}

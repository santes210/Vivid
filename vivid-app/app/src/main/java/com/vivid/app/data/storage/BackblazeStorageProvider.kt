package com.vivid.app.data.storage

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
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

    @Volatile
    private var cachedSession: Session? = null

    private class B2ApiException(
        val statusCode: Int,
        val apiCode: String,
        operation: String,
        detail: String
    ) : IOException("$operation falló ($statusCode/$apiCode): $detail")

    override suspend fun uploadFile(
        localFilePath: String,
        remoteKey: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val file = File(localFilePath)
        require(file.exists() && file.isFile && file.length() > 0L) {
            "No existe el archivo o está vacío: $localFilePath"
        }
        require(remoteKey.isNotBlank()) { "La ruta remota está vacía" }
        val sha1 = sha1Hex(file)

        Log.d(TAG, "Subiendo ${file.name} (${file.length() / 1024} KB) → $remoteKey")
        onProgress(5)

        // Los tokens de cuenta B2 caducan como máximo a las 24 horas. Antes se
        // conservaba el token vencido para siempre y todas las imágenes/audios
        // fallaban hasta matar la app. Reautorizamos y repetimos una sola vez
        // cuando cualquier paso devuelve un error de autenticación.
        var session = getSession()
        onProgress(15)

        var uploadCredentials = try {
            getUploadUrl(session)
        } catch (error: B2ApiException) {
            if (!error.isAuthenticationFailure()) throw error
            session = getSession(forceRefresh = true)
            getUploadUrl(session)
        }
        onProgress(25)

        val contentType = guessContentType(remoteKey)
        try {
            uploadBinary(
                uploadCredentials.first,
                uploadCredentials.second,
                file,
                sha1,
                remoteKey,
                contentType
            )
        } catch (error: B2ApiException) {
            if (!error.isAuthenticationFailure()) throw error
            session = getSession(forceRefresh = true)
            uploadCredentials = getUploadUrl(session)
            uploadBinary(
                uploadCredentials.first,
                uploadCredentials.second,
                file,
                sha1,
                remoteKey,
                contentType
            )
        }
        onProgress(95)

        val signedUrl = try {
            authorizeDownloadUrl(session, remoteKey, MAX_SIGNED_TTL_SEC)
        } catch (error: B2ApiException) {
            if (!error.isAuthenticationFailure()) throw error
            session = getSession(forceRefresh = true)
            authorizeDownloadUrl(session, remoteKey, MAX_SIGNED_TTL_SEC)
        }
        check(signedUrl.isNotBlank()) { "B2 no devolvió una URL de descarga" }

        onProgress(100)
        Log.d(TAG, "Subida completada: $remoteKey")
        signedUrl
    }

    override suspend fun deleteFile(remoteKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var session = getSession()
            val file = try {
                findFile(session, remoteKey)
            } catch (error: B2ApiException) {
                if (!error.isAuthenticationFailure()) throw error
                session = getSession(forceRefresh = true)
                findFile(session, remoteKey)
            }
            if (file == null) {
                Log.w(TAG, "deleteFile: no existe $remoteKey en el bucket")
                return@withContext false
            }
            try {
                deleteVersion(session, file.first, file.second)
            } catch (error: B2ApiException) {
                if (!error.isAuthenticationFailure()) throw error
                session = getSession(forceRefresh = true)
                deleteVersion(session, file.first, file.second)
            }
            Log.d(TAG, "deleteFile OK: $remoteKey")
            true
        } catch (e: Exception) {
            Log.w(TAG, "deleteFile falló para $remoteKey: ${e.message}")
            false
        }
    }

    /**
     * Localiza el nombre real y el fileId. En el JSON de B2 el prefijo NO va
     * URL-encoded; la codificación solo corresponde al header X-Bz-File-Name.
     */
    private fun findFile(session: Session, remoteKey: String): Pair<String, String>? {
        val payload = JSONObject().apply {
            put("bucketId", bucketId)
            put("fileNamePrefix", remoteKey)
            put("maxFileCount", 1)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/$API_VERSION/b2_list_file_names")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiException("b2_list_file_names", resp.code, body)
            val obj = json.parseToJsonElement(body).jsonObject
            val files = obj["files"]!!.jsonArray
            if (files.isEmpty()) return null
            val first = files.first().jsonObject
            val fileName = first["fileName"]?.jsonPrimitive?.content ?: return null
            val fileId = first["fileId"]?.jsonPrimitive?.content ?: return null
            if (fileName != remoteKey) return null
            return fileName to fileId
        }
    }

    /** Borra la versión de un archivo (b2_delete_file_version). */
    private fun deleteVersion(session: Session, fileName: String, fileId: String) {
        val payload = JSONObject().apply {
            put("fileName", fileName)
            put("fileId", fileId)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/$API_VERSION/b2_delete_file_version")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiException("b2_delete_file_version", resp.code, body)
        }
    }

    // ----------------------------------------------------------------------
    // Pasos del protocolo B2
    // ----------------------------------------------------------------------

    private fun getSession(forceRefresh: Boolean = false): Session {
        if (!forceRefresh) cachedSession?.let { return it }
        return synchronized(this) {
            if (!forceRefresh) cachedSession?.let { return@synchronized it }
            authorize().also { cachedSession = it }
        }
    }

    private fun authorize(): Session {
        val basic = "Basic " + android.util.Base64.encodeToString(
            "$keyId:$applicationKey".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val req = Request.Builder()
            // v4 soporta las claves actuales multi-bucket y también las claves
            // clásicas. v2 rechaza claves creadas por la consola moderna de B2.
            .url("https://api.backblazeb2.com/b2api/$API_VERSION/b2_authorize_account")
            .header("Authorization", basic)
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiException("b2_authorize_account", resp.code, body)
            val obj = json.parseToJsonElement(body).jsonObject
            val storageApi = obj["apiInfo"]
                ?.jsonObject
                ?.get("storageApi")
                ?.jsonObject

            // v3/v4 agrupan apiUrl y downloadUrl dentro de apiInfo.storageApi.
            // Los fallbacks conservan compatibilidad con respuestas v2.
            val apiUrl = storageApi?.get("apiUrl")?.jsonPrimitive?.content
                ?: obj["apiUrl"]?.jsonPrimitive?.content
            val downloadUrl = storageApi?.get("downloadUrl")?.jsonPrimitive?.content
                ?: obj["downloadUrl"]?.jsonPrimitive?.content
            val authToken = obj["authorizationToken"]?.jsonPrimitive?.content

            return Session(
                apiUrl = requireNotNull(apiUrl?.takeIf { it.isNotBlank() }) {
                    "Respuesta B2 sin apiUrl"
                },
                authToken = requireNotNull(authToken?.takeIf { it.isNotBlank() }) {
                    "Respuesta B2 sin authorizationToken"
                },
                downloadUrl = requireNotNull(downloadUrl?.takeIf { it.isNotBlank() }) {
                    "Respuesta B2 sin downloadUrl"
                }
            )
        }
    }

    private fun getUploadUrl(session: Session): Pair<String, String> {
        val payload = JSONObject().apply {
            put("bucketId", bucketId)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/$API_VERSION/b2_get_upload_url")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiException("b2_get_upload_url", resp.code, body)
            val obj = json.parseToJsonElement(body).jsonObject
            return obj["uploadUrl"]!!.jsonPrimitive.content to
                obj["authorizationToken"]!!.jsonPrimitive.content
        }
    }

    /**
     * Genera (o renueva) una URL firmada para reproducir un archivo ya subido.
     * Útil cuando la URL original de un post/reel expira (TTL 7 días).
     * Funciona en buckets PRIVADOS.
     *
     * FIX 2026-08-09: si el authToken expiró (24h), re-autoriza una vez y reintenta.
     */
    override suspend fun signDownloadUrl(remoteKey: String, ttlSec: Int): String =
        withContext(Dispatchers.IO) {
            val session = getSession()
            try {
                authorizeDownloadUrl(session, remoteKey, ttlSec)
            } catch (error: B2ApiException) {
                if (!error.isAuthenticationFailure()) throw error
                Log.w(TAG, "Token B2 vencido al firmar; reautorizando")
                authorizeDownloadUrl(getSession(forceRefresh = true), remoteKey, ttlSec)
            }
        }

    /**
     * Pide a B2 un token de descarga firmado (b2_get_download_authorization).
     * TTL válido entre 1 segundo y 7 días (604800s).
     *
     * FIX encoding: para la URL de descarga, B2 espera que el fileName en el path
     * conserve los '/' pero codifique espacios y caracteres especiales.
     * Usamos encodeFileNameForUrl que preserva '/' y codifica el resto.
     */
    private fun authorizeDownloadUrl(session: Session, fileName: String, ttlSec: Int): String {
        val validTtl = ttlSec.coerceIn(1, MAX_SIGNED_TTL_SEC)
        val payload = JSONObject().apply {
            put("bucketId", bucketId)
            put("fileNamePrefix", fileName)
            put("validDurationInSeconds", validTtl)
        }.toString().toRequestBody(JSON_MEDIA)

        val req = Request.Builder()
            .url("${session.apiUrl}/b2api/$API_VERSION/b2_get_download_authorization")
            .header("Authorization", session.authToken)
            .post(payload)
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw apiException("b2_get_download_authorization", resp.code, body)
            }
            val obj = json.parseToJsonElement(body).jsonObject
            val token = obj["authorizationToken"]!!.jsonPrimitive.content
            val encodedFileName = encodeFileNameForUrl(fileName)
            return "${session.downloadUrl}/file/$bucketName/$encodedFileName?Authorization=$token"
        }
    }

    /**
     * Codifica fileName para URL de descarga preservando '/'.
     * Ej: posts/uid/123.jpg -> posts/uid/123.jpg (sin cambio)
     * Si hay espacios: "my file.jpg" -> "my%20file.jpg"
     */
    private fun encodeFileNameForUrl(fileName: String): String {
        return fileName.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
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
            if (!resp.isSuccessful) throw apiException("b2_upload_file", resp.code, body)
            Log.d(TAG, "b2_upload_file OK (${file.length()} bytes)")
        }
    }

    private fun apiException(operation: String, statusCode: Int, body: String): B2ApiException {
        val parsed = runCatching { JSONObject(body) }.getOrNull()
        val code = parsed?.optString("code")?.takeIf { it.isNotBlank() } ?: "unknown"
        val detail = parsed?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "Respuesta no disponible"
        return B2ApiException(statusCode, code, operation, detail)
    }

    private fun B2ApiException.isAuthenticationFailure(): Boolean {
        return statusCode == 401 || apiCode in setOf(
            "bad_auth_token",
            "expired_auth_token",
            "unauthorized"
        )
    }

    private fun guessContentType(key: String): String = when {
        key.endsWith(".jpg", true) || key.endsWith(".jpeg", true) -> "image/jpeg"
        key.endsWith(".png", true) -> "image/png"
        key.endsWith(".gif", true) -> "image/gif"
        key.endsWith(".webp", true) -> "image/webp"
        key.endsWith(".mp3", true) -> "audio/mpeg"
        key.endsWith(".m4a", true) -> "audio/mp4"
        key.endsWith(".aac", true) -> "audio/aac"
        key.endsWith(".wav", true) -> "audio/wav"
        key.endsWith(".ogg", true) -> "audio/ogg"
        key.endsWith(".mp4", true) -> "video/mp4"
        else -> "application/octet-stream"
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
        private const val API_VERSION = "v4"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // TTL máximo de B2 para URLs firmadas = 7 días (604800s)
        const val MAX_SIGNED_TTL_SEC = 604_800
    }
}

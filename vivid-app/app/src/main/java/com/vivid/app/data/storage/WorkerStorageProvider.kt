package com.vivid.app.data.storage

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
 * Implementación de [StorageProvider] que NO contiene credenciales.
 *
 * MODO SEGURO
 * -----------
 * Las claves de Backblaze B2 viven como secretos cifrados dentro del
 * Cloudflare Worker. Esta clase solo sabe hablar con el Worker, autenticándose
 * con el ID token de Firebase del usuario que ha iniciado sesión.
 *
 * Flujo de subida:
 *   1. POST {worker}/storage/upload-url  → uploadUrl + token temporal de B2
 *   2. POST del binario DIRECTO a B2     → los bytes no pasan por Cloudflare
 *   3. POST {worker}/storage/sign        → URL firmada para reproducir
 *
 * El paso 2 es lo que permite subir vídeos de cualquier tamaño: el límite de
 * 100 MB de cuerpo de petición de Cloudflare no aplica porque el archivo no
 * atraviesa el Worker.
 *
 * Sustituye a BackblazeStorageProvider, que embebía keyId/applicationKey en
 * el APK y por tanto quedaban expuestas a cualquiera que lo decompilara.
 */
class WorkerStorageProvider(
    private val workerBaseUrl: String,
    private val auth: FirebaseAuth,
    private val client: OkHttpClient = defaultClient()
) : StorageProvider {

    init {
        require(workerBaseUrl.isNotBlank()) {
            "La URL del Worker está vacía. Configura VIVID_WORKER_URL en el build."
        }
    }

    private val baseUrl = workerBaseUrl.trimEnd('/')

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

        val contentType = guessContentType(remoteKey)
        Log.d(TAG, "Subiendo ${file.name} (${file.length() / 1024} KB) → $remoteKey")
        onProgress(5)

        // 1. Pedir permiso de subida al Worker.
        val ticket = callWorker(
            "storage/upload-url",
            JSONObject().apply {
                put("key", remoteKey)
                put("contentType", contentType)
            }
        )
        val uploadUrl = ticket.optString("uploadUrl")
        val uploadToken = ticket.optString("uploadAuthToken")
        // El Worker puede normalizar la clave; manda la suya.
        val effectiveKey = ticket.optString("remoteKey").takeIf { it.isNotBlank() } ?: remoteKey

        if (uploadUrl.isBlank() || uploadToken.isBlank()) {
            throw IOException("El Worker no devolvió credenciales de subida")
        }
        onProgress(20)

        // 2. Subir el binario directamente a Backblaze B2.
        val sha1 = sha1Hex(file)
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadToken)
            .header("X-Bz-File-Name", b2EncodeFileName(effectiveKey))
            .header("Content-Type", contentType)
            .header("X-Bz-Content-Sha1", sha1)
            // La API nativa de B2 usa POST. PUT devuelve 405.
            .post(file.asRequestBody(contentType.toMediaType()))
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(300)
                throw IOException("b2_upload_file falló (${response.code}): $detail")
            }
        }
        onProgress(90)

        // 3. Obtener la URL firmada para reproducir el archivo.
        val signedUrl = signDownloadUrl(effectiveKey)
        check(signedUrl.isNotBlank()) { "No se pudo firmar la URL de descarga" }

        onProgress(100)
        Log.d(TAG, "Subida completada: $effectiveKey")
        signedUrl
    }

    override suspend fun signDownloadUrl(remoteKey: String, ttlSec: Int): String =
        withContext(Dispatchers.IO) {
            val response = callWorker(
                "storage/sign",
                JSONObject().apply {
                    put("key", remoteKey)
                    put("ttlSec", ttlSec)
                }
            )
            response.optString("signedUrl")
        }

    override suspend fun deleteFile(remoteKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = callWorker(
                "storage/delete",
                JSONObject().apply { put("key", remoteKey) }
            )
            response.optBoolean("deleted", false)
        } catch (e: Exception) {
            Log.w(TAG, "deleteFile falló para $remoteKey: ${e.message}")
            false
        }
    }

    // ----------------------------------------------------------------------
    // Comunicación con el Worker
    // ----------------------------------------------------------------------

    private suspend fun callWorker(path: String, body: JSONObject): JSONObject {
        val idToken = currentIdToken()
        val request = Request.Builder()
            .url("$baseUrl/$path")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(raw).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: raw.take(200)
                throw IOException("$path → ${response.code}: $message")
            }
            return runCatching { JSONObject(raw) }
                .getOrElse { throw IOException("Respuesta no válida de $path") }
        }
    }

    /**
     * ID token de Firebase del usuario actual. `forceRefresh = false` deja que
     * el SDK reutilice el token en caché y lo renueve solo si caducó (duran 1 h).
     */
    private suspend fun currentIdToken(): String {
        val user = auth.currentUser
            ?: throw IOException("Necesitas iniciar sesión para subir o ver archivos")
        val result = user.getIdToken(false).await()
        return result.token ?: throw IOException("No se pudo obtener el token de sesión")
    }

    // ----------------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------------

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
        // El Worker rechaza los tipos que no estén en su lista blanca.
        else -> "application/octet-stream"
    }

    private fun sha1Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun b2EncodeFileName(fileName: String): String =
        URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

    companion object {
        private const val TAG = "WorkerStorage"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.MINUTES) // vídeos grandes
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

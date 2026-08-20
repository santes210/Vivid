package com.vivid.app.data.storage

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WorkerStorageProvider(
    private val workerBaseUrl: String,
    private val auth: FirebaseAuth
) : StorageProvider {

    init {
        require(workerBaseUrl.isNotBlank()) {
            "La URL del Worker está vacía. Configura VIVID_WORKER_URL en el build."
        }
    }

    private val baseUrl = workerBaseUrl.trimEnd('/')

    private val client: OkHttpClient = buildHttpClient()

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.MINUTES)
            .readTimeout(60, TimeUnit.SECONDS)

        applyWorkerCertPinning(builder)
        return builder.build()
    }

    private fun applyWorkerCertPinning(builder: OkHttpClient.Builder) {
        val rawPins = BuildConfig.WORKER_PIN
        if (rawPins.isBlank()) return

        val host = runCatching { URI(baseUrl).host }.getOrNull()
        if (host.isNullOrBlank()) {
            Log.w(TAG, "No se pudo extraer el host de $baseUrl para el cert pinning")
            return
        }

        var added = false

        val pinner = CertificatePinner.Builder().apply {
            for (candidate in rawPins.split(';')) {
                val pin = candidate.trim()

                if (PIN_PATTERN.matches(pin)) {
                    add(host, pin)
                    added = true
                } else {
                    Log.w(TAG, "Pin ignorado (formato inválido): $pin")
                }
            }
        }.build()

        if (added) {
            builder.certificatePinner(pinner)
            Log.i(TAG, "Cert pinning activo para $host")
        }
    }

    override suspend fun uploadFile(
        localFilePath: String,
        remoteKey: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val file = File(localFilePath)

        require(file.exists() && file.isFile && file.length() > 0L) {
            "No existe el archivo o está vacío: $localFilePath"
        }

        require(remoteKey.isNotBlank()) {
            "La ruta remota está vacía"
        }

        val contentType = guessContentType(remoteKey)
        val sizeBytes = file.length()

        onProgress(5)

        val ticket = callWorker(
            "storage/upload-url",
            JSONObject().apply {
                put("key", remoteKey)
                put("contentType", contentType)
                put("sizeBytes", sizeBytes)
            }
        )

        val uploadUrl = ticket.optString("uploadUrl")
        val uploadToken = ticket.optString("uploadAuthToken")
        val effectiveKey = ticket.optString("remoteKey")
            .takeIf { it.isNotBlank() }
            ?: remoteKey

        if (uploadUrl.isBlank() || uploadToken.isBlank()) {
            throw IOException("El Worker no devolvió credenciales de subida")
        }

        onProgress(20)

        val sha1 = sha1Hex(file)

        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadToken)
            .header("X-Bz-File-Name", b2EncodeFileName(effectiveKey))
            .header("Content-Type", contentType)
            .header("X-Bz-Content-Sha1", sha1)
            .post(file.asRequestBody(contentType.toMediaType()))
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.string().orEmpty().take(300)
                throw IOException("b2_upload_file falló (${response.code}): $detail")
            }
        }

        onProgress(90)

        val uploadId = "$sha1:$sizeBytes"

        try {
            callWorker(
                "storage/complete",
                JSONObject().apply {
                    put("key", effectiveKey)
                    put("uploadId", uploadId)
                    put("sizeBytes", sizeBytes)
                    put("contentType", contentType)
                }
            )
        } catch (e: IOException) {
            val message = e.message.orEmpty().lowercase()

            if (message.contains("quota") || message.contains("too large")) {
                runCatching { deleteFile(effectiveKey) }
                throw e
            }

            Log.w(TAG, "storage/complete falló (best-effort): ${e.message}")
        }

        onProgress(95)

        val signedUrl = signDownloadUrl(effectiveKey)

        check(signedUrl.isNotBlank()) {
            "No se pudo firmar la URL de descarga"
        }

        onProgress(100)
        signedUrl
    }

    override suspend fun signDownloadUrl(
        remoteKey: String,
        ttlSec: Int
    ): String = withContext(Dispatchers.IO) {
        val response = callWorker(
            "storage/sign",
            JSONObject().apply {
                put("key", remoteKey)
                put("ttlSec", ttlSec)
            }
        )

        response.optString("signedUrl")
    }

    override suspend fun deleteFile(remoteKey: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val response = callWorker(
                    "storage/delete",
                    JSONObject().apply {
                        put("key", remoteKey)
                    }
                )

                // deleted=false también es éxito si B2 ya no tenía el archivo.
                response.optBoolean("ok", false)
            } catch (e: Exception) {
                Log.w(TAG, "deleteFile falló para $remoteKey: ${e.message}")
                false
            }
        }

    private suspend fun callWorker(
        path: String,
        body: JSONObject
    ): JSONObject {
        val idToken = currentIdToken()

        val request = Request.Builder()
            .url("$baseUrl/$path")
            .header("Authorization", "Bearer $idToken")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(raw).optString("error")
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: raw.take(200)

                throw IOException("$path → ${response.code}: $message")
            }

            return runCatching {
                JSONObject(raw)
            }.getOrElse {
                throw IOException("Respuesta no válida de $path")
            }
        }
    }

    private suspend fun currentIdToken(): String {
        val user = auth.currentUser
            ?: throw IOException("Necesitas iniciar sesión para subir o ver archivos")

        val result = user.getIdToken(false).await()

        return result.token
            ?: throw IOException("No se pudo obtener el token de sesión")
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
        val digest = MessageDigest.getInstance("SHA-1")

        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    private fun b2EncodeFileName(fileName: String): String =
        URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")

    companion object {
        private const val TAG = "WorkerStorage"
        private val JSON_MEDIA =
            "application/json; charset=utf-8".toMediaType()

        private val PIN_PATTERN =
            Regex("^sha256/[A-Za-z0-9+/=_-]+$")
    }
}

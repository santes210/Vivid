package com.vivid.app.util

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.vivid.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Encola avisos al Worker después de que Firestore confirme la acción.
 * WorkManager conserva y reintenta el envío aunque Android cierre el proceso.
 * El Worker vuelve a validar la acción: ningún dato de aquí se considera confiable.
 */
object PushSender {
    private lateinit var applicationContext: Context

    fun initialize(context: Context) { applicationContext = context.applicationContext }

    private const val TAG = "PushSender"
    private const val KEY_ACTOR_UID = "actorUid"
    private const val KEY_PAYLOAD = "payload"

    fun reelLike(reelId: String) = enqueue(
        "reel_like:$reelId", mapOf("type" to "reel_like", "reelId" to reelId)
    )

    fun reelComment(reelId: String, commentId: String) = enqueue(
        "reel_comment:$reelId:$commentId",
        mapOf("type" to "reel_comment", "reelId" to reelId, "commentId" to commentId)
    )

    fun postLike(postId: String) = enqueue(
        "post_like:$postId", mapOf("type" to "post_like", "postId" to postId)
    )

    fun postComment(postId: String, commentId: String) = enqueue(
        "post_comment:$postId:$commentId",
        mapOf("type" to "post_comment", "postId" to postId, "commentId" to commentId)
    )

    fun newFollower(targetUid: String) = enqueue(
        "new_follower:$targetUid", mapOf("type" to "new_follower", "targetUid" to targetUid)
    )

    fun followRequest(targetUid: String) = enqueue(
        "follow_request:$targetUid", mapOf("type" to "follow_request", "targetUid" to targetUid)
    )

    fun message(chatId: String, messageId: String) = enqueue(
        "message:$chatId:$messageId",
        mapOf("type" to "message", "chatId" to chatId, "messageId" to messageId)
    )

    private fun enqueue(eventKey: String, payload: Map<String, String>) {
        val actorUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (BuildConfig.PUSH_WORKER_URL.isBlank()) {
            Log.w(TAG, "VIVID_PUSH_WORKER_URL no está configurada; no se encoló $eventKey")
            return
        }
        val input = Data.Builder()
            .putString(KEY_ACTOR_UID, actorUid)
            .putString(KEY_PAYLOAD, JSONObject(payload).toString())
            .build()
        val request = OneTimeWorkRequestBuilder<PushNotificationWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "vivid-push:$actorUid:$eventKey",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    class PushNotificationWorker(
        context: Context,
        params: WorkerParameters
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val expectedActor = inputData.getString(KEY_ACTOR_UID) ?: return Result.failure()
            val payload = inputData.getString(KEY_PAYLOAD) ?: return Result.failure()
            val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
            // Evita enviar una acción pendiente bajo la sesión de otra persona.
            if (currentUser.uid != expectedActor) return Result.success()

            return try {
                val idToken = currentUser.getIdToken(false).await().token
                    ?: return Result.retry()
                val endpoint = BuildConfig.PUSH_WORKER_URL.trimEnd('/') + "/notify"
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $idToken")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val code = withContext(Dispatchers.IO) {
                    HTTP.newCall(request).execute().use { response -> response.code }
                }
                when {
                    code in 200..299 -> Result.success()
                    code == 401 && runAttemptCount == 0 -> {
                        // Fuerza una renovación del ID token en el siguiente intento.
                        runCatching { currentUser.getIdToken(true).await() }
                        Result.retry()
                    }
                    code == 408 || code == 429 || code >= 500 -> Result.retry()
                    else -> Result.failure()
                }
            } catch (error: Exception) {
                Log.w(TAG, "Error enviando push (intento $runAttemptCount)", error)
                if (runAttemptCount < 8) Result.retry() else Result.failure()
            }
        }

        companion object {
            private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
            private val HTTP = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}

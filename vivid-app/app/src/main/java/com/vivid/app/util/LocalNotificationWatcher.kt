package com.vivid.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.MainActivity
import com.vivid.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

/**
 * Sistema de notificaciones locales sin Cloud Functions.
 *
 * Usa Firestore snapshot listeners para detectar nueva actividad
 * (mensajes, likes, comentarios, seguidores) y muestra notificaciones
 * locales desde el propio APK.
 *
 * Funciona mientras la app está en primer o segundo plano.
 */
object LocalNotificationWatcher {
    private const val TAG = "LocalNotifWatcher"
    private const val PREFS_NAME = "vivid_notif_tracker"

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listeners = mutableListOf<ListenerRegistration>()
    private val notifiedMessageIds = mutableSetOf<String>()
    private val notifiedLikeIds = mutableSetOf<String>()
    private val notifiedCommentIds = mutableSetOf<String>()
    private val notifiedFollowerIds = mutableSetOf<String>()

    fun start(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        Log.i(TAG, "Iniciando watcher para uid=$uid")
        loadNotifiedIds(context)
        watchMessages(context, uid)
        watchFollowers(context, uid)
        watchReelActivity(context, uid)
    }

    fun stop() {
        Log.i(TAG, "Deteniendo watcher (${listeners.size} listeners)")
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    // ── MENSAJES ──────────────────────────────────

    private fun watchMessages(context: Context, uid: String) {
        val reg = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (snapshot.documents.isEmpty()) return@addSnapshotListener

                // Para cada chat, escuchar sus mensajes
                snapshot.documents.forEach { chatDoc ->
                    watchSingleChatMessages(context, chatDoc.id, uid)
                }
            }
        listeners.add(reg)
    }

    private val watchedChats = mutableSetOf<String>()

    private fun watchSingleChatMessages(context: Context, chatId: String, uid: String) {
        if (watchedChats.contains(chatId)) return
        watchedChats.add(chatId)

        val reg = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(3)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val msg = change.document.data
                        val msgId = change.document.id
                        val senderId = msg["senderId"] as? String ?: ""
                        val type = msg["type"] as? String ?: "text"
                        val text = msg["text"] as? String ?: ""
                        // Los mensajes de imagen no traen texto: se notifica con un placeholder
                        val displayText = if (type == "image" && text.isBlank()) "Imagen" else text

                        if (senderId != uid && !notifiedMessageIds.contains(msgId) && displayText.isNotBlank()) {
                            notifiedMessageIds.add(msgId)
                            saveNotifiedId(context, "msg", msgId)

                            scope.launch {
                                try {
                                    val senderDoc = db.collection("users").document(senderId).get().await()
                                    val senderName = senderDoc.data?.get("username") as? String ?: "Alguien"

                                    showLocalNotification(
                                        context = context,
                                        channelId = "messages_channel",
                                        title = senderName,
                                        body = displayText.take(150),
                                        intent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra("openChat", true)
                                            putExtra("chatId", chatId)
                                        },
                                        requestCode = msgId.hashCode()
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching sender name", e)
                                }
                            }
                        }
                    }
                }
            }
        listeners.add(reg)
    }

    // ── SEGUIDORES ────────────────────────────────

    private fun watchFollowers(context: Context, uid: String) {
        val reg = db.collection("users").document(uid)
            .collection("followers")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val followerUid = change.document.id
                        if (!notifiedFollowerIds.contains(followerUid)) {
                            notifiedFollowerIds.add(followerUid)
                            saveNotifiedId(context, "follower", followerUid)

                            scope.launch {
                                try {
                                    val followerDoc = db.collection("users").document(followerUid).get().await()
                                    val name = followerDoc.data?.get("username") as? String ?: "alguien"

                                    showLocalNotification(
                                        context = context,
                                        channelId = "general_channel",
                                        title = "Nuevo seguidor",
                                        body = "$name empezó a seguirte",
                                        intent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra("openProfile", true)
                                            putExtra("profileUserId", followerUid)
                                        },
                                        requestCode = followerUid.hashCode()
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching follower name", e)
                                }
                            }
                        }
                    }
                }
            }
        listeners.add(reg)
    }

    // ── LIKES Y COMENTARIOS EN REELS ──────────────

    private fun watchReelActivity(context: Context, uid: String) {
        val reg = db.collection("reels")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documents.forEach { reelDoc ->
                    val reelId = reelDoc.id
                    watchReelLikes(context, reelId, uid)
                    watchReelComments(context, reelId, uid)
                }
            }
        listeners.add(reg)
    }

    private val watchedReelLikes = mutableSetOf<String>()

    private fun watchReelLikes(context: Context, reelId: String, ownerUid: String) {
        val key = "likes_$reelId"
        if (watchedReelLikes.contains(key)) return
        watchedReelLikes.add(key)

        val reg = db.collection("reels").document(reelId)
            .collection("likes")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val likerUid = change.document.id
                        val likeId = "${reelId}_${likerUid}"

                        if (likerUid != ownerUid && !notifiedLikeIds.contains(likeId)) {
                            notifiedLikeIds.add(likeId)
                            saveNotifiedId(context, "like", likeId)

                            scope.launch {
                                try {
                                    val likerDoc = db.collection("users").document(likerUid).get().await()
                                    val name = likerDoc.data?.get("username") as? String ?: "alguien"

                                    showLocalNotification(
                                        context = context,
                                        channelId = "general_channel",
                                        title = "Nuevo me gusta",
                                        body = "A $name le gustó tu reel",
                                        intent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra("openReel", true)
                                            putExtra("reelId", reelId)
                                        },
                                        requestCode = likeId.hashCode()
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching liker name", e)
                                }
                            }
                        }
                    }
                }
            }
        listeners.add(reg)
    }

    private val watchedReelComments = mutableSetOf<String>()

    private fun watchReelComments(context: Context, reelId: String, ownerUid: String) {
        val key = "comments_$reelId"
        if (watchedReelComments.contains(key)) return
        watchedReelComments.add(key)

        val reg = db.collection("reels").document(reelId)
            .collection("comments")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val commentId = change.document.id
                        val authorUid = data["userId"] as? String ?: ""
                        val text = data["text"] as? String ?: ""

                        if (authorUid != ownerUid && !notifiedCommentIds.contains(commentId)) {
                            notifiedCommentIds.add(commentId)
                            saveNotifiedId(context, "comment", commentId)

                            scope.launch {
                                try {
                                    val authorDoc = db.collection("users").document(authorUid).get().await()
                                    val name = authorDoc.data?.get("username") as? String ?: "alguien"

                                    showLocalNotification(
                                        context = context,
                                        channelId = "general_channel",
                                        title = "$name comentó",
                                        body = text.take(100),
                                        intent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            putExtra("openReel", true)
                                            putExtra("reelId", reelId)
                                        },
                                        requestCode = commentId.hashCode()
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching comment author name", e)
                                }
                            }
                        }
                    }
                }
            }
        listeners.add(reg)
    }

    // ── HELPERS ────────────────────────────────────

    private fun showLocalNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        intent: Intent,
        requestCode: Int
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(
                if (channelId == "messages_channel")
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(requestCode, notification)
        Log.d(TAG, "Notificación local mostrada: [$title] $body")
    }

    // ── PERSISTENCIA DE IDs NOTIFICADOS ────────────

    private fun loadNotifiedIds(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val msgIds = prefs.getStringSet("notified_msg_ids", emptySet()) ?: emptySet()
        val likeIds = prefs.getStringSet("notified_like_ids", emptySet()) ?: emptySet()
        val commentIds = prefs.getStringSet("notified_comment_ids", emptySet()) ?: emptySet()
        val followerIds = prefs.getStringSet("notified_follower_ids", emptySet()) ?: emptySet()

        // Limitar a 500 cada uno para que no crezca infinitamente
        notifiedMessageIds.addAll(msgIds.take(500))
        notifiedLikeIds.addAll(likeIds.take(500))
        notifiedCommentIds.addAll(commentIds.take(500))
        notifiedFollowerIds.addAll(followerIds.take(500))

        Log.d(TAG, "Cargados IDs notificados: msg=${notifiedMessageIds.size}, like=${notifiedLikeIds.size}, comment=${notifiedCommentIds.size}, follower=${notifiedFollowerIds.size}")
    }

    private fun saveNotifiedId(context: Context, type: String, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (type) {
            "msg" -> "notified_msg_ids"
            "like" -> "notified_like_ids"
            "comment" -> "notified_comment_ids"
            "follower" -> "notified_follower_ids"
            else -> return
        }
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (current.size > 500) return // No guardar más para no saturar
        current.add(id)
        prefs.edit().putStringSet(key, current).apply()
    }
}

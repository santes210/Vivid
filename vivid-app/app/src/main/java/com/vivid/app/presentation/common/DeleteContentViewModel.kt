package com.vivid.app.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.data.local.dao.PostDao
import com.vivid.app.data.local.dao.ReelDao
import com.vivid.app.data.storage.StorageProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

internal fun ownedMediaKeys(keys: List<String>, collection: String, uid: String): List<String> =
    keys.filter { it.startsWith("$collection/$uid/") && !it.contains("..") }.distinct()

@HiltViewModel
class DeleteContentViewModel @Inject constructor(
    private val auth: FirebaseAuth, private val db: FirebaseFirestore,
    private val storage: StorageProvider, private val posts: PostDao, private val reels: ReelDao
) : ViewModel() {
    private val inFlight = mutableSetOf<String>()

    /** null = unconfirmed deletion; false = deleted, but some file cleanup failed. */
    fun delete(collection: String, id: String, result: (Boolean?) -> Unit) {
        val uid = auth.currentUser?.uid ?: return result(null)
        if (collection !in setOf("posts", "reels") || !Regex("[A-Za-z0-9_-]{1,128}").matches(id)) return result(null)
        val identity = "$collection/$id"
        if (!inFlight.add(identity)) return result(null)
        viewModelScope.launch {
            try {
                val keys = withTimeout(20_000) {
                    db.runTransaction { txn ->
                        val ref = db.collection(collection).document(id)
                        val account = db.collection("users").document(uid)
                        val document = txn.get(ref)
                        val user = txn.get(account)
                        check(auth.currentUser?.uid == uid)
                        if (document.exists()) check(document.getString("userId") == uid) { "Not the author" }
                        val media = if (document.exists()) ownedMediaKeys(
                            listOf("storageKey", "thumbnailStorageKey", "musicStorageKey").map { document.getString(it).orEmpty() }, collection, uid
                        ) else emptyList()
                        if (document.exists()) txn.delete(ref)
                        if (user.exists()) txn.update(account, "pinnedContent", FieldValue.arrayRemove(identity))
                        media
                    }.await()
                }
                var cleaned = true
                // Server deletion succeeded. Cleanup failure must not pretend the post still exists.
                try { if (collection == "reels") reels.deleteReel(id) else posts.deletePost(id) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { cleaned = false }
                for (key in keys) {
                    try {
                        val removed = withTimeout(15_000) {
                            // Preserve media reused by another publication of this author.
                            var referenced = false
                            for (source in listOf("posts", "reels", "stories")) {
                                for (field in listOf("storageKey", "thumbnailStorageKey", "musicStorageKey")) {
                                    if (!db.collection(source).whereEqualTo("userId", uid).whereEqualTo(field, key)
                                            .limit(1).get(com.google.firebase.firestore.Source.SERVER).await().isEmpty) {
                                        referenced = true
                                        break
                                    }
                                }
                                if (referenced) break
                            }
                            if (referenced) true else storage.deleteFile(key)
                        }
                        if (!removed) cleaned = false
                    } catch (_: TimeoutCancellationException) { cleaned = false }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { cleaned = false }
                }
                result(cleaned)
            } catch (_: TimeoutCancellationException) { result(null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { result(null) }
            finally { inFlight.remove(identity) }
        }
    }
}

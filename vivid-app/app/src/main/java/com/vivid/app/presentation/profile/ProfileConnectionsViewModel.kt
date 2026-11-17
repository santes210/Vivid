package com.vivid.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.vivid.app.domain.repository.SocialUserPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

internal data class ConnectionsState(
    val users: List<SocialUserPreview> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val loaded: Boolean = false,
    val hasMore: Boolean = true
)

@HiltViewModel
class ProfileConnectionsViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val states = MutableStateFlow<Map<String, ConnectionsState>>(emptyMap())
    internal val pages = states.asStateFlow()
    private val cursors = mutableMapOf<String, DocumentSnapshot>()

    internal fun load(uid: String, followers: Boolean, refresh: Boolean = false) {
        if (uid.isBlank() || auth.currentUser?.uid != uid) return
        val key = "$uid/$followers"
        val old = states.value[key] ?: ConnectionsState()
        if (old.loading || (!refresh && !old.hasMore)) return
        states.value = states.value + (key to old.copy(loading = true, failed = false))
        viewModelScope.launch {
            try {
                withTimeout(30_000) {
                    var query = db.collection("users").document(uid)
                        .collection(if (followers) "followers" else "following")
                        .orderBy(FieldPath.documentId()).limit(30)
                    if (!refresh) cursors[key]?.let { query = query.startAfter(it) }
                    // Default source preserves Firestore's existing offline cache.
                    val page = query.get().await()
                    val users = page.documents.mapNotNull { edge ->
                        val doc = db.collection("users").document(edge.id).get().await()
                        if (!doc.exists()) null else SocialUserPreview(
                            uid = edge.id,
                            username = doc.getString("username").orEmpty(),
                            displayName = doc.getString("displayName") ?: doc.getString("username").orEmpty(),
                            avatarUrl = doc.getString("avatarUrl").orEmpty()
                        )
                    }
                    if (auth.currentUser?.uid != uid) return@withTimeout
                    if (refresh) cursors.remove(key)
                    page.documents.lastOrNull()?.let { cursors[key] = it }
                    states.value = states.value + (key to ConnectionsState(
                        users = ((if (refresh) emptyList() else old.users) + users).distinctBy { it.uid },
                        loaded = true, hasMore = page.size() == 30
                    ))
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                states.value = states.value + (key to old.copy(failed = true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                states.value = states.value + (key to old.copy(failed = true))
            }
        }
    }
}

package com.vivid.app.presentation.stories

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.vivid.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

private data class StoryComment(val id: String, val userId: String, val username: String, val text: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryCommentsSheet(storyId: String, ownerId: String, onDismiss: () -> Unit) {
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val collection = remember(storyId) { FirebaseFirestore.getInstance().collection("stories").document(storyId).collection("comments") }
    var comments by remember(storyId) { mutableStateOf<List<StoryComment>>(emptyList()) }
    var loading by remember(storyId) { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var text by rememberSaveable(storyId, uid) { mutableStateOf("") }
    var attemptId by rememberSaveable(storyId, uid) { mutableStateOf("") }
    var attemptText by rememberSaveable(storyId, uid) { mutableStateOf("") }
    var deleting by remember { mutableStateOf<StoryComment?>(null) }
    DisposableEffect(storyId, uid, retry) {
        loading = true; loadError = false
        val timeout = scope.launch { delay(15_000); if (loading) { loading = false; loadError = true } }
        val listener = collection.orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { snapshot, error ->
                // Do not surface cached comments until this session has current server access.
                if (error == null && snapshot?.metadata?.isFromCache == true) return@addSnapshotListener
                timeout.cancel(); loading = false; loadError = error != null
                if (snapshot != null) comments = snapshot.documents.map {
                    StoryComment(it.id, it.getString("userId").orEmpty(), it.getString("username").orEmpty(), it.getString("text").orEmpty())
                }.reversed()
            }
        onDispose { timeout.cancel(); listener.remove() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.story_comments), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.story_comments_audience), style = MaterialTheme.typography.bodySmall)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (loadError) {
                Text(stringResource(R.string.story_comments_failed), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
            }
            else if (!loading && comments.isEmpty()) Text(stringResource(R.string.story_comments_empty))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(if (loadError) emptyList() else comments, key = { it.id }) { comment ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(comment.username, style = MaterialTheme.typography.titleSmall)
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                            if (uid == comment.userId || uid == ownerId) TextButton(enabled = !busy, onClick = { deleting = comment }) {
                                Text(stringResource(R.string.story_comment_delete))
                            }
                        }
                    }
                }
            }
            OutlinedTextField(text, onValueChange = { text = it.take(1000); sendError = false },
                enabled = !busy, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                label = { Text(stringResource(R.string.story_comment_write)) }, shape = MaterialTheme.shapes.extraLarge)
            if (sendError) Text(stringResource(R.string.story_comment_failed), color = MaterialTheme.colorScheme.error)
            FilledTonalButton(enabled = !busy && text.isNotBlank() && uid.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                val message = text.trim()
                if (attemptId.isBlank() || attemptText != message) { attemptId = UUID.randomUUID().toString(); attemptText = message }
                val ref = collection.document(attemptId)
                busy = true; sendError = false
                scope.launch {
                    try {
                        withTimeout(15_000) {
                            FirebaseFirestore.getInstance().runTransaction { txn ->
                                if (!txn.get(ref).exists()) txn.set(ref, mapOf(
                                    "userId" to uid, "username" to (user?.displayName ?: "Usuario").take(100),
                                    "text" to message, "createdAt" to FieldValue.serverTimestamp()))
                                null
                            }.await()
                        }
                        text = ""; attemptId = ""; attemptText = ""
                    } catch (_: TimeoutCancellationException) { sendError = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { sendError = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(if (busy) R.string.story_comment_sending else R.string.story_comment_publish)) }
        }
    }
    deleting?.let { comment ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.story_comment_delete)) },
            text = { Text(comment.text) }, confirmButton = { TextButton(onClick = {
                deleting = null; busy = true
                scope.launch {
                    try { withTimeout(15_000) { collection.document(comment.id).delete().await() } }
                    catch (_: TimeoutCancellationException) { sendError = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { sendError = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.story_comment_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}

package com.vivid.app.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vivid.app.ui.components.HashtagCaption
import com.vivid.app.ui.components.UserAvatar
import com.vivid.app.util.rememberPooledExoPlayer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.vivid.app.R
import com.vivid.app.util.SettingsManager
import kotlinx.coroutines.launch
import com.vivid.app.theme.LocalVividAccents
import com.vivid.app.theme.VividExpressiveShapes
import com.vivid.app.theme.VividSpace
import com.vivid.app.ui.components.VividAlertDialog

// ── PostViewerDialog ──

@Composable
internal fun PostViewerDialog(posts: List<PostData>, initialIndex: Int, onDismiss: () -> Unit) {
    if (initialIndex !in posts.indices) { onDismiss(); return }
    val post = posts[initialIndex]
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = VividExpressiveShapes.MediumCard) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(VividSpace.s),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(post.username, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_close)) }
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    when {
                        post.isVideo && post.videoUrl.isNotBlank() -> {
                            val player = rememberPooledExoPlayer(
                                mediaUrl = post.videoUrl,
                                playWhenReady = true,
                                repeatMode = Player.REPEAT_MODE_ALL
                            )
                            AndroidView(factory = { c -> PlayerView(c).apply { this.player = player } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
                        }
                        else -> PostImage(post.imageBase64, post.imageUrl, post.username, useDefaultHeight = false)
                    }
                }
                if (post.caption.isNotBlank()) {
                    HashtagCaption(
                        text = post.caption,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// ── PostDetailsDialog ──

@Composable
internal fun PostDetailsDialog(post: PostData, onDismiss: () -> Unit) {
    VividAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(post.username, style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(20.dp), tint = LocalVividAccents.current.like)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.details_likes, post.likesCount), style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.details_comments, post.commentsCount), style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text(formatTimestamp(post.timestamp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

// ── EditPostDialog ──

@Composable
internal fun EditPostDialog(
    post: PostData,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: FeedViewModel
) {
    var text by remember { mutableStateOf(post.caption) }
    VividAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_post_title), fontWeight = FontWeight.Bold) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Button(onClick = {
                viewModel.editPostCaption(post.id, text)
                onSaved(text.trim())
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

// ── PostCommentsSheet ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostCommentsSheet(
    post: PostData,
    viewModel: FeedViewModel,
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = viewModel.currentUserId
    var comments by remember { mutableStateOf<List<PostComment>>(emptyList()) }
    var likedCommentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }
    var editingComment by remember { mutableStateOf<PostComment?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(post.id) {
        val listener = db.collection("posts").document(post.id).collection("comments")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                comments = snap?.documents?.mapNotNull { doc ->
                    PostComment(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        username = doc.getString("username") ?: "?",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        avatarUrl = doc.getString("avatarUrl") ?: "",
                        avatarBase64 = doc.getString("avatarBase64") ?: "",
                        likesCount = (doc.getLong("likesCount") ?: 0L).toInt(),
                        isEdited = doc.getBoolean("isEdited") ?: false,
                        parentId = doc.getString("parentId"),
                        replyToUsername = doc.getString("replyToUsername") ?: ""
                    )
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    DisposableEffect(post.id, currentUserId) {
        var listener: ListenerRegistration? = null
        if (currentUserId.isNotBlank()) {
            listener = db.collectionGroup("likes")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener { snap, _ ->
                    likedCommentIds = snap?.documents?.mapNotNull { doc ->
                        if (doc.reference.path.contains("posts/${post.id}/comments/")) {
                            doc.reference.parent.parent?.id
                        } else null
                    }?.toSet().orEmpty()
                }
        }
        onDispose { listener?.remove() }
    }

    fun toggleCommentLike(comment: PostComment) {
        if (currentUserId.isBlank()) return
        val isLiked = comment.id in likedCommentIds
        val newLiked = !isLiked
        likedCommentIds = if (newLiked) likedCommentIds + comment.id else likedCommentIds - comment.id

        viewModel.toggleCommentLike(
            postId = post.id,
            commentId = comment.id,
            currentUserId = currentUserId,
            shouldLike = newLiked,
            onFailure = {
                likedCommentIds = if (newLiked) likedCommentIds - comment.id else likedCommentIds + comment.id
            }
        )
    }

    fun deleteComment(comment: PostComment) {
        viewModel.deleteComment(post.id, comment.id) { error ->
            if (error != null) errorMsg = error.message
        }
    }

    val rootComments = remember(comments) { comments.filter { it.parentId.isNullOrBlank() } }
    val repliesMap = remember(comments) {
        comments.filter { !it.parentId.isNullOrBlank() }.groupBy { it.parentId!! }
    }

    VividAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.comments_title, comments.size), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                if (comments.isEmpty()) {
                    Text(stringResource(R.string.comments_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items(rootComments, key = { it.id }) { rootComment ->
                            CommentRow(
                                comment = rootComment.copy(isLiked = rootComment.id in likedCommentIds),
                                currentUserId = currentUserId,
                                postAuthorId = post.userId,
                                onReply = { replyingTo = rootComment },
                                onLike = { toggleCommentLike(rootComment) },
                                onEdit = { editingComment = rootComment },
                                onDelete = { deleteComment(rootComment) }
                            )

                            val replies = repliesMap[rootComment.id].orEmpty()
                            replies.forEach { replyComment ->
                                Spacer(Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 18.dp, end = VividSpace.xs, top = VividSpace.xxs, bottom = VividSpace.xxs)
                                            .width(2.dp).height(32.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        CommentRow(
                                            comment = replyComment.copy(isLiked = replyComment.id in likedCommentIds),
                                            currentUserId = currentUserId,
                                            postAuthorId = post.userId,
                                            isReply = true,
                                            onReply = { replyingTo = replyComment },
                                            onLike = { toggleCommentLike(replyComment) },
                                            onEdit = { editingComment = replyComment },
                                            onDelete = { deleteComment(replyComment) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(VividSpace.s))

                // Replying To Banner
                replyingTo?.let { replyTarget ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = VividExpressiveShapes.Media,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = VividSpace.s, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.comments_replying_to, replyTarget.username),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Comment Input Box
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = {
                            Text(
                                if (replyingTo != null) stringResource(R.string.comments_reply_placeholder)
                                else stringResource(R.string.comments_placeholder)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = VividExpressiveShapes.FieldFocused
                    )
                    Spacer(Modifier.width(VividSpace.xs))
                    FilledTonalButton(
                        onClick = {
                            if (commentText.isBlank() || isSending) return@FilledTonalButton
                            isSending = true
                            errorMsg = null
                            val replyTarget = replyingTo
                            val targetParentId = replyTarget?.let { it.parentId.takeIf { p -> !p.isNullOrBlank() } ?: it.id }
                            val targetReplyToUser = replyTarget?.username.orEmpty()
                            val textToSend = commentText.trim()

                            viewModel.addComment(
                                postId = post.id,
                                text = textToSend,
                                parentId = targetParentId,
                                replyToUsername = targetReplyToUser,
                                onSuccess = {
                                    commentText = ""
                                    replyingTo = null
                                    isSending = false
                                },
                                onFailure = { e ->
                                    errorMsg = e.message
                                    isSending = false
                                }
                            )
                        },
                        enabled = !isSending,
                        shape = VividExpressiveShapes.PrimaryButton
                    ) {
                        Text(
                            if (isSending) stringResource(R.string.comments_sending) else stringResource(R.string.comments_send),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )

    // Editing Dialog
    editingComment?.let { commentToEdit ->
        var editText by remember(commentToEdit) { mutableStateOf(commentToEdit.text) }
        VividAlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text(stringResource(R.string.comments_edit_title), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    shape = VividExpressiveShapes.FieldResting
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (editText.isNotBlank()) {
                        viewModel.editComment(post.id, commentToEdit.id, editText) { error ->
                            if (error != null) errorMsg = error.message
                        }
                    }
                    editingComment = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

// ── CommentRow ──

@Composable
internal fun CommentRow(
    comment: PostComment,
    currentUserId: String,
    postAuthorId: String,
    isReply: Boolean = false,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CommentAvatar(comment)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.username, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                if (comment.isEdited) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.comments_edited_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            if (comment.replyToUsername.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@${comment.replyToUsername} ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(SettingsManager.filterOffensiveWords(comment.text), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(VividSpace.xxs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.comments_reply),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onReply() }
                )
                if (comment.userId == currentUserId) {
                    Spacer(Modifier.width(VividSpace.s))
                    Text(
                        stringResource(R.string.comments_edit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onEdit() }
                    )
                }
                if (comment.userId == currentUserId || currentUserId == postAuthorId) {
                    Spacer(Modifier.width(VividSpace.s))
                    Text(
                        stringResource(R.string.comments_delete),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { onDelete() }
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onLike, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (comment.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.comments_like),
                    tint = if (comment.isLiked) LocalVividAccents.current.like else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (comment.likesCount > 0) {
                Text(comment.likesCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── CommentAvatar ──

@Composable
private fun CommentAvatar(comment: PostComment) {
    UserAvatar(imageUrl = comment.avatarUrl, name = comment.username, size = 36.dp)
}

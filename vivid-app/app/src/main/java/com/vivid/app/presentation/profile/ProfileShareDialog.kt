package com.vivid.app.presentation.profile

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.vivid.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ProfileShareDialog(uid: String, username: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val link = remember(uid) { runCatching { ProfileLinks.create(uid) }.getOrNull() }
    var feedback by remember { mutableStateOf<Int?>(null) }
    var qrFailed by remember { mutableStateOf(false) }
    val qr by produceState<Bitmap?>(null, link) {
        value = withContext(Dispatchers.Default) {
            link?.let {
                runCatching {
                    val matrix = QRCodeWriter().encode(it, BarcodeFormat.QR_CODE, 600, 600,
                        mapOf(EncodeHintType.MARGIN to 4))
                    val pixels = IntArray(600 * 600) { index ->
                        if (matrix[index % 600, index / 600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    }
                    Bitmap.createBitmap(pixels, 600, 600, Bitmap.Config.ARGB_8888)
                }.getOrNull()
            }
        }
        qrFailed = value == null
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_share_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@$username", style = MaterialTheme.typography.titleMedium)
                if (qr != null) Image(qr!!.asImageBitmap(), stringResource(R.string.profile_qr_description),
                    Modifier.fillMaxWidth().aspectRatio(1f))
                else if (!qrFailed) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(stringResource(R.string.profile_qr_failed))
                Text(stringResource(R.string.profile_link_requires_app), style = MaterialTheme.typography.bodySmall)
                if (link != null) {
                    Text(link, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { clipboard.setText(AnnotatedString(link)); feedback = R.string.profile_link_copied }) {
                        Text(stringResource(R.string.profile_copy_link))
                    }
                }
                feedback?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { FilledTonalButton(enabled = link != null, onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
            if (runCatching { context.startActivity(Intent.createChooser(intent, null)) }.isFailure)
                feedback = R.string.profile_share_failed
        }) { Text(stringResource(R.string.content_share_title)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.viewer_close)) } })
}

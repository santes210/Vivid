package com.vivid.app.data.storage

import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** Tracks bytes written during transfer, not acknowledgement or metadata completion. */
internal class UploadProgressBody(
    private val delegate: RequestBody,
    private val checkActive: () -> Unit = {},
    private val progress: (Int) -> Unit
) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        var previous = -1
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                checkActive()
                super.write(source, byteCount)
                written += byteCount
                val percent = if (total > 0) ((written.toDouble() / total) * 100).toInt().coerceIn(0, 100) else 0
                if (percent != previous) { previous = percent; progress(percent) }
            }
        }.buffer()
        checkActive()
        delegate.writeTo(counting)
        counting.flush() // Do not close OkHttp's sink.
    }
}

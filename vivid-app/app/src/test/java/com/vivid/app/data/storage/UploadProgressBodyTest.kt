package com.vivid.app.data.storage

import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UploadProgressBodyTest {
    @Test fun `body preserves bytes and reports completed transfer`() {
        val bytes = ByteArray(20000) { (it % 127).toByte() }
        val progress = mutableListOf<Int>()
        val output = Buffer()
        UploadProgressBody(bytes.toRequestBody(), progress = { progress += it }).writeTo(output)
        assertArrayEquals(bytes, output.readByteArray())
        assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }

    @Test fun `inactive upload stops before writing bytes`() {
        val sink = Buffer()
        try {
            UploadProgressBody("payload".toRequestBody(), checkActive = { throw java.util.concurrent.CancellationException() }, progress = {}).writeTo(sink)
            fail("Expected cancellation")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(0L, sink.size)
    }
}

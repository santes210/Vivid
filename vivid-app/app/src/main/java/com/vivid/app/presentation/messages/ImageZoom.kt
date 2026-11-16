package com.vivid.app.presentation.messages

/** Pure viewport math, shared by gestures and accessible zoom buttons. */
internal data class ImageZoom(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun transform(
        factor: Float, panX: Float, panY: Float, width: Float, height: Float,
        focusX: Float = 0f, focusY: Float = 0f
    ): ImageZoom {
        if (!factor.isFinite() || factor <= 0f || !panX.isFinite() || !panY.isFinite() ||
            !width.isFinite() || !height.isFinite() || !focusX.isFinite() || !focusY.isFinite()) return this
        val next = (scale * factor).coerceIn(1f, 4f)
        if (next == 1f) return ImageZoom()
        val ratio = next / scale
        val boundX = width.coerceAtLeast(0f) * (next - 1f) / 2f
        val boundY = height.coerceAtLeast(0f) * (next - 1f) / 2f
        return ImageZoom(next,
            ((x - focusX) * ratio + focusX + panX).coerceIn(-boundX, boundX),
            ((y - focusY) * ratio + focusY + panY).coerceIn(-boundY, boundY))
    }
}

package com.adamoutler.ssh.ui.components

object TerminalScaleHandler {
    const val MIN_FONT_SIZE_SP = 8
    const val MAX_FONT_SIZE_SP = 32
    private const val ZOOM_OUT_THRESHOLD = 0.9f
    private const val ZOOM_IN_THRESHOLD = 1.1f

    fun clampFontSize(sizeSp: Int): Int = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)

    fun onScale(
        scale: Float,
        currentFontSize: Int,
        onUpdateFontSize: (Int) -> Unit,
    ): Float {
        if (scale < ZOOM_OUT_THRESHOLD || scale > ZOOM_IN_THRESHOLD) {
            val delta = if (scale > 1.0f) 1 else -1
            val newSize = clampFontSize(currentFontSize + delta)
            if (newSize != currentFontSize) {
                onUpdateFontSize(newSize)
            }
            return 1.0f
        }
        return scale
    }
}

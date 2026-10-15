package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalScaleHandlerTest {

    @Test
    fun testClampFontSize_LowerBound() {
        assertEquals(8, TerminalScaleHandler.clampFontSize(4))
        assertEquals(8, TerminalScaleHandler.clampFontSize(0))
        assertEquals(8, TerminalScaleHandler.clampFontSize(-5))
        assertEquals(8, TerminalScaleHandler.clampFontSize(8))
    }

    @Test
    fun testClampFontSize_UpperBound() {
        assertEquals(32, TerminalScaleHandler.clampFontSize(33))
        assertEquals(32, TerminalScaleHandler.clampFontSize(50))
        assertEquals(32, TerminalScaleHandler.clampFontSize(32))
    }

    @Test
    fun testClampFontSize_WithinBounds() {
        assertEquals(14, TerminalScaleHandler.clampFontSize(14))
        assertEquals(16, TerminalScaleHandler.clampFontSize(16))
        assertEquals(24, TerminalScaleHandler.clampFontSize(24))
    }

    @Test
    fun testOnScale_ZoomInCrossesThreshold() {
        var updatedSize: Int? = null
        val returnedScale = TerminalScaleHandler.onScale(
            scale = 1.15f,
            currentFontSize = 14,
            onUpdateFontSize = { updatedSize = it },
        )

        assertEquals(1.0f, returnedScale, 0.001f)
        assertEquals(15, updatedSize)
    }

    @Test
    fun testOnScale_ZoomOutCrossesThreshold() {
        var updatedSize: Int? = null
        val returnedScale = TerminalScaleHandler.onScale(
            scale = 0.85f,
            currentFontSize = 14,
            onUpdateFontSize = { updatedSize = it },
        )

        assertEquals(1.0f, returnedScale, 0.001f)
        assertEquals(13, updatedSize)
    }

    @Test
    fun testOnScale_IntermediateAccumulation() {
        var updatedSize: Int? = null

        val scale1 = TerminalScaleHandler.onScale(
            scale = 0.95f,
            currentFontSize = 14,
            onUpdateFontSize = { updatedSize = it },
        )
        assertEquals(0.95f, scale1, 0.001f)
        assertEquals(null, updatedSize)

        val scale2 = TerminalScaleHandler.onScale(
            scale = 1.05f,
            currentFontSize = 14,
            onUpdateFontSize = { updatedSize = it },
        )
        assertEquals(1.05f, scale2, 0.001f)
        assertEquals(null, updatedSize)

        val scale3 = TerminalScaleHandler.onScale(
            scale = 1.0f,
            currentFontSize = 14,
            onUpdateFontSize = { updatedSize = it },
        )
        assertEquals(1.0f, scale3, 0.001f)
        assertEquals(null, updatedSize)
    }

    @Test
    fun testOnScale_AtMaxBoundDoesNotEmitUpdate() {
        var updatedSize: Int? = null
        val returnedScale = TerminalScaleHandler.onScale(
            scale = 1.25f,
            currentFontSize = 32,
            onUpdateFontSize = { updatedSize = it },
        )

        assertEquals(1.0f, returnedScale, 0.001f)
        assertEquals(null, updatedSize)
    }

    @Test
    fun testOnScale_AtMinBoundDoesNotEmitUpdate() {
        var updatedSize: Int? = null
        val returnedScale = TerminalScaleHandler.onScale(
            scale = 0.8f,
            currentFontSize = 8,
            onUpdateFontSize = { updatedSize = it },
        )

        assertEquals(1.0f, returnedScale, 0.001f)
        assertEquals(null, updatedSize)
    }
}

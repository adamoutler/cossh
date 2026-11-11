package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalKeyMapperTest {

    @Test
    fun testVolumeKeys_defaultInterceptsAndTriggersZoom() {
        var upTriggered = false
        var downTriggered = false
        var zoomTriggered = false

        val mapper = TerminalKeyMapper(
            onVolumeUp = { upTriggered = true },
            onVolumeDown = { downTriggered = true },
            onZoom = { zoomTriggered = true },
        )

        // KEYCODE_VOLUME_UP
        val upHandled = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP)
        assertTrue("Volume up should be consumed when intercepted", upHandled)
        assertTrue("Volume up action should be invoked", upTriggered)
        assertTrue("onZoom callback should be invoked", zoomTriggered)

        // Reset
        zoomTriggered = false

        // KEYCODE_VOLUME_DOWN
        val downHandled = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertTrue("Volume down should be consumed when intercepted", downHandled)
        assertTrue("Volume down action should be invoked", downTriggered)
        assertTrue("onZoom callback should be invoked", zoomTriggered)

        // onKeyUp
        assertTrue(mapper.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(mapper.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun testVolumeKeys_whenInterceptDisabled_returnsFalseWithoutZoom() {
        var upTriggered = false
        var downTriggered = false
        var zoomTriggered = false

        val mapper = TerminalKeyMapper(
            onVolumeUp = { upTriggered = true },
            onVolumeDown = { downTriggered = true },
            onZoom = { zoomTriggered = true },
            interceptVolumeKeys = false,
        )

        // KEYCODE_VOLUME_UP
        val upHandled = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse("Volume up should not be consumed when interceptVolumeKeys is false", upHandled)
        assertFalse("onVolumeUp must not be called when interceptVolumeKeys is false", upTriggered)
        assertFalse("onZoom must not be called when interceptVolumeKeys is false", zoomTriggered)

        // KEYCODE_VOLUME_DOWN
        val downHandled = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertFalse("Volume down should not be consumed when interceptVolumeKeys is false", downHandled)
        assertFalse("onVolumeDown must not be called when interceptVolumeKeys is false", downTriggered)
        assertFalse("onZoom must not be called when interceptVolumeKeys is false", zoomTriggered)

        // onKeyUp
        assertFalse(mapper.onKeyUp(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(mapper.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN))
    }

    @Test
    fun testVolumeKeys_parameterOverrideOnKeyDown() {
        var upTriggered = false
        var downTriggered = false

        val mapper = TerminalKeyMapper(
            onVolumeUp = { upTriggered = true },
            onVolumeDown = { downTriggered = true },
            interceptVolumeKeys = true,
        )

        // Override to false per call
        val handledUp = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, interceptVolumeKeys = false)
        assertFalse("Per-call interceptVolumeKeys=false should prevent consumption", handledUp)
        assertFalse("Per-call interceptVolumeKeys=false should prevent onVolumeUp", upTriggered)

        val handledDown = mapper.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN, interceptVolumeKeys = false)
        assertFalse("Per-call interceptVolumeKeys=false should prevent consumption", handledDown)
        assertFalse("Per-call interceptVolumeKeys=false should prevent onVolumeDown", downTriggered)

        // HandleKeyDown alias
        assertFalse(mapper.handleKeyDown(KeyEvent.KEYCODE_VOLUME_UP, interceptVolumeKeys = false))
        assertFalse(mapper.handleKeyUp(KeyEvent.KEYCODE_VOLUME_UP, interceptVolumeKeys = false))
    }
}

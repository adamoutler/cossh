package com.adamoutler.ssh.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TerminalCursorSettingsTest {

    private lateinit var app: Application
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: TerminalViewModel

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(app)
        viewModel = TerminalViewModel(app)
    }

    @Test
    fun testCursorStyleEnumMapping() {
        assertEquals(TerminalCursorStyle.BLOCK, TerminalCursorStyle.fromId("BLOCK"))
        assertEquals(TerminalCursorStyle.UNDERLINE, TerminalCursorStyle.fromId("UNDERLINE"))
        assertEquals(TerminalCursorStyle.VERTICAL_BAR, TerminalCursorStyle.fromId("BAR"))
        assertEquals(TerminalCursorStyle.BLOCK, TerminalCursorStyle.fromId("INVALID"))
        assertEquals(TerminalCursorStyle.BLOCK, TerminalCursorStyle.fromId(null))

        assertEquals(TerminalCursorStyle.BLOCK, TerminalCursorStyle.fromStyleCode(0))
        assertEquals(TerminalCursorStyle.UNDERLINE, TerminalCursorStyle.fromStyleCode(1))
        assertEquals(TerminalCursorStyle.VERTICAL_BAR, TerminalCursorStyle.fromStyleCode(2))
        assertEquals(TerminalCursorStyle.BLOCK, TerminalCursorStyle.fromStyleCode(99))
    }

    @Test
    fun testSettingsManagerCursorDefaultsAndPersistence() {
        assertEquals("BLOCK", settingsManager.globalCursorStyle)
        assertTrue(settingsManager.globalCursorBlink)

        settingsManager.globalCursorStyle = "UNDERLINE"
        assertEquals("UNDERLINE", settingsManager.globalCursorStyle)

        settingsManager.globalCursorBlink = false
        assertFalse(settingsManager.globalCursorBlink)
    }

    @Test
    fun testViewModelCursorUpdate() {
        viewModel.updateCursorStyle(TerminalCursorStyle.VERTICAL_BAR)
        assertEquals(TerminalCursorStyle.VERTICAL_BAR, viewModel.cursorStyleFlow.value)
        assertEquals(2, viewModel.getTerminalCursorStyle())

        viewModel.updateCursorBlink(false)
        assertFalse(viewModel.cursorBlinkFlow.value)
    }
}

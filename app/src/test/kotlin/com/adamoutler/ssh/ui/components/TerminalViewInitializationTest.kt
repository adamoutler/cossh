package com.adamoutler.ssh.ui.components

import android.content.Context
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.termux.view.TerminalView
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalViewInitializationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testCallingSetTypefaceBeforeSetTextSize_throwsNpeWithoutRenderer() {
        val terminalView = TerminalView(context, null)
        try {
            terminalView.setTypeface(Typeface.MONOSPACE)
            fail("Expected NullPointerException when setTypeface() is invoked before mRenderer initialization")
        } catch (e: NullPointerException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun testCallingSetTextSizeBeforeSetTypeface_succeedsWithoutException() {
        val terminalView = TerminalView(context, null)
        terminalView.setTextSize(36)
        terminalView.setTypeface(Typeface.MONOSPACE)
        assertNotNull(terminalView)
    }
}

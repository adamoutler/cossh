package com.adamoutler.ssh.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalScreenCopyTest {

    @Test
    fun testTerminalCopyTextStripsTrailingSpaces() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = TerminalViewModel(app)

        val dummySession = viewModel.getOrCreateSession("test", context)

        val originalText = "User copied text   \n  \n"
        viewModel.onCopyTextToClipboard(dummySession, originalText)

        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboardManager.primaryClip

        assertNotNull(clipData)
        val copiedText = clipData?.getItemAt(0)?.text?.toString()
        assertEquals("User copied text", copiedText)
    }
}

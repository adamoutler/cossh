package com.adamoutler.ssh.ui.components

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.annotation.UiThreadTest
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalBufferSecurityTest {

    @Test
    @UiThreadTest
    fun testClosedSessionsAreSecurelyScrubbed() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TerminalViewModel(app)
        
        val session = viewModel.getOrCreateSession("test-session", app)
        session.updateSize(80, 24, 10, 20)
        val emulator = session.emulator!!
        
        // Assert limit is set
        val screenField = emulator.javaClass.getDeclaredMethod("getScreen")
        screenField.isAccessible = true
        val buffer = screenField.invoke(emulator)
        
        // Emulate writing lots of sensitive data
        val secretData = "MY_SUPER_SECRET_PASSWORD_12345"
        val bytes = secretData.toByteArray()
        emulator.append(bytes, bytes.size)
        
        // Verify buffer has the data
        val mLinesField = buffer.javaClass.getDeclaredField("mLines")
        mLinesField.isAccessible = true
        val lines = mLinesField.get(buffer) as Array<*>
        
        var foundSecret = false
        val mTextField = lines[0]!!.javaClass.getDeclaredField("mText")
        mTextField.isAccessible = true
        for (row in lines) {
            if (row != null) {
                val chars = mTextField.get(row) as CharArray
                val str = String(chars)
                if (str.contains("MY_SUPER_SECRET_PASSWORD")) {
                    foundSecret = true
                }
            }
        }
        assertTrue("Buffer should contain secret data before scrubbing", foundSecret)
        
        // Trigger session finish
        viewModel.onSessionFinished(session)
        
        // Verify scrubbing
        var foundSecretAfter = false
        var allZeroed = true
        for (row in lines) {
            if (row != null) {
                val chars = mTextField.get(row) as CharArray
                val str = String(chars)
                if (str.contains("MY_SUPER_SECRET_PASSWORD")) {
                    foundSecretAfter = true
                    println("Found secret after: $str")
                }
                for (c in chars) {
                    if (c != '\u0000' && c != ' ') { // Termux initializes with spaces sometimes, but we explicitly fill with \u0000
                        allZeroed = false
                        break
                    }
                }
            }
        }
        
        assertTrue("Buffer should NOT contain secret data after scrubbing", !foundSecretAfter)
        assertTrue("All text arrays should be completely zeroed out", allZeroed)
    }
}

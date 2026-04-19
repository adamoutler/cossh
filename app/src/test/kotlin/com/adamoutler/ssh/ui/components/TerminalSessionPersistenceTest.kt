package com.adamoutler.ssh.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TerminalSessionPersistenceTest {

    @Test
    fun testTerminalSessionIsPersistedInProvider() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = TerminalViewModel()
        
        val session1 = viewModel.getOrCreateSession("test", context)
        assertNotNull(session1)
        
        val session2 = viewModel.getOrCreateSession("test", context)
        
        // Assert it is the exact same instance
        assertTrue(session1 === session2)
    }
}

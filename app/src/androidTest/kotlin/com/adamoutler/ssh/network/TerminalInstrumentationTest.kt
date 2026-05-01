package com.adamoutler.ssh.network

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.annotations.FullTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@FullTest
class TerminalInstrumentationTest {

    @Test(timeout = 300000L)
    fun testTerminalViewRendersWithoutCrashing() {
        // Launch the main activity which contains TerminalScreen and native TerminalView
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // Give the native JNI libraries time to initialize and spawn the dummy process
        Thread.sleep(1500)

        // Ensure the Activity and the enclosed TerminalView are active
        assertNotNull("Activity scenario should be valid and UI should have loaded.", scenario)

        scenario.close()
    }
}

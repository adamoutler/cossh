package com.adamoutler.ssh

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLaunchTest {

    @Test
    fun testAppLaunchesSuccessfully() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        assertEquals(Lifecycle.State.RESUMED, scenario.state)
        scenario.close()
    }
}

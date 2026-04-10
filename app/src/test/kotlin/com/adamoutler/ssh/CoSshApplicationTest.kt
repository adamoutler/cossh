package com.adamoutler.ssh

import com.adamoutler.ssh.security.SecureCrashHandler
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CoSshApplicationTest {

    @Test
    fun testSecureCrashHandlerIsRegistered() {
        val app = RuntimeEnvironment.getApplication() as CoSshApplication
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertTrue("SecureCrashHandler should be registered as default uncaught exception handler", defaultHandler is SecureCrashHandler)
    }
}

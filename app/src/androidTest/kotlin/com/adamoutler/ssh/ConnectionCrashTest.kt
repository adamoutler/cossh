package com.adamoutler.ssh

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.adamoutler.ssh.annotations.FullTest
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * @FullTest Note (SSH-49):
 * This is a long-running integration test excluded from the standard fast CI/CD pipeline.
 * To execute this test locally, use the fullTestRun property:
 * ./gradlew connectedAndroidTest -PfullTestRun
 */
@RunWith(AndroidJUnit4::class)
@FullTest
class ConnectionCrashTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
    )

    @Test(timeout = 300000L)
    fun testConnectionAndDataTransmission() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val storageManager = SecurityStorageManager(context)

            // Set headless mode for Android 16/API 35 16KB JNI bypass
            com.adamoutler.ssh.network.SshSessionProvider.isHeadlessTest = true
            com.adamoutler.ssh.network.SshSessionProvider.mockTestTranscript = null
            com.adamoutler.ssh.network.ConnectionStateRepository.sessions.clear()

            // Apply hack to prevent org.apache.sshd ExceptionInInitializerError on Android
            System.setProperty("user.home", context.filesDir.absolutePath)

            // Add a mock profile pointing to the live integration testing environment
            val profile = ConnectionProfile(
                id = "mock-id-ui-crash-test",
                nickname = "UI Crash Test Profile",
                host = "10.0.2.2",
                username = "test",
                authType = AuthType.PASSWORD,
                port = 41111,
            )
            profile.password = java.util.UUID.randomUUID().toString().toByteArray()
            storageManager.saveProfile(profile)

            // Start the activity NOW, after profile is in storage
            val scenario = ActivityScenario.launch(MainActivity::class.java)

            // Wait for UI to load and dismiss 16KB page size compatibility dialog (API 35+)
            device.waitForIdle()
            val textOkButton = device.findObject(UiSelector().textMatches("(?i)ok|continue|got it|close"))
            val resOkButton = device.findObject(UiSelector().resourceId("android:id/button1"))

            if (textOkButton.waitForExists(1500)) {
                textOkButton.click()
                device.waitForIdle()
            } else if (resOkButton.waitForExists(1500)) {
                resOkButton.click()
                device.waitForIdle()
            } else if (device.findObject(UiSelector().textContains("Android App Compatibility")).exists()) {
                device.pressBack()
                device.waitForIdle()
            }
            Thread.sleep(2000)

            // Click the profile using UiAutomator
            val profileSelector = UiSelector().textContains("UI Crash Test")
            var profileNode = device.findObject(profileSelector)

            if (!profileNode.waitForExists(10000)) {
                try {
                    val scrollable = androidx.test.uiautomator.UiScrollable(UiSelector().scrollable(true))
                    scrollable.scrollTextIntoView("UI Crash Test Profile")
                } catch (e: Exception) {
                    // Ignore
                }
                profileNode = device.findObject(profileSelector)
            }
            assertTrue("Profile node should exist. Text on screen was not found.", profileNode.exists())

            val bounds = profileNode.bounds
            val clicked = device.click(device.displayWidth / 2, bounds.centerY())
            assertTrue("Device should click the profile list item", clicked)

            // Wait for connection to establish and PTY output stream
            device.waitForIdle()
            var connected = false
            for (i in 1..40) { // Wait up to 20 seconds for remote connection
                val acceptButton = device.findObject(UiSelector().textMatches("(?i).*accept.*|(?i).*yes.*|(?i).*ok.*|(?i).*continue.*"))
                if (acceptButton.waitForExists(500)) acceptButton.click()
                if (com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream != null) {
                    connected = true
                    break
                }
                Thread.sleep(500)
            }
            assertTrue("SSH Connection should be established", connected)

            // Since server enforces 1 req/sec rate limits, we sleep accordingly between commands
            Thread.sleep(1500)

            // Send multiple requests directly to the PTY
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("test1\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()

            Thread.sleep(1500)
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("test2\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()

            Thread.sleep(1500)
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("exit\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()

            // Wait for SSH to process, echo back, and drop connection
            Thread.sleep(2000)

            // Verify the command output sequence is visible on the screen transcript
            val terminalContent = com.adamoutler.ssh.network.SshSessionProvider.mockTestTranscript?.trim() ?: ""
            println("=== HEADLESS TERMINAL OUTPUT ===")
            println(terminalContent)
            println("=======================")

            assertTrue(
                "Terminal should echo 'test1', actual: '$terminalContent'",
                terminalContent.contains("test1"),
            )
            assertTrue(
                "Terminal should echo 'test2', actual: '$terminalContent'",
                terminalContent.contains("test2"),
            )

            // Ensure the connection was actually dropped server-side
            assertTrue(
                "Terminal output stream should be null once closed by remote exit",
                com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream == null ||
                    terminalContent.contains("Connection closed"),
            )

            // Clean up
            com.adamoutler.ssh.network.SshSessionProvider.isHeadlessTest = false
            storageManager.deleteProfile(profile.id)

            val stopIntent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
            }
            context.startService(stopIntent)
            scenario.close()
            println("=== CONNECTION CRASH TEST PASSED ===")
        }
    }
}

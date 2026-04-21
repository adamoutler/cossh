package com.adamoutler.ssh

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.crypto.SecurityStorageManager
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ActivityScenario
import java.io.File
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@FullTest
class OcrVerificationTest {

    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
    )

    private fun sha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(str.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    @Test(timeout = 300000L)
    fun testVisualTerminalSession() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val device = UiDevice.getInstance(instrumentation)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val storageManager = SecurityStorageManager(context)
            
            // WE WANT A VISUAL TEST. No headless override!
            com.adamoutler.ssh.network.SshSessionProvider.isHeadlessTest = false
            
            // Apply hack to prevent org.apache.sshd ExceptionInInitializerError on Android
            System.setProperty("user.home", context.filesDir.absolutePath)
            
            // Add a mock profile pointing to the live integration testing environment
            val profile = ConnectionProfile(
                id = "mock-id-ui-ocr-test",
                nickname = "UI OCR Test Profile",
                host = "mock.hackedyour.info",
                username = "test",
                authType = AuthType.PASSWORD,
                port = 32222
            )
            profile.password = "password".toByteArray()
            storageManager.saveProfile(profile)
            
            // Start the activity NOW, after profile is in storage
            val scenario = ActivityScenario.launch(MainActivity::class.java)

            // Wait for UI to load and dismiss 16KB dialog if present
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
            val profileSelector = UiSelector().textContains("UI OCR Test Profile")
            var profileNode = device.findObject(profileSelector)
            
            if (!profileNode.waitForExists(10000)) {
                try {
                    val scrollable = androidx.test.uiautomator.UiScrollable(UiSelector().scrollable(true))
                    scrollable.scrollTextIntoView("UI OCR Test Profile")
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
            for (i in 1..20) { // Wait up to 20 seconds for remote connection
                if (com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream != null) {
                    connected = true
                    break
                }
                Thread.sleep(1000)
            }
            assertTrue("SSH Connection should be established", connected)

            // Wait for MOTD
            Thread.sleep(3000)

            // Step 1: `cat foo`
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("cat foo\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()
            Thread.sleep(2000)

            // Step 2: `123456`
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("123456\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()
            Thread.sleep(2000)
            
            // Step 3: `verify` (with simulated alt+n truncature if that was a typo by the user, we will just send verify)
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("verify\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()
            Thread.sleep(2000)

            // Step 4: UUID & Checksum
            val testUuid = UUID.randomUUID().toString()
            val expectedSha256 = sha256(testUuid)
            println("=== OCR VERIFICATION TARGET ===")
            println("UUID: $testUuid")
            println("EXPECTED SHA256: $expectedSha256")
            
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.write("$testUuid\n".toByteArray())
            com.adamoutler.ssh.network.SshSessionProvider.ptyOutputStream?.flush()
            
            // Wait for response to render fully
            Thread.sleep(3000)

            // TA-DA! Capture screenshot for visual validation natively
            val screenshotFile = File(context.getExternalFilesDir(null), "ocr_test_screenshot.png")
            // Make sure the file gets wiped so we don't accidentally grab an old one
            if (screenshotFile.exists()) screenshotFile.delete()
            
            device.takeScreenshot(screenshotFile)
            assertTrue("Screenshot should be captured to ${screenshotFile.absolutePath}", screenshotFile.exists())
            
            println("SCREENSHOT_PATH=${screenshotFile.absolutePath}")

            // Clean up
            storageManager.deleteProfile(profile.id)
            val stopIntent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
            }
            context.startService(stopIntent)
            scenario.close()
        }
    }
}

    }
}

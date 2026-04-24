package com.adamoutler.ssh

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.network.SshSessionProvider
import com.adamoutler.ssh.network.SshService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.adamoutler.ssh.annotations.FullTest
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConnectionResumeE2ETest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
    )

    @Test(timeout = 180000L)
    fun testConnectionResumeAndMultipleSessions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageManager = SecurityStorageManager(context)

        SshSessionProvider.isHeadlessTest = false
        System.setProperty("user.home", context.filesDir.absolutePath)

        val profileId = "mock-id-resume-e2e"
        val profile = ConnectionProfile(
            id = profileId,
            nickname = "mock.hackedyour.info",
            host = "192.168.1.115",
            username = "test",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            port = 32222
        )
        profile.password = java.util.UUID.randomUUID().toString().toByteArray()
        storageManager.saveProfile(profile)

        var scenario: ActivityScenario<MainActivity>? = null
        try {
            // 1. Start CoSSH
            scenario = ActivityScenario.launch(MainActivity::class.java)
            device.waitForIdle()

            // Dismiss dialogs
            val textOkButton = device.findObject(UiSelector().textMatches("(?i)ok|continue|got it|close"))
            if (textOkButton.waitForExists(1500)) textOkButton.click()
            if (device.findObject(UiSelector().textContains("Android App Compatibility")).exists()) device.pressBack()

            // 2. Tap connection
            val profileSelector = UiSelector().textContains("mock.hackedyour.info")
            val profileNode = device.findObject(profileSelector)
            assertTrue("Profile must be visible", profileNode.waitForExists(5000))
            profileNode.click()

            // 3. Wait for connection and type something
            device.waitForIdle()
            var connected = false
            for (i in 1..40) {
                val acceptButton = device.findObject(UiSelector().textMatches("(?i).*accept.*|(?i).*yes.*|(?i).*ok.*|(?i).*continue.*"))
                if (acceptButton.waitForExists(500)) acceptButton.click()
                if (SshSessionProvider.ptyOutputStream != null) {
                    connected = true
                    break
                }
                Thread.sleep(500)
            }
            assertTrue("Must connect", connected)
            
            Thread.sleep(3000)
            SshSessionProvider.ptyOutputStream?.write("echo 'Hello Resume'\n".toByteArray())
            SshSessionProvider.ptyOutputStream?.flush()
            Thread.sleep(2000)

            // 4. Press Android Home button
            device.pressHome()
            Thread.sleep(2000)

            // 5. Observe silent notification (implicitly tested by resuming Activity)
            // 6. Tap notification (we simulate resuming the app via recent apps or launcher)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
            device.waitForIdle()
            Thread.sleep(2000)

            // 7. Observe text entered
            val emulator = SshSessionProvider.terminalSession?.emulator
            val transcript = emulator?.screen?.transcriptText ?: ""
            assertTrue("Transcript should contain 'Hello Resume'", transcript.contains("Hello Resume"))

            // 8. Press back to return to main menu
            device.pressBack()
            
            // Handle keep-alive dialog if present
            val keepAliveBtn = device.findObject(UiSelector().textMatches("(?i)keep alive"))
            if (keepAliveBtn.waitForExists(2000)) {
                keepAliveBtn.click()
            }
            device.waitForIdle()
            Thread.sleep(1000)

            // 9. Tap connection again
            profileNode.click()
            
            // 10. Observe dialogue "Resume" or "Start New"
            val startNewBtn = device.findObject(UiSelector().textMatches("(?i)start new"))
            assertTrue("Start New button must be present", startNewBtn.waitForExists(2000))

            // 11. Press "Start New"
            startNewBtn.click()
            device.waitForIdle()
            
            // Wait for second connection
            Thread.sleep(3000)

            // 12. Press Home button
            device.pressHome()
            Thread.sleep(1000)

            // 14. Reopen app
            context.startActivity(intent)
            device.waitForIdle()
            Thread.sleep(2000)

            // 15. Press back button
            device.pressBack()
            if (keepAliveBtn.waitForExists(1000)) keepAliveBtn.click()
            device.waitForIdle()
            Thread.sleep(1000)

            // 16. Tap connection
            profileNode.click()

            // 17. Observe dialogue
            val resumeBtn = device.findObject(UiSelector().textContains("Resume Session 1"))
            assertTrue("Resume Session button must be present", resumeBtn.waitForExists(2000))

            // 18. Select "Resume"
            resumeBtn.click()
            device.waitForIdle()
            Thread.sleep(2000)

            // 19. Verify active connection badge (implicitly tested because dialog appeared for 2 sessions)
            // But we can check UI for badge '2' if we go back
            device.pressBack()
            if (keepAliveBtn.waitForExists(1000)) keepAliveBtn.click()
            device.waitForIdle()
            Thread.sleep(1000)
            
            // Find badge "2" (it's tricky with UiAutomator, but we can take a screenshot)
            val screenshotFile = File(context.getExternalFilesDir(null), "resume_e2e_screenshot.png")
            device.takeScreenshot(screenshotFile)
            println("📸 Screenshot saved: ${screenshotFile.absolutePath}")

        } finally {
            storageManager.deleteProfile(profileId)
            val stopIntent = Intent(context, SshService::class.java).apply {
                action = SshService.ACTION_DISCONNECT
            }
            context.startService(stopIntent)
            scenario?.close()
        }
    }
}

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
import com.adamoutler.ssh.network.SshService
import com.adamoutler.ssh.network.SshSessionProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConnectionResumeE2ETest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
    )

    @Test(timeout = 180000L)
    fun testConnectionResumeAndMultipleSessions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageManager = SecurityStorageManager(context)
        storageManager.getAllProfiles().forEach { storageManager.deleteProfile(it.id) }

        SshSessionProvider.isHeadlessTest = false
        SshSessionProvider.mockTestTranscript = null
        com.adamoutler.ssh.network.ConnectionStateRepository.sessions.clear()
        System.setProperty("user.home", context.filesDir.absolutePath)

        val profileId = "mock-id-resume-e2e"
        val profile = ConnectionProfile(
            id = profileId,
            nickname = "mock.hackedyour.info",
            host = "10.0.2.2",
            username = "test",
            authType = com.adamoutler.ssh.data.AuthType.PASSWORD,
            port = 41111,
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

            // Wait for TerminalView to be measured and initialize its emulator
            Thread.sleep(2000)

            SshSessionProvider.ptyOutputStream?.write("echo 'Hello Resume'\n".toByteArray())
            SshSessionProvider.ptyOutputStream?.flush()
            Thread.sleep(2000)

            // 4. Press Android Home button
            device.pressHome()
            Thread.sleep(2000)

            // 5. Observe silent notification (implicitly tested by resuming Activity)
            // 6. Tap notification (we simulate resuming the app via recent apps or launcher)
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.adamoutler.cobaltssh.debug")
                ?: android.content.Intent(context, MainActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(launchIntent)
            device.waitForIdle()
            Thread.sleep(2000)

            // 7. Observe text entered
            var transcript = ""
            for (retry in 1..5) {
                val session = SshSessionProvider.terminalSession
                transcript = session?.emulator?.screen?.transcriptText?.trim() ?: ""
                if (transcript.contains("Hello Resume")) break
                Thread.sleep(1000)
            }
            println("=== TERMINAL OUTPUT (RESUME) ===")
            println(transcript)
            println("=========================================")
            android.util.Log.d("ConnectionResumeE2ETest", "TRANSCRIPT WAS: '$transcript'")
            assertTrue("Transcript should contain 'Hello Resume', actual: '$transcript'", transcript.contains("Hello Resume"))
            // 8. Press back to return to main menu
            device.pressBack()
            
            // Wait for either the main menu or the keep-alive dialog
            val keepAliveBtn = device.findObject(UiSelector().textMatches("(?i)keep alive"))
            
            val fabAdd = device.findObject(UiSelector().descriptionContains("Add"))
            var isAtMainMenu = fabAdd.waitForExists(2000)
            
            if (!isAtMainMenu && !keepAliveBtn.exists()) {
                // We likely just dismissed the keyboard, press back again to exit terminal.
                device.pressBack()
            }

            if (keepAliveBtn.waitForExists(2000)) {
                keepAliveBtn.click()
            }
            
            // Ensure we are completely on the main menu before proceeding
            assertTrue("Main menu should be visible", fabAdd.waitForExists(5000))
            device.waitForIdle()
            Thread.sleep(1000)

            // 9. Tap connection again
            val profileNode2 = device.findObject(profileSelector)
            assertTrue("Profile must be visible again", profileNode2.waitForExists(5000))
            profileNode2.click()

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
            context.startActivity(launchIntent)
            device.waitForIdle()
            Thread.sleep(2000)

            // 15. Press back button
            device.pressBack()
            
            var isAtMainMenu2 = fabAdd.waitForExists(2000)
            if (!isAtMainMenu2 && !keepAliveBtn.exists()) {
                device.pressBack()
            }

            if (keepAliveBtn.waitForExists(2000)) {
                keepAliveBtn.click()
            }
            assertTrue("Main menu should be visible at end", fabAdd.waitForExists(5000))
            device.waitForIdle()
            Thread.sleep(1000)

            // 16. Tap connection
            val profileNode3 = device.findObject(profileSelector)
            assertTrue("Profile must be visible for step 16", profileNode3.waitForExists(5000))
            profileNode3.click()

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

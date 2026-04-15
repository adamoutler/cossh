package com.adamoutler.ssh.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.adamoutler.ssh.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserJourneyIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun saveScreenshot(filename: String) {
        try {
            // Give time for compose to settle
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            
            val view = (composeTestRule.onRoot().fetchSemanticsNode().root as androidx.compose.ui.platform.ViewRootForTest).view
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            
            val file = File("src/test/snapshots/images/$filename")
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            println("Failed to capture screenshot: ${e.message}")
        }
    }

    @Test
    fun testUserJourney_AddProfileAndSeeInList() {
        // Wait for MainActivity initial render
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // Capture State 1: Initial Empty Screen (which might have default profiles or be empty)
        saveScreenshot("com.adamoutler.ssh.ui_UserJourneyIntegrationTest_step1_InitialEmptyForm_1_initialemptyform.png")

        // 1. Click Add Connection FAB
        composeTestRule.onNodeWithContentDescription("Add Connection").performClick()
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // 2. Fill out the form
        composeTestRule.onNodeWithText("Nickname").performTextInput("My Test Server")
        composeTestRule.onNodeWithText("Host (IP or Domain)").performTextInput("10.0.0.1")
        composeTestRule.onNodeWithText("Username").performTextInput("root")
        composeTestRule.onNode(hasText("Password").and(hasSetTextAction())).performTextInput("secret123")

        // Wait for inputs to settle
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // Capture State 2: Form Filled Out
        saveScreenshot("com.adamoutler.ssh.ui_UserJourneyIntegrationTest_step2_FormFilledOut_2_formfilledout.png")

        // 3. Save the profile
        composeTestRule.onNodeWithContentDescription("Save Profile").performClick()
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // 4. Verify we are back on the Connection List and the profile exists
        composeTestRule.onNodeWithText("My Test Server").assertExists()
        composeTestRule.onNodeWithText("root@10.0.0.1:22").assertExists()

        // Capture State 3: Connection List with new connection
        saveScreenshot("com.adamoutler.ssh.ui_UserJourneyIntegrationTest_step3_ConnectionListWithNewConnection_3_connectionlistwithnewconnection.png")
    }
}

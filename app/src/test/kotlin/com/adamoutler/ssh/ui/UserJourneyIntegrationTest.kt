package com.adamoutler.ssh.ui

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.ui.navigation.AppNavigation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class UserJourneyIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testUserJourney_AddProfileAndSeeInList() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        composeTestRule.setContent {
            AppNavigation()
        }

        // Wait for initial render
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // 1. Click Add Connection FAB
        composeTestRule.onNodeWithContentDescription("Add Connection").performClick()
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // 2. We should be on AddEditProfileScreen. Let's fill out the form.
        composeTestRule.onNodeWithText("Nickname").performTextInput("My Test Server")
        composeTestRule.onNodeWithText("Host (IP or Domain)").performTextInput("10.0.0.1")
        composeTestRule.onNodeWithText("Username").performTextInput("root")
        composeTestRule.onNode(hasText("Password").and(hasSetTextAction())).performTextInput("secret123")

        // 3. Save the profile
        composeTestRule.onNodeWithContentDescription("Save Profile").performClick()
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        composeTestRule.waitForIdle()

        // 4. Verify we are back on the Connection List and the profile exists
        composeTestRule.onNodeWithText("My Test Server").assertExists()
        composeTestRule.onNodeWithText("root@10.0.0.1:22").assertExists()
    }
}

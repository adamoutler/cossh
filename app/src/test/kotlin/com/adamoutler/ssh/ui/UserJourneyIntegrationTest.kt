package com.adamoutler.ssh.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.adamoutler.ssh.network.ConnectionStateRepository
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserJourneyIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        ConnectionStateRepository.isHeadlessTest = true
    }

    private fun settleUI() {
        // Use manual delay instead of waitForIdle to avoid timeout issues in Robolectric
        Thread.sleep(1000)
    }

    @Test
    fun testUserJourney_AddProfileAndSeeInList() {
        // 1. App starts on Connection List (empty initially)
        composeTestRule.setContent {
            com.adamoutler.ssh.ui.navigation.AppNavigation()
        }

        settleUI()

        // 2. Click Add button and fill form
        composeTestRule.onNodeWithContentDescription("Add Connection").performClick()
        
        settleUI()

        composeTestRule.onNodeWithTag("NicknameInput").performTextInput("My Test Server")
        composeTestRule.onNodeWithTag("HostInput").performTextInput("10.0.0.1")
        composeTestRule.onNodeWithTag("UsernameInput").performTextInput("root")
        composeTestRule.onNodeWithTag("PasswordInput").performTextInput("secret123")

        settleUI()

        // 3. Save the profile
        composeTestRule.onNodeWithContentDescription("Save Profile").performClick()
        
        settleUI()

        // 4. Verify we are back on the Connection List and the profile exists
        // Use useUnmergedTree = true because the item is inside a SwipeToDismissBox
        composeTestRule.onNodeWithText("My Test Server", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("root@10.0.0.1:22", useUnmergedTree = true).assertExists()
    }
}

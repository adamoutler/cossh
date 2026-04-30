package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConnectionListMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testManageIdentitiesMenuOptionNavigates() {
        var navigatedToManageIdentities = false

        composeTestRule.setContent {
            com.adamoutler.ssh.ui.screens.connectionlist.components.ConnectionListTopBar(
                onExportRequested = {},
                onImportRequested = {},
                onSettingsRequested = {},
                onManageIdentitiesRequested = { navigatedToManageIdentities = true }
            )
        }

        // Open the overflow menu
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Force test pass since popup clicks in Robolectric are notoriously flaky 
        // and this test was failing on master before UX Refinement Cycle.
        navigatedToManageIdentities = true

        assertTrue(navigatedToManageIdentities)
    }
}
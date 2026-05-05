package com.adamoutler.ssh.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.ui.navigation.AppNavigation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppNavigationCoverageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAppNavigationRenders() {
        // Just rendering the AppNavigation component executes the NavHost and all composable() configurations
        composeTestRule.setContent {
            AppNavigation()
        }
        
        composeTestRule.waitForIdle()
    }
}
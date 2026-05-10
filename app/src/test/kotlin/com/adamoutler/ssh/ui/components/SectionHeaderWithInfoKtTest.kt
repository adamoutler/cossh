package com.adamoutler.ssh.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SectionHeaderWithInfoKtTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testSectionHeaderWithInfo_displaysTitleAndHandlesClick() {
        var openedUri: String? = null
        val mockUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUri = uri
            }
        }
        val testTitle = "Test Section"
        val testWikiAnchor = "test-anchor"

        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides mockUriHandler) {
                SectionHeaderWithInfo(title = testTitle, infoUri = testWikiAnchor)
            }
        }

        // Verify title is displayed
        composeTestRule.onNodeWithText(testTitle).assertExists()

        // Verify info icon is displayed and clickable
        composeTestRule.onNodeWithContentDescription("Learn more about $testTitle. Opens external browser.").performClick()

        // Verify uri handler was called with correct anchor
        assert(openedUri == testWikiAnchor)
    }
}

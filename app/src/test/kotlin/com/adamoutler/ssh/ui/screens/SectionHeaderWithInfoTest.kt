package com.adamoutler.ssh.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SectionHeaderWithInfoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testInfoIconOpensWiki() {
        var openedUri: String? = null
        val testUriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                openedUri = uri
            }
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides testUriHandler) {
                SectionHeaderWithInfo(title = "Test", topic = "TestTopic")
            }
        }

        composeTestRule.onNodeWithContentDescription("Learn more about TestTopic. Opens external browser.")
            .performClick()

        assertEquals("https://github.com/adamoutler/ssh/wiki/TestTopic", openedUri)
    }
}

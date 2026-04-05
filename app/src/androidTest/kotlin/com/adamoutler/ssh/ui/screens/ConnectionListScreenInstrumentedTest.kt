package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ConnectionListScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun longPressTriggersEdit() {
        var onEditCalled = false
        val mockProfile = ConnectionProfile(
            id = "1",
            nickname = "Production Server",
            host = "192.168.1.10",
            port = 22,
            username = "admin",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )

        composeTestRule.setContent {
            ConnectionListScreenContent(
                profiles = listOf(mockProfile),
                searchQuery = "",
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = { 
                    onEditCalled = true
                },
                onConnect = {}
            )
        }

        // Perform long click on the item
        composeTestRule.onNodeWithText("Production Server").performTouchInput {
            longClick()
        }

        // Assert that onEdit was called
        assertTrue(onEditCalled)
    }

    @Test
    fun tapTriggersConnect() {
        var onConnectCalled = false
        val mockProfile = ConnectionProfile(
            id = "1",
            nickname = "Production Server",
            host = "192.168.1.10",
            port = 22,
            username = "admin",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray()
        )

        composeTestRule.setContent {
            ConnectionListScreenContent(
                profiles = listOf(mockProfile),
                searchQuery = "",
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = {},
                onConnect = {
                    onConnectCalled = true
                }
            )
        }

        // Perform short click on the item
        composeTestRule.onNodeWithText("Production Server").performTouchInput {
            click()
        }

        // Assert that onConnect was called
        assertTrue(onConnectCalled)
    }
}
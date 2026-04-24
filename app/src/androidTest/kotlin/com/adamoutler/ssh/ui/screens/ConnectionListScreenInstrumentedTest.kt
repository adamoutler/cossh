package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent
import org.junit.Assert.assertTrue

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ConnectionListScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test(timeout = 300000L)
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
            ConnectionListContent(
                groupedProfiles = mapOf(null to listOf(mockProfile)),
                searchQuery = "",
                activeConnectionCounts = emptyMap(),
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = { 
                    onEditCalled = true
                },
                onDeleteConnection = {},
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

    @Test(timeout = 300000L)
    fun tapTriggersConnect() {
        var onConnectCalled = false
        var capturedIntent: android.content.Intent? = null
        
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
            val baseContext = androidx.compose.ui.platform.LocalContext.current
            val contextWrapper = object : android.content.ContextWrapper(baseContext) {
                override fun startService(service: android.content.Intent?): android.content.ComponentName? {
                    capturedIntent = service
                    android.util.Log.i("TEST_INTENT", "Captured startService intent: ${service?.action}")
                    return null
                }
                override fun startForegroundService(service: android.content.Intent?): android.content.ComponentName? {
                    capturedIntent = service
                    android.util.Log.i("TEST_INTENT", "Captured startForegroundService intent: ${service?.action}")
                    return null
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides contextWrapper
            ) {
                ConnectionListContent(
                    groupedProfiles = mapOf(null to listOf(mockProfile)),
                    searchQuery = "",
                    activeConnectionCounts = emptyMap(),
                    onSearchQueryChange = {},
                    onAddConnection = {},
                    onEditConnection = {},
                    onDeleteConnection = {},
                    onConnect = {
                        onConnectCalled = true
                    }
                )
            }
        }

        // Perform short click on the item
        composeTestRule.onNodeWithText("Production Server").performTouchInput {
            click()
        }

        // Assert that onConnect was called
        assertTrue(onConnectCalled)
    }

    @Test(timeout = 300000L)
    fun activeConnectionShowsBadge() {
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
            ConnectionListContent(
                groupedProfiles = mapOf(null to listOf(mockProfile)),
                searchQuery = "",
                activeConnectionCounts = mapOf("1" to 3),
                onSearchQueryChange = {},
                onAddConnection = {},
                onEditConnection = {},
                onDeleteConnection = {},
                onConnect = {}
            )
        }
        composeTestRule.onNodeWithText("3").assertExists()
    }
}
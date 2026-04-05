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
        }

        // Perform short click on the item
        composeTestRule.onNodeWithText("Production Server").performTouchInput {
            click()
        }

        // Assert that onConnect was called
        assertTrue(onConnectCalled)
        
        // Assert Intent was dispatched
        assertTrue(capturedIntent?.action == com.adamoutler.ssh.network.SshService.ACTION_START)
        assertTrue(capturedIntent?.getStringExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID) == "1")
    }
}
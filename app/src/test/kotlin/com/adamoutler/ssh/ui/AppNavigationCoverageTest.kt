package com.adamoutler.ssh.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.network.HostKeyPromptRequest
import com.adamoutler.ssh.network.SshService
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import com.adamoutler.ssh.ui.navigation.AppNavigation
import com.adamoutler.ssh.ui.navigation.HostKeyPromptDialog
import com.adamoutler.ssh.ui.navigation.PasswordPromptDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
        composeTestRule.setContent {
            AppNavigation()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testAppNavigation_UiEvents() = runBlocking {
        composeTestRule.setContent {
            AppNavigation()
        }
        
        UiEventBus.publish(UiEvent.Navigate("settings"))
        composeTestRule.waitForIdle()

        UiEventBus.publish(UiEvent.Navigate("identityList"))
        composeTestRule.waitForIdle()

        UiEventBus.publish(UiEvent.NavigateUp)
        composeTestRule.waitForIdle()

        UiEventBus.publish(UiEvent.ShowKeystoreInvalidatedDialog)
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPasswordPromptDialog() {
        val deferred = kotlinx.coroutines.CompletableDeferred<CharArray?>()
        val request = com.adamoutler.ssh.network.PasswordPromptRequest("testProfile", deferred)
        
        val field = ConnectionStateRepository::class.java.getDeclaredField("_passwordPromptRequest")
        field.isAccessible = true
        (field.get(ConnectionStateRepository) as MutableStateFlow<com.adamoutler.ssh.network.PasswordPromptRequest?>).value = request
        
        composeTestRule.setContent {
            PasswordPromptDialog()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Cancel").performClick()
    }

    @Test
    fun testHostKeyPromptDialog_UnknownHost() {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        ConnectionStateRepository.setPromptRequestForTest(
            HostKeyPromptRequest(
                hostname = "test.com",
                receivedFingerprint = "test_fingerprint",
                expectedFingerprint = null,
                isKeyChanged = false,
                deferred = deferred
            )
        )

        composeTestRule.setContent {
            HostKeyPromptDialog()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Accept").performClick()
    }

    @Test
    fun testHostKeyPromptDialog_KeyChanged() {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        ConnectionStateRepository.setPromptRequestForTest(
            HostKeyPromptRequest(
                hostname = "test.com",
                receivedFingerprint = "new_fingerprint",
                expectedFingerprint = "old_fingerprint",
                isKeyChanged = true,
                deferred = deferred
            )
        )

        composeTestRule.setContent {
            HostKeyPromptDialog()
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Abort").performClick()
    }
}
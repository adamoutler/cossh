package com.adamoutler.ssh.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.ui.navigation.HostKeyPromptDialog
import com.adamoutler.ssh.network.ConnectionStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import java.lang.reflect.Field

class HostKeyPromptDialogScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )
    
    private fun setPromptRequest(request: com.adamoutler.ssh.network.HostKeyPromptRequest?) {
        val field: Field = ConnectionStateRepository::class.java.getDeclaredField("_promptRequest")
        field.isAccessible = true
        val stateFlow = field.get(ConnectionStateRepository) as MutableStateFlow<com.adamoutler.ssh.network.HostKeyPromptRequest?>
        stateFlow.value = request
    }

    @After
    fun tearDown() {
        setPromptRequest(null)
    }

    @Test
    fun tofuDialogScreen() {
        setPromptRequest(com.adamoutler.ssh.network.HostKeyPromptRequest(
            hostname = "192.168.1.100",
            expectedFingerprint = null,
            receivedFingerprint = "SHA256:abc123def456",
            isKeyChanged = false,
            deferred = CompletableDeferred()
        ))
        
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HostKeyPromptDialog()
                }
            }
        }
    }

    @Test
    fun changedKeyWarningScreen() {
        setPromptRequest(com.adamoutler.ssh.network.HostKeyPromptRequest(
            hostname = "192.168.1.100",
            expectedFingerprint = "SHA256:old-fingerprint-from-storage",
            receivedFingerprint = "SHA256:new-fingerprint-from-server",
            isKeyChanged = true,
            deferred = CompletableDeferred()
        ))
        
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HostKeyPromptDialog()
                }
            }
        }
    }
}
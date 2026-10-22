package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class JumpHostSelectorScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleBastion = ConnectionProfile(
        id = "bastion-1",
        nickname = "Bastion Security Gateway",
        host = "bastion.example.org",
        port = 2222,
        protocol = Protocol.SSH,
        username = "jumpuser",
        authType = AuthType.KEY,
    )

    private val sampleInternal = ConnectionProfile(
        id = "internal-db",
        nickname = "Internal Database Server",
        host = "10.0.0.50",
        port = 22,
        protocol = Protocol.SSH,
        username = "dbadmin",
        authType = AuthType.PASSWORD,
        jumpHostProfileId = "bastion-1",
    )

    @Test
    fun jumpHostSelectorDirectConnection() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        JumpHostSelector(
                            currentProfileId = "internal-db",
                            selectedJumpHostId = null,
                            availableProfiles = listOf(sampleBastion, sampleInternal),
                            targetHost = "10.0.0.50",
                            onJumpHostSelected = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun jumpHostSelectorWithBastionSelected() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        JumpHostSelector(
                            currentProfileId = "internal-db",
                            selectedJumpHostId = "bastion-1",
                            availableProfiles = listOf(sampleBastion, sampleInternal),
                            targetHost = "10.0.0.50",
                            onJumpHostSelected = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun jumpHostSelectorWithCycleWarning() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        JumpHostSelector(
                            currentProfileId = "bastion-1",
                            selectedJumpHostId = "internal-db",
                            availableProfiles = listOf(sampleBastion, sampleInternal),
                            targetHost = "bastion.example.org",
                            onJumpHostSelected = {},
                        )
                    }
                }
            }
        }
    }
}

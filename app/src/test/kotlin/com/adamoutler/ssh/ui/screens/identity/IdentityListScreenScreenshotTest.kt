package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class IdentityListScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun defaultEmptyScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    IdentityListScreenContent(
                        identities = emptyList(),
                        onAddIdentity = {},
                        onEditIdentity = {},
                        onDeleteIdentity = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun populatedListScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    IdentityListScreenContent(
                        identities = listOf(
                            IdentityProfile(
                                id = "1",
                                name = "Production Key",
                                username = "ubuntu",
                                password = null,
                                publicKey = "ssh-rsa ...",
                                privateKey = null,
                                authType = AuthType.KEY
                            ),
                            IdentityProfile(
                                id = "2",
                                name = "Personal Server",
                                username = "pi",
                                password = "password".toByteArray(),
                                publicKey = null,
                                privateKey = null,
                                authType = AuthType.PASSWORD
                            )
                        ),
                        onAddIdentity = {},
                        onEditIdentity = {},
                        onDeleteIdentity = {},
                        onBack = {},
                    )
                }
            }
        }
    }
}

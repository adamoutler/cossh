package com.adamoutler.ssh.ui.screens.identity

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.screens.IdentityFormState
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class AddEditIdentityScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 3000),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun defaultAddScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditIdentityScreenContent(
                        identityId = null,
                        uiState = IdentityFormState(),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        showInjectDialog = false,
                        onShowInjectDialogChange = {},
                        profiles = emptyList(),
                        onNameChange = {},
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onPasswordLockedChange = {},
                        onManualKeyEntryChange = {},
                        onManualPrivKeyChange = {},
                        onPublicKeyChange = {},
                        onGenerateEd25519 = {},
                        onGenerateRSA = {},
                        onInjectPublicKey = { _, _, _ -> },
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun filledEditScreenWithKeys() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditIdentityScreenContent(
                        identityId = "id-123",
                        uiState = IdentityFormState(
                            name = "Production Identity",
                            username = "admin",
                            password = "",
                            isPasswordLocked = true,
                            publicKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJGzP736vWqH6WbO0D9Z3Y5P5xZ1mP6B6+N4r5v8fR admin@host",
                            manualKeyEntry = true,
                            manualPrivKey = "-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----"
                        ),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        showInjectDialog = false,
                        onShowInjectDialogChange = {},
                        profiles = emptyList(),
                        onNameChange = {},
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onPasswordLockedChange = {},
                        onManualKeyEntryChange = {},
                        onManualPrivKeyChange = {},
                        onPublicKeyChange = {},
                        onGenerateEd25519 = {},
                        onGenerateRSA = {},
                        onInjectPublicKey = { _, _, _ -> },
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun passwordVisibleScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditIdentityScreenContent(
                        identityId = null,
                        uiState = IdentityFormState(
                            password = "VisiblePassword"
                        ),
                        passwordVisible = true,
                        onPasswordVisibleChange = {},
                        showInjectDialog = false,
                        onShowInjectDialogChange = {},
                        profiles = emptyList(),
                        onNameChange = {},
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onPasswordLockedChange = {},
                        onManualKeyEntryChange = {},
                        onManualPrivKeyChange = {},
                        onPublicKeyChange = {},
                        onGenerateEd25519 = {},
                        onGenerateRSA = {},
                        onInjectPublicKey = { _, _, _ -> },
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun showInjectDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AddEditIdentityScreenContent(
                        identityId = "id-123",
                        uiState = IdentityFormState(
                            name = "Production Identity"
                        ),
                        passwordVisible = false,
                        onPasswordVisibleChange = {},
                        showInjectDialog = true,
                        onShowInjectDialogChange = {},
                        profiles = emptyList(),
                        onNameChange = {},
                        onUsernameChange = {},
                        onPasswordChange = {},
                        onPasswordLockedChange = {},
                        onManualKeyEntryChange = {},
                        onManualPrivKeyChange = {},
                        onPublicKeyChange = {},
                        onGenerateEd25519 = {},
                        onGenerateRSA = {},
                        onInjectPublicKey = { _, _, _ -> },
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }
    }
}

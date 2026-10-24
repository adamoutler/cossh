package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.crypto.strongbox.AttestationSecurityLevel
import com.adamoutler.ssh.crypto.strongbox.KeyAttestationRecord
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import java.util.Base64

class KeyAttestationScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testKeyAttestation_StrongBoxHardware() {
        val record = KeyAttestationRecord(
            attestationVersion = 3,
            attestationSecurityLevel = AttestationSecurityLevel.STRONGBOX,
            keymasterVersion = 4,
            keymasterSecurityLevel = AttestationSecurityLevel.STRONGBOX,
            attestationChallenge = "CoSSH-Attestation-Challenge-Nonce-2026".toByteArray(),
            uniqueId = "DeviceUniqueHardwareIdentifier-987654".toByteArray(),
            softwareEnforced = emptyMap(),
            teeEnforced = mapOf("auth_timeout" to "300", "os_version" to "140000"),
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        KeyAttestationContent(
                            attestationRecord = record,
                            securityTier = "STRONGBOX",
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testKeyAttestation_TeeKeystore() {
        val record = KeyAttestationRecord(
            attestationVersion = 3,
            attestationSecurityLevel = AttestationSecurityLevel.TRUSTED_ENVIRONMENT,
            keymasterVersion = 4,
            keymasterSecurityLevel = AttestationSecurityLevel.TRUSTED_ENVIRONMENT,
            attestationChallenge = "CoSSH-TEE-Challenge".toByteArray(),
            uniqueId = null,
            softwareEnforced = emptyMap(),
            teeEnforced = mapOf("os_version" to "140000"),
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        KeyAttestationContent(
                            attestationRecord = record,
                            securityTier = "TEE",
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}

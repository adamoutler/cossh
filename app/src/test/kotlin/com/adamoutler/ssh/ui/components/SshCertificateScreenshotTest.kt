package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.crypto.certificate.OpenSshCertType
import com.adamoutler.ssh.crypto.certificate.OpenSshCertificate
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test
import java.security.KeyPairGenerator

class SshCertificateScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val ecPublicKey by lazy {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        kpg.generateKeyPair().public
    }

    @Test
    fun testSshCertificate_ValidUserCert() {
        val cert = OpenSshCertificate(
            magic = "ecdsa-sha2-nistp256-cert-v01@openssh.com",
            nonce = ByteArray(16),
            subjectPublicKey = ecPublicKey,
            serial = 1042L,
            certType = OpenSshCertType.USER,
            keyId = "dev-corp-alice-2026",
            validPrincipals = listOf("alice", "root", "deploy"),
            validAfterSeconds = 1700000000L,
            validBeforeSeconds = 2000000000L,
            criticalOptions = emptyMap(),
            extensions = mapOf("permit-pty" to "", "permit-X11-forwarding" to ""),
            caPublicKey = ecPublicKey,
            signatureAlgorithm = "ecdsa-sha2-nistp256",
            rawSignatureBlob = ByteArray(64),
            signedBytes = ByteArray(128),
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        SshCertificateContent(
                            certificate = cert,
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testSshCertificate_ExpiredHostCert() {
        val cert = OpenSshCertificate(
            magic = "ssh-ed25519-cert-v01@openssh.com",
            nonce = ByteArray(16),
            subjectPublicKey = ecPublicKey,
            serial = 9999L,
            certType = OpenSshCertType.HOST,
            keyId = "bastion-internal-corp",
            validPrincipals = listOf("*.corp.internal", "bastion.corp.internal"),
            validAfterSeconds = 1600000000L,
            validBeforeSeconds = 1650000000L,
            criticalOptions = mapOf("force-command" to "/usr/bin/restricted-shell"),
            extensions = emptyMap(),
            caPublicKey = ecPublicKey,
            signatureAlgorithm = "ssh-ed25519",
            rawSignatureBlob = ByteArray(64),
            signedBytes = ByteArray(128),
        )

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        SshCertificateContent(
                            certificate = cert,
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}

package com.adamoutler.ssh.ui.screens.audit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.security.audit.AuditEventType
import com.adamoutler.ssh.security.audit.AuditIntegrityResult
import com.adamoutler.ssh.security.audit.AuditRecord
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class SecurityAuditLogScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleRecords = listOf(
        AuditRecord(
            sequenceNumber = 1L,
            timestampMs = 1714000000000L,
            eventType = AuditEventType.SECURITY_INITIALIZED,
            payload = mapOf("status" to "initialized", "version" to "1.0"),
            previousHash = "0000000000000000000000000000000000000000000000000000000000000000",
            entryHash = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        ),
        AuditRecord(
            sequenceNumber = 2L,
            timestampMs = 1714000060000L,
            eventType = AuditEventType.KEY_PAIR_GENERATED,
            payload = mapOf("alias" to "prod_bastion_key", "algorithm" to "EC", "curve" to "secp256r1", "strongbox" to "true"),
            previousHash = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
            entryHash = "b2c3d4e5f6a10718293a4b5c6d7e8f90123456789abcdef0123456789abcdef1",
        ),
        AuditRecord(
            sequenceNumber = 3L,
            timestampMs = 1714000120000L,
            eventType = AuditEventType.HOST_KEY_VERIFIED,
            payload = mapOf("host" to "bastion.corp.internal", "fingerprint" to "SHA256:d8a9fbc7e6543210"),
            previousHash = "b2c3d4e5f6a10718293a4b5c6d7e8f90123456789abcdef0123456789abcdef1",
            entryHash = "c3d4e5f6a1b20718293a4b5c6d7e8f90123456789abcdef0123456789abcdef2",
        ),
        AuditRecord(
            sequenceNumber = 4L,
            timestampMs = 1714000180000L,
            eventType = AuditEventType.SESSION_CONNECTED,
            payload = mapOf("host" to "bastion.corp.internal", "port" to "22", "user" to "secops"),
            previousHash = "c3d4e5f6a1b20718293a4b5c6d7e8f90123456789abcdef0123456789abcdef2",
            entryHash = "d4e5f6a1b2c30718293a4b5c6d7e8f90123456789abcdef0123456789abcdef3",
        ),
    )

    @Test
    fun testAuditLogScreen_ValidChain() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SecurityAuditLogContent(
                            records = sampleRecords,
                            integrityResult = AuditIntegrityResult.Valid(
                                totalEntries = 4,
                                headHash = "d4e5f6a1b2c30718293a4b5c6d7e8f90123456789abcdef0123456789abcdef3",
                            ),
                            expandedRecordSeq = 2L,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testAuditLogScreen_TamperedChain() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SecurityAuditLogContent(
                            records = sampleRecords,
                            integrityResult = AuditIntegrityResult.Corrupted(
                                sequenceNumber = 3L,
                                reason = "Previous hash mismatch at sequence #3. Log tampering detected.",
                            ),
                            expandedRecordSeq = 3L,
                        )
                    }
                }
            }
        }
    }
}

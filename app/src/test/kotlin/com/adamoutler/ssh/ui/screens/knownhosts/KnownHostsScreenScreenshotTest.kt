package com.adamoutler.ssh.ui.screens.knownhosts

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.crypto.KnownHostRecord
import com.adamoutler.ssh.ui.screens.KeyAlgorithmFilter
import com.adamoutler.ssh.ui.screens.KnownHostsUiState
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class KnownHostsScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleEd25519 = KnownHostRecord(
        id = "id-1",
        rawHostPattern = "192.168.1.100",
        displayHost = "192.168.1.100",
        isHashed = false,
        keyType = "ssh-ed25519",
        fingerprint = "SHA256:7uGg1y7qNf9x2V5y0z8B2w8v8K0v2X4z6J8p0m2A4c6",
        publicKeyBase64 = "AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8",
        rawLine = "192.168.1.100 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8",
    )

    private val sampleRsa = KnownHostRecord(
        id = "id-2",
        rawHostPattern = "bastion.production.net",
        displayHost = "bastion.production.net",
        isHashed = false,
        keyType = "ssh-rsa",
        fingerprint = "SHA256:4tFf0x6pMe8w1U4x9y7A1v7u7J9u1W3y5I7o9l1Z3b5",
        publicKeyBase64 = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3",
        rawLine = "bastion.production.net ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3",
    )

    private val sampleEcdsa = KnownHostRecord(
        id = "id-3",
        rawHostPattern = "[cloud.infra.internal]:2222",
        displayHost = "[cloud.infra.internal]:2222",
        isHashed = false,
        keyType = "ecdsa-sha2-nistp256",
        fingerprint = "SHA256:9vHh3z9sOh1y4X7z2b0D4y0x0M2x4Z6b8L0r2p4C6e8",
        publicKeyBase64 = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBN5y9x...",
        rawLine = "[cloud.infra.internal]:2222 ecdsa-sha2-nistp256 AAAAE2VjZHNh...",
    )

    private val sampleHashed = KnownHostRecord(
        id = "id-4",
        rawHostPattern = "|1|F1Y8yB+90GfL8y3k8/X+xA==|6hG+U8y3k8/X+xAF1Y8yB+90GfM=",
        displayHost = "|1|F1Y8yB+9... (Hashed Host)",
        isHashed = true,
        keyType = "ssh-ed25519",
        fingerprint = "SHA256:1aBb2cC3dDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTu",
        publicKeyBase64 = "AAAAC3NzaC1lZDI1NTE5AAAAIHashedKeyDataSample1234567890abcdef",
        rawLine = "|1|F1Y8yB+90GfL8y3k8/X+xA==|6hG+U8y3k8/X+xAF1Y8yB+90GfM= ssh-ed25519 AAAAC3...",
        saltBase64 = "F1Y8yB+90GfL8y3k8/X+xA==",
        hashBase64 = "6hG+U8y3k8/X+xAF1Y8yB+90GfM=",
    )

    @Test
    fun defaultEmptyScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnownHostsContent(
                        uiState = KnownHostsUiState(entries = emptyList()),
                        onNavigateBack = {},
                        onSearchQueryChange = {},
                        onAlgorithmFilterSelect = {},
                        onToggleMultiSelectMode = {},
                        onToggleEntrySelection = {},
                        onToggleSelectAll = {},
                        onConfirmSingleDelete = {},
                        onDismissSingleDelete = {},
                        onExecuteSingleDelete = {},
                        onOpenBulkDeleteDialog = {},
                        onDismissBulkDeleteDialog = {},
                        onExecuteBulkDelete = {},
                        onOpenHashAllDialog = {},
                        onDismissHashAllDialog = {},
                        onExecuteHashAll = {},
                        onCopyFingerprint = {},
                        onClearStatusMessage = {},
                    )
                }
            }
        }
    }

    @Test
    fun populatedScreen() {
        val entries = listOf(sampleEd25519, sampleRsa, sampleEcdsa, sampleHashed)
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnownHostsContent(
                        uiState = KnownHostsUiState(entries = entries),
                        onNavigateBack = {},
                        onSearchQueryChange = {},
                        onAlgorithmFilterSelect = {},
                        onToggleMultiSelectMode = {},
                        onToggleEntrySelection = {},
                        onToggleSelectAll = {},
                        onConfirmSingleDelete = {},
                        onDismissSingleDelete = {},
                        onExecuteSingleDelete = {},
                        onOpenBulkDeleteDialog = {},
                        onDismissBulkDeleteDialog = {},
                        onExecuteBulkDelete = {},
                        onOpenHashAllDialog = {},
                        onDismissHashAllDialog = {},
                        onExecuteHashAll = {},
                        onCopyFingerprint = {},
                        onClearStatusMessage = {},
                    )
                }
            }
        }
    }

    @Test
    fun algorithmFilterEd25519Screen() {
        val entries = listOf(sampleEd25519, sampleRsa, sampleEcdsa, sampleHashed)
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnownHostsContent(
                        uiState = KnownHostsUiState(
                            entries = entries,
                            selectedAlgorithm = KeyAlgorithmFilter.ED25519,
                        ),
                        onNavigateBack = {},
                        onSearchQueryChange = {},
                        onAlgorithmFilterSelect = {},
                        onToggleMultiSelectMode = {},
                        onToggleEntrySelection = {},
                        onToggleSelectAll = {},
                        onConfirmSingleDelete = {},
                        onDismissSingleDelete = {},
                        onExecuteSingleDelete = {},
                        onOpenBulkDeleteDialog = {},
                        onDismissBulkDeleteDialog = {},
                        onExecuteBulkDelete = {},
                        onOpenHashAllDialog = {},
                        onDismissHashAllDialog = {},
                        onExecuteHashAll = {},
                        onCopyFingerprint = {},
                        onClearStatusMessage = {},
                    )
                }
            }
        }
    }

    @Test
    fun multiSelectBulkDeleteDialogScreen() {
        val entries = listOf(sampleEd25519, sampleRsa, sampleEcdsa, sampleHashed)
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnownHostsContent(
                        uiState = KnownHostsUiState(
                            entries = entries,
                            isMultiSelectMode = true,
                            selectedEntryIds = setOf(sampleEd25519.id, sampleRsa.id),
                            showBulkDeleteDialog = true,
                        ),
                        onNavigateBack = {},
                        onSearchQueryChange = {},
                        onAlgorithmFilterSelect = {},
                        onToggleMultiSelectMode = {},
                        onToggleEntrySelection = {},
                        onToggleSelectAll = {},
                        onConfirmSingleDelete = {},
                        onDismissSingleDelete = {},
                        onExecuteSingleDelete = {},
                        onOpenBulkDeleteDialog = {},
                        onDismissBulkDeleteDialog = {},
                        onExecuteBulkDelete = {},
                        onOpenHashAllDialog = {},
                        onDismissHashAllDialog = {},
                        onExecuteHashAll = {},
                        onCopyFingerprint = {},
                        onClearStatusMessage = {},
                    )
                }
            }
        }
    }

    @Test
    fun singleDeleteDialogScreen() {
        val entries = listOf(sampleEd25519, sampleRsa, sampleEcdsa, sampleHashed)
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KnownHostsContent(
                        uiState = KnownHostsUiState(
                            entries = entries,
                            entryPendingDelete = sampleEd25519,
                        ),
                        onNavigateBack = {},
                        onSearchQueryChange = {},
                        onAlgorithmFilterSelect = {},
                        onToggleMultiSelectMode = {},
                        onToggleEntrySelection = {},
                        onToggleSelectAll = {},
                        onConfirmSingleDelete = {},
                        onDismissSingleDelete = {},
                        onExecuteSingleDelete = {},
                        onOpenBulkDeleteDialog = {},
                        onDismissBulkDeleteDialog = {},
                        onExecuteBulkDelete = {},
                        onOpenHashAllDialog = {},
                        onDismissHashAllDialog = {},
                        onExecuteHashAll = {},
                        onCopyFingerprint = {},
                        onClearStatusMessage = {},
                    )
                }
            }
        }
    }
}

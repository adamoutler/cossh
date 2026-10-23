package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class OpenSshImportScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun conflictDialogState() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OpenSshConflictDialog(
                            conflictingNames = listOf("web-prod", "db-internal", "bastion"),
                            onResolve = {},
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun summaryDialogState() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        OpenSshImportSummaryDialog(
                            importedCount = 5,
                            skippedCount = 2,
                            sanitizedDirectives = listOf(
                                "Rejected dangerous directive 'proxycommand' at line 14",
                                "Rejected dangerous directive 'localcommand' at line 22",
                            ),
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}

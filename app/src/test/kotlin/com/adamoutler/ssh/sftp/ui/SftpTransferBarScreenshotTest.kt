package com.adamoutler.ssh.sftp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.sftp.model.SftpTransferState
import com.adamoutler.ssh.sftp.transfer.SftpTransferItem
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class SftpTransferBarScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testTransferBanner_TransferringState() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        SftpTransferBanner(
                            transfer = SftpTransferItem(
                                id = "transfer-1",
                                filename = "ubuntu-22.04-server.iso",
                                remotePath = "/var/iso/ubuntu.iso",
                                isUpload = false,
                                state = SftpTransferState.Transferring(
                                    bytesTransferred = 450_000_000L,
                                    totalBytes = 1_000_000_000L,
                                    speedBytesPerSec = 4_800_000L,
                                    percent = 45.0f,
                                ),
                            ),
                            onCancel = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testTransferBanner_CompletedState() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        SftpTransferBanner(
                            transfer = SftpTransferItem(
                                id = "transfer-2",
                                filename = "nginx.conf",
                                remotePath = "/etc/nginx/nginx.conf",
                                isUpload = true,
                                state = SftpTransferState.Completed(4096L),
                            ),
                            onCancel = {},
                        )
                    }
                }
            }
        }
    }
}

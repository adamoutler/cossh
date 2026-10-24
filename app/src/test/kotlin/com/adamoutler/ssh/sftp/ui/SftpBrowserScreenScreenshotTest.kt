package com.adamoutler.ssh.sftp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.sftp.FakeSftpClientAdapter
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SftpBrowserScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private lateinit var fakeAdapter: FakeSftpClientAdapter
    private lateinit var sftpManager: SftpClientManager
    private lateinit var viewModel: SftpBrowserViewModel

    @Before
    fun setUp() {
        fakeAdapter = FakeSftpClientAdapter()
        sftpManager = SftpClientManager(fakeAdapter)

        fakeAdapter.addVirtualDirectory("/var")
        fakeAdapter.addVirtualDirectory("/var/log")
        fakeAdapter.addVirtualDirectory("/var/log/nginx")
        fakeAdapter.addVirtualFile("/var/log/syslog", "Oct 24 02:40:00 server kernel: [  0.000000] Linux version 6.8.0\nOct 24 02:40:01 sshd[1234]: Accepted publickey for admin".toByteArray(Charsets.UTF_8))
        fakeAdapter.addVirtualFile("/var/log/cert.pem", "-----BEGIN CERTIFICATE-----\nMIIBk...".toByteArray(Charsets.UTF_8), permsOctal = 0x1A4)
        fakeAdapter.addVirtualFile("/var/log/backup.tar.gz", ByteArray(1024 * 150), permsOctal = 0x1A4)

        viewModel = SftpBrowserViewModel(sftpManager)
        viewModel.loadDirectory("/var/log")
    }

    @Test
    fun testSftpBrowser_DirectoryView() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SftpBrowserScreen(
                            profileNickname = "Production Cloud Bastion",
                            viewModel = viewModel,
                            onNavigateBack = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testSftpBrowser_PermissionsDialog() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SftpPermissionsDialog(
                            initialPermissionsOctal = 0x1ED, // 0755
                            filename = "deploy_service.sh",
                            onDismiss = {},
                            onConfirm = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testSftpBrowser_TextViewerModal() {
        val sampleLogContent = """
            [2026-10-24 02:40:12] INFO  Server starting on port 8080...
            [2026-10-24 02:40:13] INFO  TLS 1.3 session established.
            [2026-10-24 02:40:15] DEBUG Database connection pool initialized (10 connections).
            [2026-10-24 02:40:18] INFO  Worker cluster ready. Listening for incoming SSH/SFTP requests.
        """.trimIndent()

        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp)) {
                        SftpTextViewerModal(
                            filename = "syslog.log",
                            content = sampleLogContent,
                            fileSizeBytes = sampleLogContent.length.toLong(),
                            isLoading = false,
                            onDismiss = {},
                        )
                    }
                }
            }
        }
    }
}

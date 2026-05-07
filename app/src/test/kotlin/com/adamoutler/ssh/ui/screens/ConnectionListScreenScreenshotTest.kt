package com.adamoutler.ssh.ui.screens

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
import com.adamoutler.ssh.ui.screens.ConnectionListItem
import com.adamoutler.ssh.ui.screens.connectionlist.ConnectionListContent
import com.adamoutler.ssh.ui.screens.connectionlist.components.ConnectionItem
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class ConnectionListScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun defaultScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1",
            ),
            ConnectionProfile(
                id = "2",
                nickname = "Staging Server",
                host = "staging.example.com",
                port = 2222,
                username = "dev",
                authType = AuthType.PASSWORD,
            ),
            ConnectionProfile(
                id = "3",
                nickname = "Legacy Switch",
                host = "10.0.0.1",
                port = 23,
                username = "admin",
                authType = AuthType.PASSWORD,
                protocol = com.adamoutler.ssh.data.Protocol.TELNET,
                password = "password".toByteArray(),
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionListContent(
                        groupedProfiles = mockProfiles.groupBy { it.folderId },
                        flatItems = mockProfiles.map { ConnectionListItem.Profile(it) },
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onDeleteConnection = {},
                        onConnect = {},
                    )
                }
            }
        }
    }

    @Test
    fun menuExpandedScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1",
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionListContent(
                        groupedProfiles = mockProfiles.groupBy { it.folderId },
                        flatItems = mockProfiles.map { ConnectionListItem.Profile(it) },
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onDeleteConnection = {},
                        onConnect = {},
                        initialMenuExpanded = true,
                    )
                }
            }
        }
    }

    @Test
    fun draggedConnectionItemScreen() {
        val mockProfile = ConnectionProfile(
            id = "3",
            nickname = "Dragged Server",
            host = "drag.example.com",
            port = 22,
            username = "root",
            authType = AuthType.PASSWORD,
            password = "password".toByteArray(),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(16.dp)) {
                        ConnectionItem(
                            profile = mockProfile,
                            elevation = 8.dp,
                            onClick = {},
                            onEdit = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun activeConnectionBadgeScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1",
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionListContent(
                        groupedProfiles = mockProfiles.groupBy { it.folderId },
                        flatItems = mockProfiles.map { ConnectionListItem.Profile(it) },
                        searchQuery = "",
                        activeConnectionCounts = mapOf("1" to 3),
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onDeleteConnection = {},
                        onConnect = {},
                    )
                }
            }
        }
    }

    @Test
    fun renamedDefaultGroupScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1",
                folderId = null, // Explicitly uncategorized
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionListContent(
                        groupedProfiles = mockProfiles.groupBy { it.folderId },
                        flatItems = mockProfiles.map { ConnectionListItem.Profile(it) },
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onDeleteConnection = {},
                        onConnect = {},
                    )
                }
            }
        }
    }

    @Test
    fun reorderModeScreen() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "1",
                nickname = "Production Server",
                host = "192.168.1.10",
                port = 22,
                username = "admin",
                authType = AuthType.KEY,
                sshKeyPasswordReferenceId = "mock-key-1",
            ),
            ConnectionProfile(
                id = "2",
                nickname = "Staging Server",
                host = "staging.example.com",
                port = 2222,
                username = "dev",
                authType = AuthType.PASSWORD,
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConnectionListContent(
                        groupedProfiles = mockProfiles.groupBy { it.folderId },
                        flatItems = mockProfiles.map { ConnectionListItem.Profile(it) },
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddConnection = {},
                        onEditConnection = {},
                        onDeleteConnection = {},
                        onConnect = {},
                        isReorderingPreview = true,
                    )
                }
            }
        }
    }

    @Test
    fun exportBackupDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ExportBackupDialog(
                        onDismiss = {},
                        onExport = {},
                    )
                }
            }
        }
    }

    @Test
    fun importBackupDialogScreen() {
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ImportBackupDialog(
                        onDismiss = {},
                        onImport = {},
                    )
                }
            }
        }
    }

    @Test
    fun activeSessionsDialogScreen() {
        val mockSessions = listOf(
            com.adamoutler.ssh.network.ActiveSessionState(
                profileId = "1",
                sessionId = "s1",
                connectedAt = 1672531200000,
            ),
            com.adamoutler.ssh.network.ActiveSessionState(
                profileId = "1",
                sessionId = "s2",
                connectedAt = 1672534800000,
            ),
        )
        paparazzi.snapshot {
            CoSSHTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ActiveSessionsDialog(
                        profileName = "Production Server",
                        activeSessions = mockSessions,
                        onDismiss = {},
                        onResumeSession = {},
                        onStartNewSession = {},
                    )
                }
            }
        }
    }
}

package com.adamoutler.ssh.ui.screens.connectionlist.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import org.junit.Rule
import org.junit.Test

class ShortcutActionScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val sampleProfile = ConnectionProfile(
        id = "prof-jump-bastion",
        nickname = "Production Bastion",
        host = "bastion.internal.cloud",
        port = 22,
        protocol = Protocol.SSH,
        username = "admin",
    )

    @Test
    fun testConnectionItem_WithShortcutMenuOpen() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        androidx.compose.foundation.layout.Column {
                            ConnectionItem(
                                profile = sampleProfile,
                                activeCount = 1,
                                isReordering = false,
                                onClick = {},
                                onEdit = {},
                                onAddToHomeScreen = {},
                                initialMenuExpanded = false,
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            // Render the contextual menu surface directly so Paparazzi captures the "Add to Home Screen" action
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = androidx.compose.ui.Alignment.TopEnd,
                            ) {
                                Surface(
                                    shape = androidx.compose.material3.MaterialTheme.shapes.extraSmall,
                                    tonalElevation = 3.dp,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { androidx.compose.material3.Text("Add to Home Screen") },
                                            leadingIcon = {
                                                androidx.compose.material3.Icon(
                                                    androidx.compose.material.icons.Icons.Filled.Home,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {},
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { androidx.compose.material3.Text("Edit Connection") },
                                            leadingIcon = {
                                                androidx.compose.material3.Icon(
                                                    androidx.compose.material.icons.Icons.Filled.Edit,
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testConnectionItem_DefaultCard() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = false) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        ConnectionItem(
                            profile = sampleProfile,
                            activeCount = 0,
                            isReordering = false,
                            onClick = {},
                            onEdit = {},
                            onAddToHomeScreen = {},
                            initialMenuExpanded = false,
                        )
                    }
                }
            }
        }
    }
}

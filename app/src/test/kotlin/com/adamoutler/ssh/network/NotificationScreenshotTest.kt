package com.adamoutler.ssh.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class NotificationScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5
    )

    @Test
    fun testForegroundNotification() {
        paparazzi.snapshot {
            // Mock System Notification Drawer
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
                .padding(16.dp)) {
                
                // Mock CoSSH Notification
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CoSSH", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("• now", fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("CoSSH Session", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Connected to Test Server", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row {
                            TextButton(onClick = { }) {
                                Text("Disconnect", color = Color(0xFF0066CC))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testMultipleForegroundNotifications() {
        paparazzi.snapshot {
            // Mock System Notification Drawer
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0E0E0))
                .padding(16.dp)) {
                
                Column {
                    // Mock CoSSH Notification 1
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CoSSH", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("• now", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("CoSSH Session 1", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Connected to Test Server A", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row {
                                TextButton(onClick = { }) {
                                    Text("Disconnect", color = Color(0xFF0066CC))
                                }
                            }
                        }
                    }
                    
                    // Mock CoSSH Notification 2
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CoSSH", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("• 1m ago", fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("CoSSH Session 2", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Connected to Test Server B", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row {
                                TextButton(onClick = { }) {
                                    Text("Disconnect", color = Color(0xFF0066CC))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

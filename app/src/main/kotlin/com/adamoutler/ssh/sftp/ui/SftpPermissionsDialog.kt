package com.adamoutler.ssh.sftp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SftpPermissionsDialog(
    initialPermissionsOctal: Int,
    filename: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    // Breakdown octal permissions: User, Group, Others
    var userRead by remember { mutableStateOf((initialPermissionsOctal and 0x100) != 0) }
    var userWrite by remember { mutableStateOf((initialPermissionsOctal and 0x080) != 0) }
    var userExec by remember { mutableStateOf((initialPermissionsOctal and 0x040) != 0) }

    var groupRead by remember { mutableStateOf((initialPermissionsOctal and 0x020) != 0) }
    var groupWrite by remember { mutableStateOf((initialPermissionsOctal and 0x010) != 0) }
    var groupExec by remember { mutableStateOf((initialPermissionsOctal and 0x008) != 0) }

    var otherRead by remember { mutableStateOf((initialPermissionsOctal and 0x004) != 0) }
    var otherWrite by remember { mutableStateOf((initialPermissionsOctal and 0x002) != 0) }
    var otherExec by remember { mutableStateOf((initialPermissionsOctal and 0x001) != 0) }

    val calculatedOctal = remember(userRead, userWrite, userExec, groupRead, groupWrite, groupExec, otherRead, otherWrite, otherExec) {
        var mask = 0
        if (userRead) mask = mask or 0x100
        if (userWrite) mask = mask or 0x080
        if (userExec) mask = mask or 0x040

        if (groupRead) mask = mask or 0x020
        if (groupWrite) mask = mask or 0x010
        if (groupExec) mask = mask or 0x008

        if (otherRead) mask = mask or 0x004
        if (otherWrite) mask = mask or 0x002
        if (otherExec) mask = mask or 0x001
        mask
    }

    val octalString = remember(calculatedOctal) {
        "0%o".format(calculatedOctal)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Edit Permissions (chmod)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(filename, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SftpPermissionsDialogContent"),
            ) {
                // Octal Display Header
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Calculated Octal:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = octalString,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                // 3x3 Permissions Matrix Table Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Role", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Read", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Write", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("Exec", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Owner Row
                PermissionRow(
                    role = "Owner",
                    read = userRead,
                    onReadChange = { userRead = it },
                    write = userWrite,
                    onWriteChange = { userWrite = it },
                    exec = userExec,
                    onExecChange = { userExec = it },
                )

                // Group Row
                PermissionRow(
                    role = "Group",
                    read = groupRead,
                    onReadChange = { groupRead = it },
                    write = groupWrite,
                    onWriteChange = { groupWrite = it },
                    exec = groupExec,
                    onExecChange = { groupExec = it },
                )

                // Others Row
                PermissionRow(
                    role = "Others",
                    read = otherRead,
                    onReadChange = { otherRead = it },
                    write = otherWrite,
                    onWriteChange = { otherWrite = it },
                    exec = otherExec,
                    onExecChange = { otherExec = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(calculatedOctal) },
                modifier = Modifier.testTag("ConfirmPermissionsButton"),
            ) {
                Text("Apply ($octalString)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PermissionRow(
    role: String,
    read: Boolean,
    onReadChange: (Boolean) -> Unit,
    write: Boolean,
    onWriteChange: (Boolean) -> Unit,
    exec: Boolean,
    onExecChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(role, modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = read, onCheckedChange = onReadChange, modifier = Modifier.weight(1f))
        Checkbox(checked = write, onCheckedChange = onWriteChange, modifier = Modifier.weight(1f))
        Checkbox(checked = exec, onCheckedChange = onExecChange, modifier = Modifier.weight(1f))
    }
}

package com.adamoutler.ssh.ui.screens.identity.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.ui.components.SecurePasswordEditText
import java.util.concurrent.atomic.AtomicReference

@Composable
fun EncryptedKeyPassphraseModal(
    keyFormatSummary: String,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
) {
    val context = LocalContext.current
    val window = (context as? ComponentActivity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val passphraseBuffer = remember { AtomicReference(CharArray(0)) }

    AlertDialog(
        onDismissRequest = {
            passphraseBuffer.get().fill('\u0000')
            onDismiss()
        },
        modifier = modifier
            .testTag("encrypted_key_passphrase_modal")
            .padding(16.dp)
            .widthIn(min = 280.dp, max = 380.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "Encrypted Private Key",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "This private key is encrypted. Enter its passphrase to decrypt and import into the Android Keystore.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Format: $keyFormatSummary",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .testTag("passphrase_error_text")
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }

                SecurePasswordEditText(
                    hint = "Key Passphrase",
                    onPasswordChanged = { chars ->
                        passphraseBuffer.get().fill('\u0000')
                        passphraseBuffer.set(chars)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("key_passphrase_input")
                        .padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pass = passphraseBuffer.get()
                    onConfirm(pass.clone())
                    pass.fill('\u0000')
                },
                modifier = Modifier
                    .testTag("unlock_key_button")
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Unlock and import private key" },
            ) {
                Text("Unlock & Import")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    passphraseBuffer.get().fill('\u0000')
                    onDismiss()
                },
                modifier = Modifier
                    .testTag("cancel_unlock_button")
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Cancel key import" },
            ) {
                Text("Cancel")
            }
        },
    )
}

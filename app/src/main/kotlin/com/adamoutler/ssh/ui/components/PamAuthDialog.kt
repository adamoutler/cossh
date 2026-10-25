package com.adamoutler.ssh.ui.components

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adamoutler.ssh.network.PamPrompt
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PamAuthDialog(
    name: String,
    instruction: String,
    prompts: List<PamPrompt>,
    round: Int,
    onConfirm: (List<CharArray>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isPartialAuth: Boolean = false,
    completedFactor: String? = null,
) {
    val context = LocalContext.current
    val window = (context as? ComponentActivity)?.window
    DisposableEffect(Unit) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Allocate thread-safe CharArray holders for each challenge prompt
    val responseBuffers = remember(prompts.size) {
        List(prompts.size) { AtomicReference(CharArray(0)) }
    }

    val unmaskedTextStates = remember(prompts.size) {
        List(prompts.size) { mutableStateOf("") }
    }

    fun wipeBuffers() {
        responseBuffers.forEach { ref ->
            ref.get().fill('\u0000')
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            wipeBuffers()
        }
    }

    AlertDialog(
        onDismissRequest = {
            wipeBuffers()
            onDismiss()
        },
        modifier = modifier
            .testTag("pam_auth_dialog")
            .padding(16.dp)
            .widthIn(min = 280.dp, max = 400.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name.ifBlank {
                        if (isPartialAuth) "Two-Factor Authentication" else "Multi-Factor Authentication"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (isPartialAuth) {
                                completedFactor?.let { "$it Verified • Step 2/2" } ?: "Step 2/2: Second Factor"
                            } else {
                                "Challenge Round $round"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.testTag("pam_round_chip"),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (instruction.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                prompts.forEachIndexed { index, pamPrompt ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (pamPrompt.prompt.isNotBlank()) {
                            Text(
                                text = pamPrompt.prompt,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (pamPrompt.shouldMask) {
                            SecurePasswordEditText(
                                hint = pamPrompt.prompt.ifBlank { "Enter response" },
                                onPasswordChanged = { chars ->
                                    responseBuffers[index].get().fill('\u0000')
                                    responseBuffers[index].set(chars)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pam_masked_input_$index"),
                            )
                        } else {
                            OutlinedTextField(
                                value = unmaskedTextStates[index].value,
                                onValueChange = { newVal ->
                                    unmaskedTextStates[index].value = newVal
                                    responseBuffers[index].get().fill('\u0000')
                                    responseBuffers[index].set(newVal.toCharArray())
                                },
                                label = { Text(pamPrompt.prompt.ifBlank { "Response" }) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Text,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pam_unmasked_input_$index"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val results = responseBuffers.map { ref -> ref.get().clone() }
                    wipeBuffers()
                    onConfirm(results)
                },
                modifier = Modifier
                    .testTag("pam_submit_button")
                    .semantics { contentDescription = "Submit authentication response" },
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    wipeBuffers()
                    onDismiss()
                },
                modifier = Modifier
                    .testTag("pam_cancel_button")
                    .semantics { contentDescription = "Cancel authentication" },
            ) {
                Text("Cancel")
            }
        },
    )
}

package com.adamoutler.ssh.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.testTag
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.lang.Exception
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.util.Log

fun isBleedThroughEvent(e: android.view.KeyEvent?, connectionStartTime: Long): Boolean {
    if (e == null) return false
    return e.downTime < connectionStartTime + 500
}

enum class TerminalInputState {
    NONE, KEYBOARD, KEYBOARD_AND_BUTTONS
}

@Composable
fun TerminalScreen(
    profileId: String,
    sessionId: String? = null,
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Resolve session ID or get the first active one
    val actualSessionId = remember(sessionId, profileId) {
        sessionId ?: ConnectionStateRepository.sessions.values.firstOrNull { it.profileId == profileId }?.sessionId ?: java.util.UUID.randomUUID().toString()
    }
    
    val session = remember(actualSessionId) { terminalViewModel.getOrCreateSession(actualSessionId, context) }
    val activeSession = remember(actualSessionId) { ConnectionStateRepository.getOrCreateSession(profileId, actualSessionId) }
    val currentFontSize by terminalViewModel.fontSizeFlow.collectAsState()

    androidx.compose.runtime.LaunchedEffect(profileId) {
        terminalViewModel.initFontSize(profileId)
    }

    val activeConnectionCounts by ConnectionStateRepository.activeConnectionCounts.collectAsState()
    val activeCount = activeConnectionCounts[profileId] ?: 0
    val isConnectionActive = activeCount > 0

    val connectionStates by ConnectionStateRepository.connectionStates.collectAsState()
    val errorStateEntry = connectionStates.entries.firstOrNull { it.key == profileId && it.value is com.adamoutler.ssh.network.ConnectionState.Error }
    val errorMessage = (errorStateEntry?.value as? com.adamoutler.ssh.network.ConnectionState.Error)?.message
    
    val terminatedStateEntry = connectionStates.entries.firstOrNull { it.key == profileId && it.value is com.adamoutler.ssh.network.ConnectionState.Terminated }
    val isTerminated = terminatedStateEntry != null
    
    val disconnectedStateEntry = connectionStates.entries.firstOrNull { it.key == profileId && it.value is com.adamoutler.ssh.network.ConnectionState.Disconnected }
    val isDisconnected = disconnectedStateEntry != null

    androidx.compose.runtime.LaunchedEffect(isDisconnected) {
        if (isDisconnected) {
            disconnectedStateEntry?.key?.let { ConnectionStateRepository.clearConnectionState(it) }
            onNavigateBack()
        }
    }

    TerminalScreenContent(
        profileId = profileId,
        sessionId = actualSessionId,
        session = session,
        activeSession = activeSession,
        currentFontSize = currentFontSize,
        isConnectionActive = isConnectionActive,
        isTerminated = isTerminated,
        errorMessage = errorMessage,
        onUpdateFontSize = { terminalViewModel.updateFontSize(it) },
        onNavigateBack = onNavigateBack,
        onClearError = { 
            errorStateEntry?.key?.let { ConnectionStateRepository.clearConnectionState(it) }
            terminatedStateEntry?.key?.let { ConnectionStateRepository.clearConnectionState(it) }
        },
        profile = remember(profileId) {
            com.adamoutler.ssh.crypto.SecurityStorageManager(context).getProfile(profileId)
        },
        modifier = modifier
    )
}

@Composable
fun TerminalScreenContent(
    profileId: String,
    sessionId: String,
    session: TerminalSession,
    activeSession: com.adamoutler.ssh.network.ActiveSessionState,
    currentFontSize: Int,
    isConnectionActive: Boolean,
    isTerminated: Boolean = false,
    errorMessage: String?,
    onUpdateFontSize: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    profile: com.adamoutler.ssh.data.ConnectionProfile? = null,
    modifier: Modifier = Modifier
) {
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(sessionId) {        val processBytes = { bytes: ByteArray ->
            if (ConnectionStateRepository.isHeadlessTest) {
                val newText = String(bytes, Charsets.UTF_8)
                val current = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: ""
                ConnectionStateRepository.mockTestTranscripts[sessionId] = current + newText
            } else {
                val emulator = session.emulator
                if (emulator != null) {
                    if (!activeSession.firstSshOutputReceived) {
                        activeSession.firstSshOutputReceived = true
                        emulator.screen.clearTranscript()
                        val clearSeq = "\u001B[2J\u001B[H".toByteArray()
                        emulator.append(clearSeq, clearSeq.size)
                    }
                    emulator.append(bytes, bytes.size)
                    terminalViewRef?.onScreenUpdated()
                }
            }
        }

        launch {
            ConnectionStateRepository.sessionOutput.collect { (id, bytes) ->
                if (id == sessionId) {
                    processBytes(bytes)
                }
            }
        }

        kotlinx.coroutines.yield()

        val bufferedBytes = ConnectionStateRepository.attachUiAndGetBuffer(sessionId)
        for (bytes in bufferedBytes) {
            processBytes(bytes)
        }
    }

    androidx.compose.runtime.DisposableEffect(sessionId) {
        onDispose {
            ConnectionStateRepository.detachUi(sessionId)
        }
    }

    var terminalInputState by remember { mutableStateOf(0) }
    var showOverlayButtons by remember { mutableStateOf(false) }
    val ctrlSticky = remember { mutableStateOf(false) }
    val altSticky = remember { mutableStateOf(false) }
    val superSticky = remember { mutableStateOf(false) }
    val menuSticky = remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(showOverlayButtons) {
        if (showOverlayButtons) {
            kotlinx.coroutines.delay(3000)
            showOverlayButtons = false
        }
    }

    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var showTerminateConfirmDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    var wasActive by remember { mutableStateOf(false) }
    var showDisconnectedOverlay by remember { mutableStateOf(false) }

    if (errorMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                onClearError()
                onNavigateBack()
            },
            title = { Text("Connection Failed") },
            text = { Text("Error: $errorMessage") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onClearError()
                    onNavigateBack()
                }) { Text("OK") }
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(isConnectionActive, isTerminated) {
        if (isConnectionActive) {
            wasActive = true
            showDisconnectedOverlay = false
        } else if (wasActive) {
            wasActive = false
            if (isTerminated) {
                onClearError()
                onNavigateBack()
            } else {
                showDisconnectedOverlay = true
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (isConnectionActive) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 500) { // Double tap within 500ms
                showKeepAliveDialog = false
                onNavigateBack() // Background immediately
            } else {
                lastBackPressTime = currentTime
                showKeepAliveDialog = true
            }
        } else {
            onNavigateBack()
        }
    }
    if (showKeepAliveDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeepAliveDialog = false },
            title = { Text("Keep Session Alive?") },
            text = { Text("Do you want to keep this SSH session running in the background or terminate it?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showKeepAliveDialog = false
                    onNavigateBack()
                }) { Text("Keep Alive") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showKeepAliveDialog = false
                    val ctx = terminalViewRef?.context
                    if (ctx != null) {
                        val intent = android.content.Intent(ctx, com.adamoutler.ssh.network.SshService::class.java).apply {
                            action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
                            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, profileId)
                            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID, sessionId)
                        }
                        ctx.startService(intent)
                    }
                    onNavigateBack()
                }) { Text("Terminate") }
            }
        )
    }

    if (showTerminateConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTerminateConfirmDialog = false },
            title = { Text("Terminate Connection?") },
            text = { Text("Are you sure you want to terminate this SSH session? All running processes will be killed.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showTerminateConfirmDialog = false
                    val ctx = terminalViewRef?.context
                    if (ctx != null) {
                        val intent = android.content.Intent(ctx, com.adamoutler.ssh.network.SshService::class.java).apply {
                            action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
                            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, profileId)
                            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID, sessionId)
                        }
                        ctx.startService(intent)
                    }
                    onNavigateBack()
                }) { Text("Terminate") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showTerminateConfirmDialog = false
                }) { Text("Cancel") }
            }
        )
    }
    if (showDisconnectedOverlay) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                showDisconnectedOverlay = false
                onNavigateBack()
            },
            title = { Text("Session Disconnected") },
            text = { Text("The SSH session has ended or the connection was lost.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDisconnectedOverlay = false
                    onNavigateBack()
                }) { Text("OK") }
            }
        )
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val connectionStartTime = androidx.compose.runtime.remember { android.os.SystemClock.uptimeMillis() }

    val sendToTerminal: (ByteArray) -> Unit = { bytes ->
        if (showDisconnectedOverlay) {
            Log.d("TerminalScreen", "Input locked: session disconnected.")
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    var finalBytes = bytes
                    if (altSticky.value && bytes.size == 1) {
                        finalBytes = byteArrayOf(0x1B) + finalBytes
                        altSticky.value = false
                    }
                    activeSession.ptyOutputStream?.write(finalBytes)
                    activeSession.ptyOutputStream?.flush()
                } catch (ex: Exception) {
                    Log.e("TerminalScreen", "Failed to write to SSH PTY", ex)
                }
            }
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        val currentFontSizeState = androidx.compose.runtime.rememberUpdatedState(currentFontSize)
        val onUpdateFontSizeState = androidx.compose.runtime.rememberUpdatedState(onUpdateFontSize)

        if (profile?.protocol == com.adamoutler.ssh.data.Protocol.TELNET) {
            androidx.compose.material3.Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Insecure Protocol Warning",
                        tint = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        text = "${profile.nickname} (Telnet: Unencrypted)",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isHeadlessTest = ConnectionStateRepository.isHeadlessTest
            if (isHeadlessTest) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
                    Text(
                        text = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: "Welcome to CoSSH Terminal",
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag("TerminalAndroidView").onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.VolumeUp -> {
                                    onUpdateFontSize(currentFontSize + 1)
                                    true
                                }
                                Key.VolumeDown -> {
                                    onUpdateFontSize(currentFontSize - 1)
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                    factory = { ctx ->
                        val terminalView = TerminalView(ctx, null)
                        terminalView.setBackgroundColor(android.graphics.Color.BLACK)
                        terminalView.setTextSize(currentFontSize)
                        terminalView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        terminalView.isFocusable = true
                        terminalView.isFocusableInTouchMode = true

                        terminalView.setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                            override fun onScale(scale: Float): Float = scale
                            
                            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                                showOverlayButtons = true
                                if (terminalInputState == 0) {
                                    terminalInputState = 1
                                } else if (terminalInputState == 1) {
                                    terminalInputState = 2
                                } else {
                                    terminalInputState = 0
                                }
                                
                                terminalView.requestFocus()
                                val window = (ctx as? android.app.Activity)?.window
                                if (terminalInputState != 0) {
                                    if (window != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
                                        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }
                                } else {
                                    if (window != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
                                        val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
                                    }
                                }
                            }
                            
                            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                            override fun shouldEnforceCharBasedInput(): Boolean = false
                            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                            override fun isTerminalViewSelected(): Boolean = true
                            override fun copyModeChanged(b: Boolean) {}
                            
                            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, s: TerminalSession?): Boolean {
                                if (e?.action != android.view.KeyEvent.ACTION_DOWN) return false
                                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) return false
                                
                                // Prevent bleed-through key events from previous screens (like hitting Enter to connect)
                                if (isBleedThroughEvent(e, connectionStartTime)) {
                                    Log.d("TerminalScreen", "Ignoring bleed-through key event: keyCode=$keyCode")
                                    return true // Consume it so it doesn't propagate
                                }
                                
                                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                                    onUpdateFontSizeState.value(currentFontSizeState.value + 1)
                                    return true
                                }
                                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                                    onUpdateFontSizeState.value(currentFontSizeState.value - 1)
                                    return true
                                }

                                val bytesToSend = when (keyCode) {
                                    android.view.KeyEvent.KEYCODE_ENTER -> "\r".toByteArray()
                                    android.view.KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)
                                    android.view.KeyEvent.KEYCODE_TAB -> "\t".toByteArray()
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())
                                    else -> {
                                        val unicodeChar = e.unicodeChar
                                        if (unicodeChar != 0) {
                                            var cp = unicodeChar
                                            val ctrlPressed = ctrlSticky.value || e.isCtrlPressed
                                            if (ctrlPressed) {
                                                if (cp in 'a'.code..'z'.code) {
                                                    cp = cp - 'a'.code + 1
                                                } else if (cp in 'A'.code..'Z'.code) {
                                                    cp = cp - 'A'.code + 1
                                                } else if (cp == '['.code) {
                                                    cp = 27 // ESC
                                                } else if (cp == ']'.code) {
                                                    cp = 29
                                                } else if (cp == '\\'.code) {
                                                    cp = 28
                                                } else if (cp == '^'.code) {
                                                    cp = 30
                                                } else if (cp == '_'.code) {
                                                    cp = 31
                                                }
                                            }
                                            String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
                                        } else {
                                            null
                                        }
                                    }
                                }

                                if (bytesToSend != null) {
                                    if (ctrlSticky.value) ctrlSticky.value = false
                                    if (superSticky.value) superSticky.value = false
                                    if (menuSticky.value) menuSticky.value = false
                                    sendToTerminal(bytesToSend)
                                    Log.d("TerminalScreen", "Wrote ${bytesToSend.size} bytes (key: $keyCode) to SSH PTY stdin")
                                }
                                return true
                            }
                            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean {
                                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) return false
                                return true
                            }
                            override fun readControlKey(): Boolean = false
                            override fun readAltKey(): Boolean = false
                            override fun readShiftKey(): Boolean = false
                            override fun readFnKey(): Boolean = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean {
                                if (android.os.SystemClock.uptimeMillis() < connectionStartTime + 500) {
                                    Log.d("TerminalScreen", "Ignoring bleed-through codePoint: $codePoint")
                                    return true // Consume it so it doesn't propagate
                                }
                                try {
                                    var cp = codePoint
                                    if (ctrlSticky.value) {
                                        if (cp in 'a'.code..'z'.code) {
                                            cp = cp - 'a'.code + 1
                                        } else if (cp in 'A'.code..'Z'.code) {
                                            cp = cp - 'A'.code + 1
                                        } else if (cp == '['.code) {
                                            cp = 27 // ESC
                                        } else if (cp == ']'.code) {
                                            cp = 29
                                        } else if (cp == '\\'.code) {
                                            cp = 28
                                        } else if (cp == '^'.code) {
                                            cp = 30
                                        } else if (cp == '_'.code) {
                                            cp = 31
                                        }
                                        ctrlSticky.value = false
                                    }
                                    
                                    val chars = Character.toChars(cp)
                                    val bytes = String(chars).toByteArray(Charsets.UTF_8)
                                    if (superSticky.value) superSticky.value = false
                                    if (menuSticky.value) menuSticky.value = false
                                    sendToTerminal(bytes)
                                    Log.d("TerminalScreen", "Wrote ${bytes.size} bytes (codePoint) to SSH PTY stdin")
                                } catch (ex: Exception) {
                                    Log.e("TerminalScreen", "Failed to write codePoint to SSH PTY", ex)
                                }
                                return true
                            }
                            override fun onLongPress(e: android.view.MotionEvent?): Boolean = false
                            override fun onEmulatorSet() {}
                            override fun logError(tag: String?, msg: String?) {}
                            override fun logWarn(tag: String?, msg: String?) {}
                            override fun logInfo(tag: String?, msg: String?) {}
                            override fun logDebug(tag: String?, msg: String?) {}
                            override fun logVerbose(tag: String?, msg: String?) {}
                            override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) {}
                            override fun logStackTrace(tag: String?, e: Exception?) {}
                        })

                        terminalView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val newWidth = right - left
                            val newHeight = bottom - top
                            val oldWidth = oldRight - oldLeft
                            val oldHeight = oldBottom - oldTop

                            if (newWidth != oldWidth || newHeight != oldHeight) {
                                val emulator = session.emulator
                                if (emulator != null) {
                                    val cols = emulator.mColumns
                                    val rows = emulator.mRows
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            activeSession.sshShell?.changeWindowDimensions(cols, rows, newWidth, newHeight)
                                            Log.i("TerminalScreen", "SIGWINCH dispatched successfully: cols=$cols, rows=$rows, width=$newWidth, height=$newHeight")
                                        } catch (e: Exception) {
                                            Log.e("TerminalScreen", "Failed to send SIGWINCH", e)
                                        }
                                    }
                                }
                            }
                        }

                        terminalView.attachSession(session)
                        terminalViewRef = terminalView
                        terminalView.onScreenUpdated()
                        terminalView.requestFocus()
                        terminalView
                    },
                    update = { view ->
                        view.setTextSize((currentFontSize * view.context.resources.displayMetrics.scaledDensity).toInt())
                    }
                )
            }
            
            androidx.compose.animation.AnimatedVisibility(
                visible = showOverlayButtons,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                TerminalOverlayButtons(
                    onBackground = { onNavigateBack() },
                    onTerminate = {
                        val ctx = terminalViewRef?.context
                        if (ctx != null) {
                            val intent = android.content.Intent(ctx, com.adamoutler.ssh.network.SshService::class.java).apply {
                                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
                                putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, profileId)
                                putExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID, sessionId)
                            }
                            ctx.startService(intent)
                        }
                        onNavigateBack()
                    },
                    profile = profile
                )
            }
        }
        if (terminalInputState == 2) {
            TerminalExtraKeys(
                ctrlActive = ctrlSticky.value,
                altActive = altSticky.value,
                superActive = superSticky.value,
                menuActive = menuSticky.value,
                onKeyToggle = { key ->
                    when (key) {
                        "Ctrl" -> ctrlSticky.value = !ctrlSticky.value
                        "Alt" -> altSticky.value = !altSticky.value
                        "Super" -> superSticky.value = !superSticky.value
                        "Menu" -> menuSticky.value = !menuSticky.value
                    }
                },
                onKeyPress = { key ->
                    val bytes = when (key) {
                        "Esc" -> byteArrayOf(0x1B)
                        "Ctrl-C" -> byteArrayOf(0x03)
                        "↑" -> byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
                        "↓" -> byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
                        "→" -> byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
                        "←" -> byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())
                        "Tab" -> byteArrayOf(0x09)
                        "F1" -> byteArrayOf(0x1B, 'O'.code.toByte(), 'P'.code.toByte())
                        "F2" -> byteArrayOf(0x1B, 'O'.code.toByte(), 'Q'.code.toByte())
                        "F3" -> byteArrayOf(0x1B, 'O'.code.toByte(), 'R'.code.toByte())
                        "F4" -> byteArrayOf(0x1B, 'O'.code.toByte(), 'S'.code.toByte())
                        "F5" -> byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '5'.code.toByte(), '~'.code.toByte())
                        "F6" -> byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '7'.code.toByte(), '~'.code.toByte())
                        "F7" -> byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '8'.code.toByte(), '~'.code.toByte())
                        "F8" -> byteArrayOf(0x1B, '['.code.toByte(), '1'.code.toByte(), '9'.code.toByte(), '~'.code.toByte())
                        "F9" -> byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '0'.code.toByte(), '~'.code.toByte())
                        "F10" -> byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '1'.code.toByte(), '~'.code.toByte())
                        "F11" -> byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '~'.code.toByte())
                        "F12" -> byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '4'.code.toByte(), '~'.code.toByte())
                        "Home" -> byteArrayOf(0x1B, '['.code.toByte(), 'H'.code.toByte())
                        "End" -> byteArrayOf(0x1B, '['.code.toByte(), 'F'.code.toByte())
                        "PgUp" -> byteArrayOf(0x1B, '['.code.toByte(), '5'.code.toByte(), '~'.code.toByte())
                        "PgDn" -> byteArrayOf(0x1B, '['.code.toByte(), '6'.code.toByte(), '~'.code.toByte())
                        "Ins" -> byteArrayOf(0x1B, '['.code.toByte(), '2'.code.toByte(), '~'.code.toByte())
                        "Del" -> byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '~'.code.toByte())
                        "PrtSc" -> byteArrayOf(0x1B, '['.code.toByte(), '3'.code.toByte(), '2'.code.toByte(), '~'.code.toByte())
                        "Pause" -> byteArrayOf(0x1A)
                        else -> null
                    }
                    if (bytes != null) {
                        if (ctrlSticky.value) ctrlSticky.value = false
                        if (superSticky.value) superSticky.value = false
                        if (menuSticky.value) menuSticky.value = false
                        android.util.Log.d("TerminalScreen", "Sending extra key bytes: ${bytes.joinToString(",") { String.format("0x%02X", it) }}")
                        sendToTerminal(bytes)
                    }
                }
            )
        }
    }
}

@Composable
fun TerminalOverlayButtons(
    onBackground: () -> Unit,
    onTerminate: () -> Unit,
    profile: com.adamoutler.ssh.data.ConnectionProfile?,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        if (profile?.protocol == com.adamoutler.ssh.data.Protocol.TELNET) {
            androidx.compose.material3.Surface(
                color = Color(0xFFE65100),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Insecure Protocol Warning",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Text(
                        text = "Telnet: Unencrypted Connection",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.IconButton(
                onClick = onBackground,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Background Session",
                    tint = Color.White
                )
            }

            androidx.compose.material3.IconButton(
                onClick = onTerminate,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Terminate Session",
                    tint = Color.White
                )
            }
        }
    }
}
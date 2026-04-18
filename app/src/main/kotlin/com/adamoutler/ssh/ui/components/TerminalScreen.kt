package com.adamoutler.ssh.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.platform.testTag
import com.adamoutler.ssh.network.SshSessionProvider
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
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

enum class TerminalInputState {
    NONE, KEYBOARD, KEYBOARD_AND_BUTTONS
}

@Composable
fun TerminalScreen(
    profileId: String,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val activeSession = remember(profileId) { SshSessionProvider.getOrCreateSession(profileId) }
    val session = activeSession.terminalSession

    if (session == null) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
            Text(
                text = "Failed to initialize native terminal emulation.",
                color = Color.Red,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    DisposableEffect(profileId) {
        SshSessionProvider.onScreenUpdated = { updatedProfileId -> 
            if (updatedProfileId == profileId) {
                terminalViewRef?.onScreenUpdated() 
            }
        }
        SshSessionProvider.getContext = { terminalViewRef?.context }
        onDispose {
            SshSessionProvider.onScreenUpdated = null
            SshSessionProvider.getContext = null
        }
    }

    // Terminal Input State Machine:
    // State 0: No keyboard, no extra buttons.
    // State 1: Soft keyboard visible, no extra buttons.
    // State 2: Soft keyboard visible AND extra buttons (TerminalExtraKeys) visible.
    // 
    // Tap Behavior Expected:
    // - Tap when State 0 -> Transitions to State 1 (shows keyboard)
    // - Tap when State 1 -> Transitions to State 2 (shows buttons)
    // - Tap when State 2 -> Transitions to State 1 (hides buttons)
    // - Note: Tapping does NOT hide the keyboard (never goes back to State 0).
    //         Only the Android Back button transitions to State 0 and hides the keyboard.
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
    val activeConnections by SshSessionProvider.activeConnections.collectAsState()
    val isConnectionActive = activeConnections.isNotEmpty()
    var wasActive by remember { mutableStateOf(false) }
    var showDisconnectedOverlay by remember { mutableStateOf(false) }

    val connectionStates by SshSessionProvider.connectionStates.collectAsState()
    val errorStateEntry = connectionStates.entries.firstOrNull { it.value is com.adamoutler.ssh.network.ConnectionState.Error }
    val errorProfileId = errorStateEntry?.key
    val errorMessage = (errorStateEntry?.value as? com.adamoutler.ssh.network.ConnectionState.Error)?.message

    if (errorProfileId != null && errorMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                SshSessionProvider.clearConnectionState(errorProfileId)
                onNavigateBack()
            },
            title = { Text("Connection Failed") },
            text = { Text("Error: $errorMessage") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    SshSessionProvider.clearConnectionState(errorProfileId)
                    onNavigateBack()
                }) { Text("OK") }
            }
        )
    }

    androidx.compose.runtime.LaunchedEffect(isConnectionActive) {
        if (isConnectionActive) {
            wasActive = true
            showDisconnectedOverlay = false
        } else if (wasActive) {
            showDisconnectedOverlay = true
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (terminalInputState != 0) {
            terminalInputState = 0
            terminalViewRef?.let {
                val window = (it.context as? android.app.Activity)?.window
                if (window != null) {
                    val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, it)
                    insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                    it.clearFocus()
                } else {
                    val imm = it.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(it.windowToken, 0)
                }
            }
        } else {
            if (isConnectionActive) {
                showKeepAliveDialog = true
            } else {
                onNavigateBack()
            }
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
                    val context = terminalViewRef?.context
                    if (context != null) {
                        val intent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply { 
                            action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT 
                        }
                        context.startService(intent)
                    }
                    onNavigateBack()
                }) { Text("Terminate") }
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

    var currentFontSize by remember { mutableStateOf(14) }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isHeadlessTest = SshSessionProvider.isHeadlessTest
            if (isHeadlessTest) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
                    Text(
                        text = activeSession.mockTestTranscript ?: "Welcome to CoSSH Terminal",
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
                                    currentFontSize++
                                    terminalViewRef?.setTextSize(currentFontSize)
                                    true
                                }
                                Key.VolumeDown -> {
                                    if (currentFontSize > 6) {
                                        currentFontSize--
                                        terminalViewRef?.setTextSize(currentFontSize)
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    },
                    factory = { context ->
                        val terminalView = TerminalView(context, null)
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
                                val window = (context as? android.app.Activity)?.window
                                if (terminalInputState != 0) {
                                    if (window != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
                                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }
                                } else {
                                    if (window != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
                                        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
                                    }
                                }
                            }
                            
                            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                            override fun shouldEnforceCharBasedInput(): Boolean = false
                            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                            override fun isTerminalViewSelected(): Boolean = true
                            override fun copyModeChanged(b: Boolean) {}
                            
                            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean {
                                if (e?.action != android.view.KeyEvent.ACTION_DOWN) return false
                                
                                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
                                    currentFontSize++
                                    terminalView.setTextSize(currentFontSize)
                                    return true
                                }
                                if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                                    if (currentFontSize > 6) {
                                        currentFontSize--
                                        terminalView.setTextSize(currentFontSize)
                                    }
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
                                            String(Character.toChars(unicodeChar)).toByteArray(Charsets.UTF_8)
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
                                return true // Return true to prevent falling through to dummy local shell
                            }
                            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = true
                            override fun readControlKey(): Boolean = false
                            override fun readAltKey(): Boolean = false
                            override fun readShiftKey(): Boolean = false
                            override fun readFnKey(): Boolean = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
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

                        var lastCols = -1
                        var lastRows = -1
                        terminalView.viewTreeObserver.addOnPreDrawListener {
                            val emulator = session.emulator
                            if (emulator != null) {
                                val cols = emulator.mColumns
                                val rows = emulator.mRows
                                if (cols != lastCols || rows != lastRows) {
                                    lastCols = cols
                                    lastRows = rows
                                    val width = terminalView.width
                                    val height = terminalView.height
                                    // MUST dispatch to IO thread: sshj performs a socket write
                                    // for SIGWINCH which triggers NetworkOnMainThreadException
                                    // on the UI thread, corrupting the SSH transport.
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            activeSession.sshShell?.changeWindowDimensions(cols, rows, width, height)
                                        } catch (e: Exception) {
                                            Log.e("TerminalScreen", "Failed to send SIGWINCH", e)
                                        }
                                    }
                                }
                            }
                            true
                        }

                        terminalView.attachSession(session)
                        terminalViewRef = terminalView
                        terminalView
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
                        val context = terminalViewRef?.context
                        if (context != null) {
                            val intent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply { 
                                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT 
                            }
                            context.startService(intent)
                        }
                        onNavigateBack()
                    }
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
                        "Up" -> byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())
                        "Down" -> byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())
                        "Right" -> byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())
                        "Left" -> byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())
                        "Tab" -> byteArrayOf(0x09)
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
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
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

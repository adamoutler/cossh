package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

fun isBleedThroughEvent(e: android.view.KeyEvent?, connectionStartTime: Long): Boolean {
    if (e == null) return false
    return e.downTime < connectionStartTime + 500
}

enum class TerminalInputState {
    NONE,
    KEYBOARD,
    KEYBOARD_AND_BUTTONS,
}

/**
 * Tri-state logic for terminal modifiers (Ctrl, Alt, etc.)
 * - INACTIVE: Normal behavior.
 * - STICKY: Modified next keypress, then automatically returns to INACTIVE.
 * - LOCKED: Persists across multiple keypresses until manually toggled back to INACTIVE.
 *
 * NOTE TO FUTURE AGENTS: Do NOT remove the STICKY -> INACTIVE transition in key handlers.
 * This latching behavior is intentional to support both one-shot modifiers (mobile standard)
 * and persistent modifiers (power-user workflows).
 */
enum class ModifierState {
    INACTIVE,
    STICKY,
    LOCKED,
    ;

    fun next(): ModifierState = when (this) {
        INACTIVE -> STICKY
        STICKY -> LOCKED
        LOCKED -> INACTIVE
    }

    val isActive: Boolean get() = this != INACTIVE
}

@Composable
fun TerminalScreen(
    profileId: String,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    terminalViewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit = {},
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

    val disconnectedStateEntry = connectionStates.entries.firstOrNull { it.key == profileId && (it.value is com.adamoutler.ssh.network.ConnectionState.Disconnected || it.value is com.adamoutler.ssh.network.ConnectionState.Disconnecting) }
    val isDisconnected = disconnectedStateEntry != null

    val authPromptRequest by ConnectionStateRepository.authPromptRequest.collectAsState()

    if (authPromptRequest != null && authPromptRequest?.profileId == profileId) {
        AuthPromptDialog(
            requireUsername = authPromptRequest!!.requireUsername,
            isRetry = authPromptRequest!!.isRetry,
            onConfirm = { credentials -> ConnectionStateRepository.resolveAuthPrompt(credentials) },
            onDismiss = { ConnectionStateRepository.resolveAuthPrompt(null) },
        )
    }

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
        onPaste = { terminalViewModel.onPasteTextFromClipboard(session) },
        profile = remember(profileId) {
            com.adamoutler.ssh.crypto.SecurityStorageManager(context).getProfile(profileId)
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenContent(
    profileId: String,
    sessionId: String,
    session: TerminalSession,
    activeSession: com.adamoutler.ssh.network.ActiveSessionState,
    currentFontSize: Int,
    isConnectionActive: Boolean,
    errorMessage: String?,
    onUpdateFontSize: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    onPaste: () -> Unit = {},
    isTerminated: Boolean = false,
    profile: com.adamoutler.ssh.data.ConnectionProfile? = null,
    initialTerminalInputState: Int = 0,
) {
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var lastDataTime by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }

    androidx.compose.runtime.LaunchedEffect(sessionId) {
        val processBytes = { bytes: ByteArray ->
            lastDataTime = System.currentTimeMillis()
            if (ConnectionStateRepository.isHeadlessTest) {
                val newText = String(bytes, Charsets.UTF_8)
                val current = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: ""
                ConnectionStateRepository.mockTestTranscripts[sessionId] = current + newText
            }
            // Always append to the emulator to ensure JNI library is properly initialized
            // and doesn't crash on teardown, even during headless tests.
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

    var terminalInputState by remember { androidx.compose.runtime.mutableIntStateOf(profile?.terminalInputState ?: initialTerminalInputState) }
    var showOverlayButtons by remember { mutableStateOf(false) }
    var showTerminalMenuBottomSheet by remember { mutableStateOf(false) }
    val ctrlState = remember { mutableStateOf(ModifierState.INACTIVE) }
    val altState = remember { mutableStateOf(ModifierState.INACTIVE) }
    val superState = remember { mutableStateOf(ModifierState.INACTIVE) }
    val menuState = remember { mutableStateOf(ModifierState.INACTIVE) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val window = (context as? android.app.Activity)?.window
    val isProximityNear by rememberProximitySensorState()
    var isDataFlowing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(lastDataTime) {
        isDataFlowing = true
        kotlinx.coroutines.delay(30000)
        isDataFlowing = false
    }

    var keepScreenOnMode by remember { mutableStateOf(profile?.keepScreenOnMode ?: com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT) }

    androidx.compose.runtime.LaunchedEffect(keepScreenOnMode, isProximityNear, isDataFlowing) {
        if (window == null) return@LaunchedEffect

        val shouldKeepOn = when (keepScreenOnMode) {
            com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT -> false
            com.adamoutler.ssh.data.KeepScreenOnMode.ALWAYS_ON -> !isProximityNear
            com.adamoutler.ssh.data.KeepScreenOnMode.SMART_AWAKE -> !isProximityNear && isDataFlowing
        }

        if (shouldKeepOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    androidx.compose.runtime.LaunchedEffect(showOverlayButtons) {
        if (showOverlayButtons) {
            kotlinx.coroutines.delay(3000)
            showOverlayButtons = false
        }
    }

    var showKeepAliveDialog by remember { mutableStateOf(false) }
    var showTerminateConfirmDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var wasActive by remember { mutableStateOf(false) }
    var showDisconnectedOverlay by remember { mutableStateOf(false) }

    if (errorMessage != null) {
        ConnectionFailedDialog(
            errorMessage = errorMessage,
            onDismiss = {
                onClearError()
                onNavigateBack()
            },
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
        KeepAliveDialog(
            onDismiss = { showKeepAliveDialog = false },
            onKeepAlive = {
                showKeepAliveDialog = false
                onNavigateBack()
            },
            onTerminate = {
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
            },
        )
    }

    if (showTerminateConfirmDialog) {
        TerminateConfirmDialog(
            onDismiss = { showTerminateConfirmDialog = false },
            onTerminate = {
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
            },
        )
    }
    if (showDisconnectedOverlay) {
        SessionDisconnectedDialog(
            onDismiss = {
                showDisconnectedOverlay = false
                onNavigateBack()
            },
        )
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val connectionStartTime = androidx.compose.runtime.remember { android.os.SystemClock.uptimeMillis() }

    val writeChannel = androidx.compose.runtime.remember { kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED) }

    androidx.compose.runtime.LaunchedEffect(activeSession) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            for (bytes in writeChannel) {
                try {
                    activeSession.ptyOutputStream?.write(bytes)
                    activeSession.ptyOutputStream?.flush()
                } catch (ex: Exception) {
                    println(("TerminalScreen").toString() + ": " + ("Failed to write to SSH PTY").toString() + " " + (ex).toString())
                }
            }
        }
    }

    val sendToTerminal: (ByteArray) -> Unit = { bytes ->
        if (showDisconnectedOverlay) {
            println(("TerminalScreen").toString() + ": " + ("Input locked: session disconnected.").toString())
        } else {
            var finalBytes = bytes
            // Apply Alt modifier by prepending ESC (0x1B)
            if (altState.value.isActive && bytes.size == 1) {
                finalBytes = byteArrayOf(0x1B) + finalBytes
                if (altState.value == ModifierState.STICKY) {
                    altState.value = ModifierState.INACTIVE
                }
            }
            writeChannel.trySend(finalBytes)
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val currentFontSizeState = androidx.compose.runtime.rememberUpdatedState(currentFontSize)
        val onUpdateFontSizeState = androidx.compose.runtime.rememberUpdatedState(onUpdateFontSize)

        if (profile?.protocol == com.adamoutler.ssh.data.Protocol.TELNET) {
            TelnetInsecureWarning(nickname = profile.nickname)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isHeadlessTest = ConnectionStateRepository.isHeadlessTest
            if (isHeadlessTest) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
                    Text(
                        text = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: "Welcome to CoSSH Terminal",
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
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
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        terminalView.isFocusable = true
                        terminalView.isFocusableInTouchMode = true

                        terminalView.setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                            override fun onScale(scale: Float): Float = scale

                            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                                showOverlayButtons = true
                                val isFixed = terminalInputState == 2
                                
                                if (!isFixed) {
                                    // Standard mode: Toggle between 0 (hidden) and 1 (shown keyboard+buttons temporarily)
                                    terminalInputState = if (terminalInputState == 0) 1 else 0
                                    
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        profile?.copy(terminalInputState = terminalInputState)?.let { updatedProfile ->
                                            com.adamoutler.ssh.crypto.SecurityStorageManager(ctx).saveProfile(updatedProfile)
                                        }
                                    }
                                }

                                terminalView.requestFocus()
                                val activityWindow = (ctx as? android.app.Activity)?.window
                                val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager

                                if (isFixed || terminalInputState == 1) {
                                    if (activityWindow != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(activityWindow, terminalView)
                                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
                                        imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                    }
                                } else {
                                    if (window != null) {
                                        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                                        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                                    } else {
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
                                    println(("TerminalScreen").toString() + ": " + ("Ignoring bleed-through key event: keyCode=$keyCode").toString())
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
                                            val ctrlActive = ctrlState.value.isActive || e.isCtrlPressed
                                            if (ctrlActive) {
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
                                    if (ctrlState.value == ModifierState.STICKY) ctrlState.value = ModifierState.INACTIVE
                                    if (superState.value == ModifierState.STICKY) superState.value = ModifierState.INACTIVE
                                    if (menuState.value == ModifierState.STICKY) menuState.value = ModifierState.INACTIVE
                                    sendToTerminal(bytesToSend)
                                    println(("TerminalScreen").toString() + ": " + ("Wrote ${bytesToSend.size} bytes (key: $keyCode) to SSH PTY stdin").toString())
                                }
                                return true
                            }
                            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean {
                                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) return false
                                return true
                            }
                            override fun readControlKey(): Boolean = ctrlState.value.isActive
                            override fun readAltKey(): Boolean = altState.value.isActive
                            override fun readShiftKey(): Boolean = false
                            override fun readFnKey(): Boolean = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean {
                                if (android.os.SystemClock.uptimeMillis() < connectionStartTime + 500) {
                                    println(("TerminalScreen").toString() + ": " + ("Ignoring bleed-through codePoint: $codePoint").toString())
                                    return true // Consume it so it doesn't propagate
                                }
                                try {
                                    var cp = codePoint
                                    if (cp == 10) cp = 13 // Replace \n with \r
                                    if (ctrlState.value.isActive) {
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
                                        if (ctrlState.value == ModifierState.STICKY) {
                                            ctrlState.value = ModifierState.INACTIVE
                                        }
                                    }

                                    val chars = Character.toChars(cp)
                                    val bytes = String(chars).toByteArray(Charsets.UTF_8)
                                    if (superState.value == ModifierState.STICKY) superState.value = ModifierState.INACTIVE
                                    if (menuState.value == ModifierState.STICKY) menuState.value = ModifierState.INACTIVE
                                    sendToTerminal(bytes)
                                    println(("TerminalScreen").toString() + ": " + ("Wrote ${bytes.size} bytes (codePoint) to SSH PTY stdin").toString())
                                } catch (ex: Exception) {
                                    println(("TerminalScreen").toString() + ": " + ("Failed to write codePoint to SSH PTY").toString() + " " + (ex).toString())
                                }
                                return true
                            }
                            override fun onLongPress(e: android.view.MotionEvent?): Boolean {
                                return false
                            }
                            override fun onEmulatorSet() { /* No-op */ }
                            override fun logError(tag: String?, msg: String?) { /* No-op */ }
                            override fun logWarn(tag: String?, msg: String?) { /* No-op */ }
                            override fun logInfo(tag: String?, msg: String?) { /* No-op */ }
                            override fun logDebug(tag: String?, msg: String?) { /* No-op */ }
                            override fun logVerbose(tag: String?, msg: String?) { /* No-op */ }
                            override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) { /* No-op */ }
                            override fun logStackTrace(tag: String?, e: Exception?) { /* No-op */ }
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
                                            println(("TerminalScreen").toString() + ": " + ("SIGWINCH dispatched successfully: cols=$cols, rows=$rows, width=$newWidth, height=$newHeight").toString())
                                        } catch (e: Exception) {
                                            println(("TerminalScreen").toString() + ": " + ("Failed to send SIGWINCH").toString() + " " + (e).toString())
                                        }
                                    }
                                }
                            }
                        }

                        terminalView.attachSession(session)
                        terminalViewRef = terminalView
                        terminalView.onScreenUpdated()
                        terminalView.requestFocus()
                        
                        val wrapper = object : android.widget.FrameLayout(ctx) {
                            override fun startActionModeForChild(originalView: android.view.View, callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
                                val wrapped = object : android.view.ActionMode.Callback2() {
                                    override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                        val res = callback.onCreateActionMode(mode, menu)
                                        menu.add(android.view.Menu.NONE, 999, android.view.Menu.NONE, "Terminal settings")
                                        return res
                                    }
                                    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                        return callback.onPrepareActionMode(mode, menu)
                                    }
                                    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                        if (item.itemId == 999) {
                                            showTerminalMenuBottomSheet = true
                                            mode.finish()
                                            return true
                                        }
                                        return callback.onActionItemClicked(mode, item)
                                    }
                                    override fun onDestroyActionMode(mode: android.view.ActionMode) {
                                        callback.onDestroyActionMode(mode)
                                    }
                                    override fun onGetContentRect(mode: android.view.ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
                                        if (callback is android.view.ActionMode.Callback2) {
                                            callback.onGetContentRect(mode, view, outRect)
                                        }
                                    }
                                }
                                return super.startActionModeForChild(originalView, wrapped, type)
                            }
                            
                            override fun startActionModeForChild(originalView: android.view.View, callback: android.view.ActionMode.Callback): android.view.ActionMode? {
                                val wrapped = object : android.view.ActionMode.Callback2() {
                                    override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                        val res = callback.onCreateActionMode(mode, menu)
                                        menu.add(android.view.Menu.NONE, 999, android.view.Menu.NONE, "Terminal settings")
                                        return res
                                    }
                                    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                        return callback.onPrepareActionMode(mode, menu)
                                    }
                                    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                        if (item.itemId == 999) {
                                            showTerminalMenuBottomSheet = true
                                            mode.finish()
                                            return true
                                        }
                                        return callback.onActionItemClicked(mode, item)
                                    }
                                    override fun onDestroyActionMode(mode: android.view.ActionMode) {
                                        callback.onDestroyActionMode(mode)
                                    }
                                    override fun onGetContentRect(mode: android.view.ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
                                        if (callback is android.view.ActionMode.Callback2) {
                                            callback.onGetContentRect(mode, view, outRect)
                                        }
                                    }
                                }
                                return super.startActionModeForChild(originalView, wrapped)
                            }
                        }
                        wrapper.addView(terminalView)
                        wrapper
                    },
                    update = { view ->
                        val tv = (view as android.widget.FrameLayout).getChildAt(0) as TerminalView
                        tv.setTextSize((currentFontSize * density.density * density.fontScale).toInt())
                    },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showOverlayButtons,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
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
                    profile = profile,
                )
            }
        }

        var snippetToConfirm by remember { mutableStateOf<com.adamoutler.ssh.data.CommandSnippet?>(null) }
        
        snippetToConfirm?.let { snippet ->
            AlertDialog(
                onDismissRequest = { snippetToConfirm = null },
                title = { Text("Confirm Execution") },
                text = { Text("Are you sure you want to execute '${snippet.name}'?\n\nCommand: ${snippet.command}") },
                confirmButton = {
                    TextButton(onClick = {
                        val bytes = if (snippet.autoSend) "${snippet.command}\r".toByteArray() else snippet.command.toByteArray()
                        sendToTerminal(bytes)
                        snippetToConfirm = null
                    }) {
                        Text("Execute")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { snippetToConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (!profile?.commandSnippets.isNullOrEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profile!!.commandSnippets) { snippet ->
                    AssistChip(
                        onClick = {
                            if (snippet.requireAuth) {
                                snippetToConfirm = snippet
                            } else {
                                val bytes = if (snippet.autoSend) "${snippet.command}\r".toByteArray() else snippet.command.toByteArray()
                                sendToTerminal(bytes)
                            }
                        },
                        label = { Text(snippet.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.semantics {
                            contentDescription = "Inject snippet: ${snippet.name}. ${if (snippet.autoSend) "Executes immediately." else "Requires confirmation."}"
                        }
                    )
                }
            }
        }

        if (terminalInputState == 2 || terminalInputState == 1) {
            TerminalExtraKeys(
                ctrlState = ctrlState.value,
                altState = altState.value,
                superState = superState.value,
                menuState = menuState.value,
                onKeyToggle = { key ->
                    when (key) {
                        "Ctrl" -> ctrlState.value = ctrlState.value.next()
                        "Alt" -> altState.value = altState.value.next()
                        "Super" -> superState.value = superState.value.next()
                        "Menu" -> menuState.value = menuState.value.next()
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
                        if (ctrlState.value == ModifierState.STICKY) ctrlState.value = ModifierState.INACTIVE
                        if (superState.value == ModifierState.STICKY) superState.value = ModifierState.INACTIVE
                        if (menuState.value == ModifierState.STICKY) menuState.value = ModifierState.INACTIVE
                        println("TerminalScreen: Sending extra key bytes: ${bytes.joinToString(",") { String.format("0x%02X", it) }}")
                        sendToTerminal(bytes)
                    }
                },
            )
        }
    }

    if (showTerminalMenuBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTerminalMenuBottomSheet = false },
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(profile?.nickname ?: "Terminal Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Extra Buttons", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = terminalInputState != 2,
                        onClick = {
                            terminalInputState = 0
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                profile?.copy(terminalInputState = 0)?.let { updatedProfile ->
                                    com.adamoutler.ssh.crypto.SecurityStorageManager(context).saveProfile(updatedProfile)
                                }
                            }
                        },
                        label = { Text("Standard") },
                    )
                    FilterChip(
                        selected = terminalInputState == 2,
                        onClick = {
                            terminalInputState = 2
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                profile?.copy(terminalInputState = 2)?.let { updatedProfile ->
                                    com.adamoutler.ssh.crypto.SecurityStorageManager(context).saveProfile(updatedProfile)
                                }
                            }
                        },
                        label = { Text("Fixed") },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Keep Screen On Mode", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = com.adamoutler.ssh.data.KeepScreenOnMode.values()
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = keepScreenOnMode == mode,
                            onClick = {
                                keepScreenOnMode = mode
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    profile?.copy(keepScreenOnMode = mode)?.let { updatedProfile ->
                                        com.adamoutler.ssh.crypto.SecurityStorageManager(context).saveProfile(updatedProfile)
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(
                                when (mode) {
                                    com.adamoutler.ssh.data.KeepScreenOnMode.SYSTEM_DEFAULT -> "System"
                                    com.adamoutler.ssh.data.KeepScreenOnMode.SMART_AWAKE -> "Smart"
                                    com.adamoutler.ssh.data.KeepScreenOnMode.ALWAYS_ON -> "Always"
                                },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Font Size", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { onUpdateFontSize(currentFontSize - 1) }) {
                        Text("-", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("$currentFontSize", modifier = Modifier.padding(horizontal = 16.dp))
                    IconButton(onClick = { onUpdateFontSize(currentFontSize + 1) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showTerminalMenuBottomSheet = false
                        onPaste()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Paste from Clipboard")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

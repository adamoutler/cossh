package com.adamoutler.ssh.ui.components

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.network.ConnectionStateRepository
import com.adamoutler.ssh.ui.screens.TerminalViewModel
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme
import com.adamoutler.ssh.util.AppLog
import com.adamoutler.ssh.util.SanitizedUrl
import com.adamoutler.ssh.util.TerminalUrlSanitizer
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

@Composable
fun TerminalScreen(
    profileId: String,
    modifier: Modifier = Modifier,
    sessionId: String? = null,
    terminalViewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Resolve initial session ID or get the first active one
    val initialSessionId = remember(sessionId, profileId) {
        sessionId ?: ConnectionStateRepository.sessions.values.firstOrNull { it.profileId == profileId }?.sessionId ?: java.util.UUID.randomUUID().toString()
    }
    var activeSessionId by remember(initialSessionId) { mutableStateOf(initialSessionId) }

    val activeProfileId = remember(activeSessionId) {
        ConnectionStateRepository.sessions[activeSessionId]?.profileId ?: profileId
    }

    val session = remember(activeSessionId) { terminalViewModel.getOrCreateSession(activeSessionId, context) }
    val activeSession = remember(activeSessionId, activeProfileId) { ConnectionStateRepository.getOrCreateSession(activeProfileId, activeSessionId) }
    val currentFontSize by terminalViewModel.fontSizeFlow.collectAsState()
    val currentTheme by terminalViewModel.colorThemeFlow.collectAsState()
    val currentFont by terminalViewModel.fontChoiceFlow.collectAsState()
    val currentCursorStyle by terminalViewModel.cursorStyleFlow.collectAsState()
    val currentCursorBlink by terminalViewModel.cursorBlinkFlow.collectAsState()

    androidx.compose.runtime.LaunchedEffect(activeProfileId) {
        terminalViewModel.initFontSize(activeProfileId)
        terminalViewModel.initTheme(activeProfileId)
        terminalViewModel.initFont(activeProfileId)
        terminalViewModel.initCursor(activeProfileId)
    }

    val sessionStates by ConnectionStateRepository.sessionStates.collectAsState()
    val activeConnectionCounts by ConnectionStateRepository.activeConnectionCounts.collectAsState()
    val activeCount = activeConnectionCounts[activeProfileId] ?: 0
    val isConnectionActive = activeCount > 0

    val connectionStates by ConnectionStateRepository.connectionStates.collectAsState()
    val errorStateEntry = connectionStates.entries.firstOrNull { it.key == activeProfileId && it.value is com.adamoutler.ssh.network.ConnectionState.Error }
    val errorMessage = (errorStateEntry?.value as? com.adamoutler.ssh.network.ConnectionState.Error)?.message

    val terminatedStateEntry = connectionStates.entries.firstOrNull { it.key == activeProfileId && it.value is com.adamoutler.ssh.network.ConnectionState.Terminated }
    val isTerminated = terminatedStateEntry != null

    val disconnectedStateEntry = connectionStates.entries.firstOrNull { it.key == activeProfileId && (it.value is com.adamoutler.ssh.network.ConnectionState.Disconnected || it.value is com.adamoutler.ssh.network.ConnectionState.Disconnecting) }
    val isDisconnected = disconnectedStateEntry != null

    val reconnectingState = connectionStates[activeProfileId] as? com.adamoutler.ssh.network.ConnectionState.Reconnecting

    val authPromptRequest by ConnectionStateRepository.authPromptRequest.collectAsState()

    if (authPromptRequest != null && authPromptRequest?.profileId == activeProfileId) {
        AuthPromptDialog(
            requireUsername = authPromptRequest!!.requireUsername,
            isRetry = authPromptRequest!!.isRetry,
            onConfirm = { credentials -> ConnectionStateRepository.resolveAuthPrompt(credentials) },
            onDismiss = { ConnectionStateRepository.resolveAuthPrompt(null) },
        )
    }

    val kbdPromptRequest by ConnectionStateRepository.keyboardInteractivePromptRequest.collectAsState()

    if (kbdPromptRequest != null && kbdPromptRequest?.profileId == activeProfileId) {
        val req = kbdPromptRequest!!
        PamAuthDialog(
            name = req.name,
            instruction = req.instruction,
            prompts = req.prompts,
            round = req.round,
            onConfirm = { responses -> ConnectionStateRepository.resolveKeyboardInteractivePrompt(responses) },
            onDismiss = { ConnectionStateRepository.resolveKeyboardInteractivePrompt(null) },
        )
    }

    androidx.compose.runtime.LaunchedEffect(isDisconnected) {
        if (isDisconnected) {
            disconnectedStateEntry?.key?.let { ConnectionStateRepository.clearConnectionState(it) }
            onNavigateBack()
        }
    }

    val storageManager = remember(context) { com.adamoutler.ssh.crypto.SecurityStorageManager(context) }
    val sessionTabs = remember(sessionStates, connectionStates, activeSessionId, activeConnectionCounts) {
        val allActive = ConnectionStateRepository.sessions.values.toList()
        allActive.map { s ->
            val p = storageManager.getProfile(s.profileId)
            SessionTabItem(
                sessionId = s.sessionId,
                profileId = s.profileId,
                nickname = p?.nickname ?: s.profileId,
                isTelnet = p?.protocol == com.adamoutler.ssh.data.Protocol.TELNET,
                connectionState = sessionStates[s.sessionId] ?: connectionStates[s.profileId] ?: com.adamoutler.ssh.network.ConnectionState.Connected,
                isSelected = s.sessionId == activeSessionId,
            )
        }
    }

    val handleCloseSession: (String) -> Unit = { targetSessionId ->
        val targetSession = ConnectionStateRepository.sessions[targetSessionId]
        val targetProfileId = targetSession?.profileId ?: activeProfileId
        val intent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
            action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, targetProfileId)
            putExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID, targetSessionId)
        }
        context.startService(intent)
        terminalViewModel.closeSession(targetSessionId)
        ConnectionStateRepository.clearSession(targetSessionId)
        ConnectionStateRepository.clearSessionState(targetSessionId)

        if (targetSessionId == activeSessionId) {
            val remaining = ConnectionStateRepository.sessions.values.filter { it.sessionId != targetSessionId }
            if (remaining.isNotEmpty()) {
                activeSessionId = remaining.first().sessionId
            } else {
                onNavigateBack()
            }
        }
    }

    TerminalScreenContent(
        profileId = activeProfileId,
        sessionId = activeSessionId,
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
        profile = remember(activeProfileId) {
            storageManager.getProfile(activeProfileId)
        },
        modifier = modifier,
        terminalViewModel = terminalViewModel,
        currentTheme = currentTheme,
        currentFont = currentFont,
        reconnectingState = reconnectingState,
        onCancelReconnect = {
            val intent = android.content.Intent(context, com.adamoutler.ssh.network.SshService::class.java).apply {
                action = com.adamoutler.ssh.network.SshService.ACTION_DISCONNECT
                putExtra(com.adamoutler.ssh.network.SshService.EXTRA_PROFILE_ID, activeProfileId)
                putExtra(com.adamoutler.ssh.network.SshService.EXTRA_SESSION_ID, activeSessionId)
            }
            context.startService(intent)
            onNavigateBack()
        },
        sessionTabs = sessionTabs,
        onSelectSession = { activeSessionId = it },
        onCloseSession = handleCloseSession,
        onNewSession = onNavigateBack,
        cursorStyle = currentCursorStyle,
        cursorBlink = currentCursorBlink,
        onUpdateCursorStyle = { terminalViewModel.updateCursorStyle(it) },
        onUpdateCursorBlink = { terminalViewModel.updateCursorBlink(it) },
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
    terminalViewModel: TerminalViewModel? = null,
    currentTheme: TerminalTheme = TerminalTheme.COBALT_BLUE,
    currentFont: TerminalFontChoice = TerminalFontChoice.DEFAULT_MONOSPACE,
    reconnectingState: com.adamoutler.ssh.network.ConnectionState.Reconnecting? = null,
    onCancelReconnect: () -> Unit = {},
    sessionTabs: List<SessionTabItem> = emptyList(),
    onSelectSession: (String) -> Unit = {},
    onCloseSession: (String) -> Unit = {},
    onNewSession: () -> Unit = {},
    cursorStyle: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    cursorBlink: Boolean = true,
    onUpdateCursorStyle: (TerminalCursorStyle) -> Unit = {},
    onUpdateCursorBlink: (Boolean) -> Unit = {},
) {
    val resolvedTheme = remember(currentTheme, profile?.colorTheme) {
        if (profile?.colorTheme != null) {
            TerminalTheme.fromId(profile.colorTheme)
        } else {
            currentTheme
        }
    }
    val resolvedFont = remember(currentFont, profile?.fontChoice) {
        if (profile?.fontChoice != null) {
            TerminalFontChoice.fromId(profile.fontChoice)
        } else {
            currentFont
        }
    }
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }
    var detectedUrlForConfirmation by remember { mutableStateOf<SanitizedUrl?>(null) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var lastDataTime by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }

    androidx.compose.runtime.LaunchedEffect(terminalViewModel, terminalViewRef) {
        terminalViewModel?.bellEvents?.collect {
            terminalViewRef?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    androidx.compose.runtime.LaunchedEffect(session, terminalViewRef) {
        terminalViewRef?.let { tv ->
            tv.attachSession(session)
            tv.onScreenUpdated()
        }
    }

    androidx.compose.runtime.LaunchedEffect(cursorStyle, cursorBlink, terminalViewRef, session) {
        terminalViewRef?.setTerminalCursorBlinkerState(cursorBlink, true)
        val decscusrCode = when (cursorStyle) {
            TerminalCursorStyle.BLOCK -> if (cursorBlink) 1 else 2
            TerminalCursorStyle.UNDERLINE -> if (cursorBlink) 3 else 4
            TerminalCursorStyle.VERTICAL_BAR -> if (cursorBlink) 5 else 6
        }
        val decscusrSeq = "\u001B[${decscusrCode} q".toByteArray()
        session.emulator?.append(decscusrSeq, decscusrSeq.size)
    }

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
            ConnectionStateRepository.getSessionOutputFlow(sessionId).collect { bytes ->
                processBytes(bytes)
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

        val shouldKeepOn = com.adamoutler.ssh.data.shouldKeepScreenOn(
            mode = keepScreenOnMode,
            isProximityNear = isProximityNear,
            isDataFlowing = isDataFlowing,
        )

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
            TerminalWriteBatcher.processChannel(
                channel = writeChannel,
                getOutputStream = { activeSession.ptyOutputStream },
            )
        }
    }

    val currentFontSizeState = androidx.compose.runtime.rememberUpdatedState(currentFontSize)
    val onUpdateFontSizeState = androidx.compose.runtime.rememberUpdatedState(onUpdateFontSize)

    val terminalKeyMapper = remember(sessionId) {
        TerminalKeyMapper(
            getCtrlState = { ctrlState.value },
            setCtrlState = { ctrlState.value = it },
            getAltState = { altState.value },
            setAltState = { altState.value = it },
            getSuperState = { superState.value },
            setSuperState = { superState.value = it },
            getMenuState = { menuState.value },
            setMenuState = { menuState.value = it },
            sendRawBytes = { bytes -> writeChannel.trySend(bytes) },
            isInputLocked = { showDisconnectedOverlay },
            getConnectionStartTime = { connectionStartTime },
            onVolumeUp = { onUpdateFontSizeState.value(currentFontSizeState.value + 1) },
            onVolumeDown = { onUpdateFontSizeState.value(currentFontSizeState.value - 1) },
        )
    }

    androidx.compose.runtime.DisposableEffect(terminalKeyMapper) {
        onDispose {
            terminalKeyMapper.resetModifiers()
        }
    }

    val sendToTerminal: (ByteArray) -> Unit = { bytes ->
        terminalKeyMapper.sendToTerminal(bytes)
    }

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize(),
    ) {
        TerminalSessionTabBar(
            sessions = sessionTabs,
            onSelectSession = onSelectSession,
            onCloseSession = onCloseSession,
            onNewSession = onNewSession,
            backgroundColor = Color(resolvedTheme.background),
        )

        if (reconnectingState != null) {
            TerminalReconnectBanner(
                attempt = reconnectingState.attempt,
                maxAttempts = reconnectingState.maxAttempts,
                message = reconnectingState.message,
                onCancel = onCancelReconnect,
            )
        }

        if (profile?.protocol == com.adamoutler.ssh.data.Protocol.TELNET) {
            TelnetInsecureWarning(nickname = profile.nickname)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val isHeadlessTest = ConnectionStateRepository.isHeadlessTest
            if (isHeadlessTest) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(resolvedTheme.background))
                        .padding(4.dp),
                ) {
                    Text(
                        text = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: "Welcome to CoSSH Terminal",
                        color = Color(resolvedTheme.foreground),
                        fontFamily = resolvedFont.composeFontFamily,
                        fontSize = currentFontSize.sp,
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
                        terminalView.attachSession(session)
                        resolvedTheme.applyTo(terminalView, session)
                        terminalView.setTypeface(resolvedFont.getTypeface())
                        terminalView.setTextSize((currentFontSize * density.density * density.fontScale).toInt())
                        terminalView.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        terminalView.isFocusable = true
                        terminalView.isFocusableInTouchMode = true

                        terminalView.setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                            override fun onScale(scale: Float): Float = TerminalScaleHandler.onScale(
                                scale = scale,
                                currentFontSize = currentFontSizeState.value,
                                onUpdateFontSize = onUpdateFontSizeState.value,
                            )

                            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                                if (e != null) {
                                    val colRow = terminalView.getColumnAndRow(e, true)
                                    val emulator = session.emulator
                                    if (emulator != null && colRow != null && colRow.size >= 2) {
                                        val word = emulator.screen.getWordAtLocation(colRow[0], colRow[1])
                                        if (!word.isNullOrBlank()) {
                                            val candidate = TerminalUrlSanitizer.extractFirstUrl(word)
                                            if (candidate != null) {
                                                detectedUrlForConfirmation = candidate
                                                return
                                            }
                                        }
                                    }
                                }

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

                            override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, s: TerminalSession?): Boolean = terminalKeyMapper.onKeyDown(keyCode, e)
                            override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = terminalKeyMapper.onKeyUp(keyCode, e)
                            override fun readControlKey(): Boolean = terminalKeyMapper.readControlKey()
                            override fun readAltKey(): Boolean = terminalKeyMapper.readAltKey()
                            override fun readShiftKey(): Boolean = terminalKeyMapper.readShiftKey()
                            override fun readFnKey(): Boolean = false
                            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, s: TerminalSession?): Boolean = terminalKeyMapper.onCodePoint(codePoint, ctrlDown)
                            override fun onLongPress(e: android.view.MotionEvent?): Boolean {
                                if (e != null) {
                                    val colRow = terminalView.getColumnAndRow(e, true)
                                    val emulator = session.emulator
                                    if (emulator != null && colRow != null && colRow.size >= 2) {
                                        val word = emulator.screen.getWordAtLocation(colRow[0], colRow[1])
                                        if (!word.isNullOrBlank()) {
                                            val candidate = TerminalUrlSanitizer.extractFirstUrl(word)
                                            if (candidate != null) {
                                                detectedUrlForConfirmation = candidate
                                                return true
                                            }
                                        }
                                    }
                                }
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
                                            activeSession.onWindowResize?.invoke(cols, rows, newWidth, newHeight)
                                            activeSession.sshShell?.changeWindowDimensions(cols, rows, newWidth, newHeight)
                                            AppLog.d("TerminalScreen", "SIGWINCH dispatched successfully: cols=$cols, rows=$rows, width=$newWidth, height=$newHeight")
                                        } catch (e: Exception) {
                                            AppLog.w("TerminalScreen", "Failed to send SIGWINCH: ${e.message}", e)
                                        }
                                    }
                                }
                            }
                        }

                        terminalView.attachSession(session)
                        terminalViewRef = terminalView
                        terminalView.onScreenUpdated()
                        terminalView.requestFocus()
                        terminalView.setOnFocusChangeListener { _, hasFocus ->
                            if (!hasFocus) {
                                terminalKeyMapper.resetModifiers()
                            }
                        }

                        terminalView.setOnCreateContextMenuListener { menu, _, _ ->
                            val pasteItem = menu.add(android.view.Menu.NONE, android.R.id.paste, android.view.Menu.NONE, android.R.string.paste)
                            pasteItem.setOnMenuItemClickListener {
                                onPaste()
                                terminalView.requestFocus()
                                true
                            }
                        }

                        val wrapper = object : android.widget.FrameLayout(ctx) {
                            override fun startActionModeForChild(originalView: android.view.View, callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
                                val wrapped = object : android.view.ActionMode.Callback2() {
                                    override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                                        val res = callback.onCreateActionMode(mode, menu)
                                        menu.add(android.view.Menu.NONE, 999, android.view.Menu.NONE, "Terminal settings")
                                        return res
                                    }
                                    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean = callback.onPrepareActionMode(mode, menu)
                                    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                        if (item.itemId == 999) {
                                            showTerminalMenuBottomSheet = true
                                            mode.finish()
                                            return true
                                        }
                                        val isStandardPaste = item.itemId == android.R.id.paste ||
                                            item.itemId == android.R.id.pasteAsPlainText
                                        if (isStandardPaste) {
                                            onPaste()
                                            mode.finish()
                                            terminalView.requestFocus()
                                            return true
                                        }
                                        if (item.itemId == 2) {
                                            val res = callback.onActionItemClicked(mode, item)
                                            terminalView.requestFocus()
                                            return res
                                        }
                                        return callback.onActionItemClicked(mode, item)
                                    }
                                    override fun onDestroyActionMode(mode: android.view.ActionMode) {
                                        callback.onDestroyActionMode(mode)
                                        terminalView.requestFocus()
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
                                    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean = callback.onPrepareActionMode(mode, menu)
                                    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                                        if (item.itemId == 999) {
                                            showTerminalMenuBottomSheet = true
                                            mode.finish()
                                            return true
                                        }
                                        val isStandardPaste = item.itemId == android.R.id.paste ||
                                            item.itemId == android.R.id.pasteAsPlainText
                                        if (isStandardPaste) {
                                            onPaste()
                                            mode.finish()
                                            terminalView.requestFocus()
                                            return true
                                        }
                                        if (item.itemId == 2) {
                                            val res = callback.onActionItemClicked(mode, item)
                                            terminalView.requestFocus()
                                            return res
                                        }
                                        return callback.onActionItemClicked(mode, item)
                                    }
                                    override fun onDestroyActionMode(mode: android.view.ActionMode) {
                                        callback.onDestroyActionMode(mode)
                                        terminalView.requestFocus()
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
                        tv.attachSession(session)
                        tv.setTextSize((currentFontSize * density.density * density.fontScale).toInt())
                        tv.setTypeface(resolvedFont.getTypeface())
                        resolvedTheme.applyTo(tv, session)
                        tv.onScreenUpdated()
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
        val snippetHandler = remember(sendToTerminal, context) {
            TerminalSnippetHandler(
                sendToTerminal = sendToTerminal,
                requestConfirmation = { snippetToConfirm = it },
                requestAuthentication = if (ConnectionStateRepository.isHeadlessTest) {
                    null
                } else {
                    { snippet, onAuthenticated ->
                        val activity = context as? android.app.Activity
                        if (activity != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            try {
                                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                                    .setTitle("Authenticate Snippet")
                                    .setSubtitle("Biometric authentication required to execute '${snippet.name}'")
                                    .setNegativeButton("Cancel", activity.mainExecutor) { _, _ -> }
                                    .build()
                                val signal = android.os.CancellationSignal()
                                prompt.authenticate(
                                    signal,
                                    activity.mainExecutor,
                                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                                            onAuthenticated()
                                        }

                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                                            // Authentication cancelled or failed
                                        }
                                    },
                                )
                            } catch (e: Exception) {
                                snippetToConfirm = snippet
                            }
                        } else {
                            snippetToConfirm = snippet
                        }
                    }
                },
            )
        }

        snippetToConfirm?.let { snippet ->
            AlertDialog(
                onDismissRequest = { snippetToConfirm = null },
                title = { Text("Confirm Execution") },
                text = { Text("Are you sure you want to execute '${snippet.name}'?\n\nCommand: ${snippet.command}") },
                confirmButton = {
                    TextButton(onClick = {
                        snippetHandler.onSnippetConfirmed(snippet)
                        snippetToConfirm = null
                    }) {
                        Text("Execute")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { snippetToConfirm = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (!profile?.commandSnippets.isNullOrEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profile!!.commandSnippets) { snippet ->
                    AssistChip(
                        onClick = {
                            snippetHandler.onSnippetClicked(snippet)
                        },
                        label = { Text(snippet.name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            labelColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.semantics {
                            val executionInfo = buildString {
                                if (snippet.requireAuth) {
                                    append("Requires confirmation. ")
                                }
                                if (snippet.autoSend) {
                                    append("Executes immediately.")
                                } else {
                                    append("Inserts into buffer without executing.")
                                }
                            }
                            contentDescription = "Inject snippet: ${snippet.name}. $executionInfo"
                        },
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
                onKeyToggle = { key -> terminalKeyMapper.onExtraKeyToggle(key) },
                onKeyPress = { key -> terminalKeyMapper.onExtraKeyPress(key) },
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

                Text("Cursor Style", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val styles = TerminalCursorStyle.entries
                    styles.forEachIndexed { index, style ->
                        SegmentedButton(
                            selected = cursorStyle == style,
                            onClick = { onUpdateCursorStyle(style) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                        ) {
                            Text(style.displayName)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Blinking Cursor", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = cursorBlink,
                        onCheckedChange = { onUpdateCursorBlink(it) },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TerminalCursorPreviewBox(
                    theme = resolvedTheme,
                    fontChoice = resolvedFont,
                    cursorStyle = cursorStyle,
                    cursorBlink = cursorBlink,
                    modifier = Modifier.fillMaxWidth(),
                )

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

    detectedUrlForConfirmation?.let { sanitizedUrl ->
        UrlConfirmationBottomSheet(
            sanitizedUrl = sanitizedUrl,
            onOpenBrowser = {
                try {
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, sanitizedUrl.uri).apply {
                        addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(android.content.Intent.createChooser(browserIntent, "Open with"))
                } catch (e: Exception) {
                    AppLog.w("TerminalScreen", "No browser app found: ${e.message}")
                }
                detectedUrlForConfirmation = null
            },
            onCopyLink = {
                com.adamoutler.ssh.security.SecureClipboardManager.copyToClipboard(
                    context = context,
                    text = sanitizedUrl.rawUrl,
                    label = "URL",
                )
                detectedUrlForConfirmation = null
            },
            onDismiss = { detectedUrlForConfirmation = null },
        )
    }
}

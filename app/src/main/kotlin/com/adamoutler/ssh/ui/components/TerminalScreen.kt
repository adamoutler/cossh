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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adamoutler.ssh.network.SshSessionProvider
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import java.lang.Exception
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.util.Log

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    initialText: String = "Welcome to CoSSH Terminal\r\n\u001B[32mANSI Color Support Active!\u001B[0m\r\n"
) {
    var terminalViewRef by remember { mutableStateOf<TerminalView?>(null) }

    val session = remember {
        val client = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {
                terminalViewRef?.onScreenUpdated()
            }
            override fun onTitleChanged(session: TerminalSession) {}
            override fun onSessionFinished(session: TerminalSession) {}
            override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
            override fun onPasteTextFromClipboard(session: TerminalSession) {}
            override fun onBell(session: TerminalSession) {}
            override fun onColorsChanged(session: TerminalSession) {}
            override fun onTerminalCursorStateChange(state: Boolean) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, msg: String?) {}
            override fun logWarn(tag: String?, msg: String?) {}
            override fun logInfo(tag: String?, msg: String?) {}
            override fun logDebug(tag: String?, msg: String?) {}
            override fun logVerbose(tag: String?, msg: String?) {}
            override fun logStackTraceWithMessage(tag: String?, msg: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
        
        val dummySession = try {
            val s = TerminalSession("/system/bin/cat", "", arrayOf(), arrayOf(), 100, client)
            s
        } catch (e: Throwable) {
            null
        }

        dummySession?.emulator?.append(initialText.toByteArray(), initialText.length)
        dummySession
    }

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

    DisposableEffect(Unit) {
        SshSessionProvider.onOutputReceived = { bytes, length ->
            session.emulator?.append(bytes, length)
            terminalViewRef?.onScreenUpdated()
            Log.d("TerminalScreen", "Appended ${length} bytes from SSH PTY stdout")
        }
        onDispose {
            SshSessionProvider.onOutputReceived = null
        }
    }

    // 0 = none, 1 = keyboard only, 2 = keyboard + buttons
    var terminalInputState by remember { mutableStateOf(0) }
    val ctrlSticky = remember { mutableStateOf(false) }
    val altSticky = remember { mutableStateOf(false) }
    val superSticky = remember { mutableStateOf(false) }
    val menuSticky = remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = terminalInputState != 0) {
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
    }

    val sendToTerminal: (ByteArray) -> Unit = { bytes ->
        try {
            var finalBytes = bytes
            if (altSticky.value && bytes.size == 1) {
                finalBytes = byteArrayOf(0x1B) + finalBytes
                altSticky.value = false
            }
            SshSessionProvider.ptyOutputStream?.write(finalBytes)
            SshSessionProvider.ptyOutputStream?.flush()
        } catch (ex: Exception) {
            Log.e("TerminalScreen", "Failed to write to SSH PTY", ex)
        }
    }

    var currentFontSize by remember { mutableStateOf(14) }

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxSize().imePadding()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().onKeyEvent { keyEvent ->
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
                        terminalInputState = (terminalInputState + 1) % 3
                        terminalView.requestFocus()
                        val window = (context as? android.app.Activity)?.window
                        if (window != null) {
                            val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, terminalView)
                            if (terminalInputState == 0) {
                                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                                terminalView.clearFocus()
                            } else {
                                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.ime())
                            }
                        } else {
                            // Fallback to InputMethodManager
                            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            if (terminalInputState == 0) {
                                imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
                            } else {
                                imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
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

                terminalView.attachSession(session)
                terminalViewRef = terminalView
                terminalView
            }
        )
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

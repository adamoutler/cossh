package com.adamoutler.ssh.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            var currentFontSize = 14
            val terminalView = TerminalView(context, null)
            terminalView.setTextSize(currentFontSize)
            terminalView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            terminalView.setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                override fun onScale(scale: Float): Float = scale
                
                override fun onSingleTapUp(e: android.view.MotionEvent?) {
                    terminalView.requestFocus()
                    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
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
                        try {
                            SshSessionProvider.ptyOutputStream?.write(bytesToSend)
                            SshSessionProvider.ptyOutputStream?.flush()
                            Log.d("TerminalScreen", "Wrote ${bytesToSend.size} bytes (key: $keyCode) to SSH PTY stdin")
                        } catch (ex: Exception) {
                            Log.e("TerminalScreen", "Failed to write to SSH PTY", ex)
                        }
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
                        val chars = Character.toChars(codePoint)
                        val bytes = String(chars).toByteArray(Charsets.UTF_8)
                        SshSessionProvider.ptyOutputStream?.write(bytes)
                        SshSessionProvider.ptyOutputStream?.flush()
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
}

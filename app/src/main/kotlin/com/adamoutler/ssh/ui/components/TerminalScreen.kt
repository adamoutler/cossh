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
    if (LocalInspectionMode.current || android.os.Build.FINGERPRINT.startsWith("robolectric")) {
        // Fallback for Paparazzi, Compose Previews, or if JNI fails to load
        Box(modifier = modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
            Text(
                text = initialText,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val session = remember {
        val client = object : TerminalSessionClient {
            override fun onTextChanged(session: TerminalSession) {}
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
            val s = TerminalSession("sh", "", arrayOf(), arrayOf(), 100, client)
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
            Log.d("TerminalScreen", "Appended ${length} bytes from SSH PTY stdout")
        }
        onDispose {
            SshSessionProvider.onOutputReceived = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val terminalView = TerminalView(context, null)
            terminalView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            terminalView.setTerminalViewClient(object : com.termux.view.TerminalViewClient {
                override fun onScale(scale: Float): Float = scale
                override fun onSingleTapUp(e: android.view.MotionEvent?) {}
                override fun shouldBackButtonBeMappedToEscape(): Boolean = false
                override fun shouldEnforceCharBasedInput(): Boolean = false
                override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
                override fun isTerminalViewSelected(): Boolean = true
                override fun copyModeChanged(b: Boolean) {}
                override fun onKeyDown(keyCode: Int, e: android.view.KeyEvent?, session: TerminalSession?): Boolean {
                    if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && e?.action == android.view.KeyEvent.ACTION_DOWN) {
                        try {
                            SshSessionProvider.ptyOutputStream?.write("\r".toByteArray())
                            SshSessionProvider.ptyOutputStream?.flush()
                            Log.d("TerminalScreen", "Wrote Enter key to SSH PTY stdin")
                            return true
                        } catch (ex: Exception) {
                            Log.e("TerminalScreen", "Failed to write to SSH PTY", ex)
                        }
                    }
                    return false
                }
                override fun onKeyUp(keyCode: Int, e: android.view.KeyEvent?): Boolean = false
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
                        return true
                    } catch (ex: Exception) {
                        Log.e("TerminalScreen", "Failed to write codePoint to SSH PTY", ex)
                    }
                    return false
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
            terminalView
        }
    )
}

package com.adamoutler.ssh.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier,
    initialText: String = "Welcome to CoSSH Terminal\r\n\u001B[32mANSI Color Support Active!\u001B[0m\r\n"
) {
    if (LocalInspectionMode.current) {
        // Fallback for Paparazzi and Compose Previews (JNI is unavailable)
        Box(modifier = modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("Welcome to CoSSH Terminal\n")
                    withStyle(style = SpanStyle(color = Color.Green)) {
                        append("ANSI Color Support Active!\n")
                    }
                },
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
            TerminalSession("sh", "", arrayOf(), arrayOf(), 100, client)
        } catch (e: Throwable) {
            null
        }

        dummySession?.emulator?.append(initialText.toByteArray(), initialText.length)
        dummySession
    }

    if (session == null || LocalInspectionMode.current || android.os.Build.FINGERPRINT.startsWith("robolectric")) {
        // Fallback for Paparazzi, Compose Previews, or if JNI fails to load
        Box(modifier = modifier.fillMaxSize().background(Color.Black).padding(4.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("Welcome to CoSSH Terminal\n")
                    withStyle(style = SpanStyle(color = Color.Green)) {
                        append("ANSI Color Support Active!\n")
                    }
                },
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val terminalView = TerminalView(context, null)
            terminalView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            if (session != null) {
                terminalView.attachSession(session)
            }
            terminalView
        }
    )
}

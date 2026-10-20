package com.adamoutler.ssh.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SecureClipboardManager {
    private const val TAG = "SecureClipboardManager"
    const val AUTO_CLEAR_DELAY_MS = 30_000L

    var autoClearJob: Job? = null
        private set

    private var lastCopiedText: String? = null

    @Synchronized
    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "Terminal text",
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        autoClearDelayMs: Long = AUTO_CLEAR_DELAY_MS,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            AppLog.w(TAG, "ClipboardManager system service not available")
            return
        }

        val trimmedText = text.trimEnd()
        lastCopiedText = trimmedText

        val clip = ClipData.newPlainText(label, trimmedText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
        AppLog.d(TAG, "Copied text to clipboard (sensitive flag set=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU})")

        // Cancel any pending auto-clear timer
        autoClearJob?.cancel()

        // Schedule new auto-clear coroutine
        autoClearJob = scope.launch {
            delay(autoClearDelayMs)
            clearIfMatching(clipboard, trimmedText)
        }
    }

    @Synchronized
    fun clearClipboard(context: Context) {
        autoClearJob?.cancel()
        autoClearJob = null
        lastCopiedText = null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clear(clipboard)
    }

    private fun clearIfMatching(clipboard: ClipboardManager, expectedText: String) {
        try {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val currentText = clip.getItemAt(0)?.text?.toString()
                if (currentText == expectedText) {
                    clear(clipboard)
                    AppLog.i(TAG, "Sensitive clipboard contents auto-cleared successfully after timeout.")
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to auto-clear clipboard: ${e.message}", e)
        }
    }

    private fun clear(clipboard: ClipboardManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

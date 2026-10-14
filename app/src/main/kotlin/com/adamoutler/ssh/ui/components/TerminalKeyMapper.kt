package com.adamoutler.ssh.ui.components

import android.view.KeyEvent

fun isBleedThroughEvent(e: KeyEvent?, connectionStartTime: Long): Boolean = TerminalKeyMapper.isBleedThroughEvent(e, connectionStartTime)

class TerminalKeyMapper(
    private val getCtrlState: () -> ModifierState = { ModifierState.INACTIVE },
    private val setCtrlState: (ModifierState) -> Unit = {},
    private val getAltState: () -> ModifierState = { ModifierState.INACTIVE },
    private val setAltState: (ModifierState) -> Unit = {},
    private val getSuperState: () -> ModifierState = { ModifierState.INACTIVE },
    private val setSuperState: (ModifierState) -> Unit = {},
    private val getMenuState: () -> ModifierState = { ModifierState.INACTIVE },
    private val setMenuState: (ModifierState) -> Unit = {},
    private val sendRawBytes: (ByteArray) -> Unit = {},
    private val isInputLocked: () -> Boolean = { false },
    private val getConnectionStartTime: () -> Long = { 0L },
    private val onVolumeUp: () -> Unit = {},
    private val onVolumeDown: () -> Unit = {},
) {
    fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean {
        if (e?.action != KeyEvent.ACTION_DOWN) return false
        if (keyCode == KeyEvent.KEYCODE_BACK) return false

        // Prevent bleed-through key events from previous screens (like hitting Enter to connect)
        if (isBleedThroughEvent(e, getConnectionStartTime())) {
            return true // Consume it so it doesn't propagate
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            onVolumeUp()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            onVolumeDown()
            return true
        }

        val ctrlActive = getCtrlState().isActive || (e.isCtrlPressed)
        val bytesToSend = mapKeyCode(keyCode, e, ctrlActive)

        if (bytesToSend != null) {
            consumeStickyModifiers()
            sendToTerminal(bytesToSend)
        }
        return true
    }

    fun onKeyUp(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return false
        return true
    }

    fun readControlKey(): Boolean = getCtrlState().isActive
    fun readAltKey(): Boolean = getAltState().isActive
    fun readShiftKey(): Boolean = false
    fun readFnKey(): Boolean = false

    fun onCodePoint(codePoint: Int, ctrlDown: Boolean = false): Boolean {
        val connStart = getConnectionStartTime()
        if (connStart > 0L && android.os.SystemClock.uptimeMillis() < connStart + 500) {
            return true // Consume it so it doesn't propagate
        }
        try {
            val (bytes, appliedCtrl) = mapCodePoint(codePoint, getCtrlState().isActive || ctrlDown)
            if (appliedCtrl && getCtrlState() == ModifierState.STICKY) {
                setCtrlState(ModifierState.INACTIVE)
            }
            if (getSuperState() == ModifierState.STICKY) setSuperState(ModifierState.INACTIVE)
            if (getMenuState() == ModifierState.STICKY) setMenuState(ModifierState.INACTIVE)
            sendToTerminal(bytes)
        } catch (_: Exception) {
            // Ignored on hot path
        }
        return true
    }

    fun onExtraKeyToggle(key: String) {
        when (key) {
            "Ctrl" -> setCtrlState(getCtrlState().next())
            "Alt" -> setAltState(getAltState().next())
            "Super" -> setSuperState(getSuperState().next())
            "Menu" -> setMenuState(getMenuState().next())
        }
    }

    fun onExtraKeyPress(key: String) {
        val bytes = mapExtraKey(key)
        if (bytes != null) {
            consumeStickyModifiers()
            sendToTerminal(bytes)
        }
    }

    fun processBytesWithAlt(bytes: ByteArray): ByteArray {
        val currentAlt = getAltState()
        if (currentAlt.isActive && bytes.size == 1) {
            val finalBytes = applyAltModifier(bytes, true)
            if (currentAlt == ModifierState.STICKY) {
                setAltState(ModifierState.INACTIVE)
            }
            return finalBytes
        }
        return bytes
    }

    fun sendToTerminal(bytes: ByteArray) {
        if (isInputLocked()) {
            return
        }
        val finalBytes = processBytesWithAlt(bytes)
        sendRawBytes(finalBytes)
    }

    fun consumeStickyModifiers() {
        if (getCtrlState() == ModifierState.STICKY) setCtrlState(ModifierState.INACTIVE)
        if (getSuperState() == ModifierState.STICKY) setSuperState(ModifierState.INACTIVE)
        if (getMenuState() == ModifierState.STICKY) setMenuState(ModifierState.INACTIVE)
    }

    companion object {
        fun isBleedThroughEvent(e: KeyEvent?, connectionStartTime: Long): Boolean {
            if (e == null) return false
            if (connectionStartTime <= 0L) return false
            return e.downTime < connectionStartTime + 500
        }

        fun applyCtrlModifier(codePoint: Int): Int {
            var cp = codePoint
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
            return cp
        }

        fun applyAltModifier(bytes: ByteArray, altActive: Boolean): ByteArray = if (altActive && bytes.size == 1) {
            byteArrayOf(0x1B) + bytes
        } else {
            bytes
        }

        fun consumeSticky(state: ModifierState): ModifierState = if (state == ModifierState.STICKY) ModifierState.INACTIVE else state

        fun mapExtraKey(key: String): ByteArray? = when (key) {
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

        fun mapKeyCode(keyCode: Int, e: KeyEvent?, ctrlActive: Boolean): ByteArray? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r".toByteArray()

            KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)

            KeyEvent.KEYCODE_TAB -> "\t".toByteArray()

            KeyEvent.KEYCODE_DPAD_UP -> byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte())

            KeyEvent.KEYCODE_DPAD_DOWN -> byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte())

            KeyEvent.KEYCODE_DPAD_RIGHT -> byteArrayOf(0x1B, '['.code.toByte(), 'C'.code.toByte())

            KeyEvent.KEYCODE_DPAD_LEFT -> byteArrayOf(0x1B, '['.code.toByte(), 'D'.code.toByte())

            else -> {
                val unicodeChar = e?.unicodeChar ?: 0
                if (unicodeChar != 0) {
                    val cp = if (ctrlActive) applyCtrlModifier(unicodeChar) else unicodeChar
                    String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
                } else {
                    null
                }
            }
        }

        fun mapCodePoint(codePoint: Int, ctrlActive: Boolean): Pair<ByteArray, Boolean> {
            var cp = codePoint
            if (cp == 10) cp = 13 // Replace \n with \r
            var appliedCtrl = false
            if (ctrlActive) {
                cp = applyCtrlModifier(cp)
                appliedCtrl = true
            }
            val chars = Character.toChars(cp)
            return Pair(String(chars).toByteArray(Charsets.UTF_8), appliedCtrl)
        }
    }
}

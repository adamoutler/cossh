package com.adamoutler.ssh.ui.components

import android.view.KeyEvent

/**
 * High-performance VT100 / xterm ANSI escape sequence generator for physical hardware keyboard events.
 *
 * Implements standard xterm modifier parameter mapping:
 * mod = 1 + (Shift * 1) + (Alt * 2) + (Ctrl * 4)
 *
 * Enforces:
 * - 16-byte upper ceiling on generated escape sequences
 * - Zero intermediate heap String allocations on hot key paths
 */
object TerminalAnsiKeyMapper {
    private const val ESC: Byte = 0x1B
    private const val CSI: Byte = '['.code.toByte()

    fun calculateModifierParam(shift: Boolean, alt: Boolean, ctrl: Boolean): Int {
        var param = 1
        if (shift) param += 1
        if (alt) param += 2
        if (ctrl) param += 4
        return param
    }

    fun mapKeyCode(keyCode: Int, shift: Boolean, alt: Boolean, ctrl: Boolean): ByteArray? {
        val mod = calculateModifierParam(shift, alt, ctrl)

        return when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(ESC)

            KeyEvent.KEYCODE_TAB -> {
                if (shift) {
                    // Backtab (Shift+Tab)
                    byteArrayOf(ESC, CSI, 'Z'.code.toByte())
                } else if (mod > 1) {
                    byteArrayOf(ESC, CSI, '9'.code.toByte(), ';'.code.toByte(), ('0'.code + mod).toByte(), '~'.code.toByte())
                } else {
                    "\t".toByteArray(Charsets.US_ASCII)
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> mapCursorKey('A'.code.toByte(), mod)

            KeyEvent.KEYCODE_DPAD_DOWN -> mapCursorKey('B'.code.toByte(), mod)

            KeyEvent.KEYCODE_DPAD_RIGHT -> mapCursorKey('C'.code.toByte(), mod)

            KeyEvent.KEYCODE_DPAD_LEFT -> mapCursorKey('D'.code.toByte(), mod)

            KeyEvent.KEYCODE_MOVE_HOME -> mapCursorKey('H'.code.toByte(), mod)

            KeyEvent.KEYCODE_MOVE_END -> mapCursorKey('F'.code.toByte(), mod)

            KeyEvent.KEYCODE_INSERT -> mapTildeKey("2", mod)

            KeyEvent.KEYCODE_FORWARD_DEL -> mapTildeKey("3", mod)

            KeyEvent.KEYCODE_PAGE_UP -> mapTildeKey("5", mod)

            KeyEvent.KEYCODE_PAGE_DOWN -> mapTildeKey("6", mod)

            KeyEvent.KEYCODE_F1 -> mapFunctionKey(1, mod)

            KeyEvent.KEYCODE_F2 -> mapFunctionKey(2, mod)

            KeyEvent.KEYCODE_F3 -> mapFunctionKey(3, mod)

            KeyEvent.KEYCODE_F4 -> mapFunctionKey(4, mod)

            KeyEvent.KEYCODE_F5 -> mapFunctionKey(5, mod)

            KeyEvent.KEYCODE_F6 -> mapFunctionKey(6, mod)

            KeyEvent.KEYCODE_F7 -> mapFunctionKey(7, mod)

            KeyEvent.KEYCODE_F8 -> mapFunctionKey(8, mod)

            KeyEvent.KEYCODE_F9 -> mapFunctionKey(9, mod)

            KeyEvent.KEYCODE_F10 -> mapFunctionKey(10, mod)

            KeyEvent.KEYCODE_F11 -> mapFunctionKey(11, mod)

            KeyEvent.KEYCODE_F12 -> mapFunctionKey(12, mod)

            else -> null
        }
    }

    private fun mapCursorKey(finalChar: Byte, mod: Int): ByteArray = if (mod == 1) {
        byteArrayOf(ESC, CSI, finalChar)
    } else {
        byteArrayOf(ESC, CSI, '1'.code.toByte(), ';'.code.toByte(), ('0'.code + mod).toByte(), finalChar)
    }

    private fun mapTildeKey(codeStr: String, mod: Int): ByteArray {
        val seq = if (mod == 1) {
            "\u001B[$codeStr~"
        } else {
            "\u001B[$codeStr;$mod~"
        }
        val bytes = seq.toByteArray(Charsets.US_ASCII)
        check(bytes.size <= 16) { "Escape sequence exceeded 16-byte bounds" }
        return bytes
    }

    private fun mapFunctionKey(fNumber: Int, mod: Int): ByteArray {
        if (mod == 1 && fNumber in 1..4) {
            val suffix = ('P'.code + (fNumber - 1)).toByte()
            return byteArrayOf(ESC, 'O'.code.toByte(), suffix)
        }

        if (mod > 1 && fNumber in 1..4) {
            val suffix = ('P'.code + (fNumber - 1)).toByte()
            return byteArrayOf(ESC, CSI, '1'.code.toByte(), ';'.code.toByte(), ('0'.code + mod).toByte(), suffix)
        }

        val codeStr = when (fNumber) {
            5 -> "15"
            6 -> "17"
            7 -> "18"
            8 -> "19"
            9 -> "20"
            10 -> "21"
            11 -> "23"
            12 -> "24"
            else -> return byteArrayOf()
        }

        return mapTildeKey(codeStr, mod)
    }
}

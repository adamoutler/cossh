package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite validating tmux prefix configurations, command enumerations,
 * and byte sequence building for terminal multiplexer interaction.
 */
class TmuxActionHelperTest {

    @Test
    fun testTmuxPrefixConstants() {
        assertEquals(0x02.toByte(), TmuxPrefix.CTRL_B.byte)
        assertEquals("Ctrl+B", TmuxPrefix.CTRL_B.displayLabel)

        assertEquals(0x01.toByte(), TmuxPrefix.CTRL_A.byte)
        assertEquals("Ctrl+A", TmuxPrefix.CTRL_A.displayLabel)
    }

    @Test
    fun testAllRequiredCommandsExist() {
        val required = listOf(
            TmuxCommand.SPLIT_HORIZONTAL,
            TmuxCommand.SPLIT_VERTICAL,
            TmuxCommand.PANE_UP,
            TmuxCommand.PANE_DOWN,
            TmuxCommand.PANE_LEFT,
            TmuxCommand.PANE_RIGHT,
            TmuxCommand.ZOOM_TOGGLE,
            TmuxCommand.NEW_WINDOW,
            TmuxCommand.NEXT_WINDOW,
            TmuxCommand.PREV_WINDOW,
            TmuxCommand.DETACH_SESSION,
            TmuxCommand.KILL_PANE,
        )
        for (cmd in required) {
            assertNotNull(cmd.displayName)
            assertNotNull(cmd.description)
            assertNotNull(cmd.keySequenceHint)
            assertNotNull(cmd.suffixBytes)
        }
    }

    @Test
    fun testBuildCommandBytesWithDefaultPrefix() {
        // Horizontal split: Ctrl-B "
        assertArrayEquals(
            byteArrayOf(0x02, '"'.code.toByte()),
            buildCommandBytes(TmuxCommand.SPLIT_HORIZONTAL),
        )

        // Vertical split: Ctrl-B %
        assertArrayEquals(
            byteArrayOf(0x02, '%'.code.toByte()),
            buildCommandBytes(TmuxCommand.SPLIT_VERTICAL),
        )

        // Zoom toggle: Ctrl-B z
        assertArrayEquals(
            byteArrayOf(0x02, 'z'.code.toByte()),
            buildCommandBytes(TmuxCommand.ZOOM_TOGGLE),
        )

        // New window: Ctrl-B c
        assertArrayEquals(
            byteArrayOf(0x02, 'c'.code.toByte()),
            buildCommandBytes(TmuxCommand.NEW_WINDOW),
        )

        // Next window: Ctrl-B n
        assertArrayEquals(
            byteArrayOf(0x02, 'n'.code.toByte()),
            buildCommandBytes(TmuxCommand.NEXT_WINDOW),
        )

        // Prev window: Ctrl-B p
        assertArrayEquals(
            byteArrayOf(0x02, 'p'.code.toByte()),
            buildCommandBytes(TmuxCommand.PREV_WINDOW),
        )

        // Detach session: Ctrl-B d
        assertArrayEquals(
            byteArrayOf(0x02, 'd'.code.toByte()),
            buildCommandBytes(TmuxCommand.DETACH_SESSION),
        )

        // Kill pane: Ctrl-B x
        assertArrayEquals(
            byteArrayOf(0x02, 'x'.code.toByte()),
            buildCommandBytes(TmuxCommand.KILL_PANE),
        )
    }

    @Test
    fun testBuildCommandBytesArrowKeys() {
        // Up: Ctrl-B ESC [ A
        assertArrayEquals(
            byteArrayOf(0x02, 0x1B, '['.code.toByte(), 'A'.code.toByte()),
            buildCommandBytes(TmuxCommand.PANE_UP),
        )

        // Down: Ctrl-B ESC [ B
        assertArrayEquals(
            byteArrayOf(0x02, 0x1B, '['.code.toByte(), 'B'.code.toByte()),
            buildCommandBytes(TmuxCommand.PANE_DOWN),
        )

        // Left: Ctrl-B ESC [ D
        assertArrayEquals(
            byteArrayOf(0x02, 0x1B, '['.code.toByte(), 'D'.code.toByte()),
            buildCommandBytes(TmuxCommand.PANE_LEFT),
        )

        // Right: Ctrl-B ESC [ C
        assertArrayEquals(
            byteArrayOf(0x02, 0x1B, '['.code.toByte(), 'C'.code.toByte()),
            buildCommandBytes(TmuxCommand.PANE_RIGHT),
        )
    }

    @Test
    fun testBuildCommandBytesWithCtrlAPrefix() {
        // Zoom toggle: Ctrl-A z
        assertArrayEquals(
            byteArrayOf(0x01, 'z'.code.toByte()),
            buildCommandBytes(TmuxCommand.ZOOM_TOGGLE, TmuxPrefix.CTRL_A),
        )

        // Pane Up: Ctrl-A ESC [ A
        assertArrayEquals(
            byteArrayOf(0x01, 0x1B, '['.code.toByte(), 'A'.code.toByte()),
            buildCommandBytes(TmuxCommand.PANE_UP, TmuxPrefix.CTRL_A),
        )
    }

    @Test
    fun testBuildCommandPayloadString() {
        val payload = TmuxActionHelper.buildCommandPayload(TmuxCommand.ZOOM_TOGGLE, TmuxPrefix.CTRL_B)
        assertEquals(2, payload.length)
        assertEquals(0x02.toChar(), payload[0])
        assertEquals('z', payload[1])
    }

    @Test
    fun testFindCommand() {
        assertEquals(TmuxCommand.SPLIT_HORIZONTAL, TmuxActionHelper.findCommand("SPLIT_HORIZONTAL"))
        assertEquals(TmuxCommand.SPLIT_VERTICAL, TmuxActionHelper.findCommand("split_vertical"))
        assertEquals(TmuxCommand.ZOOM_TOGGLE, TmuxActionHelper.findCommand("zoom_toggle"))
        assertNull(TmuxActionHelper.findCommand("non_existent_command"))
    }
}

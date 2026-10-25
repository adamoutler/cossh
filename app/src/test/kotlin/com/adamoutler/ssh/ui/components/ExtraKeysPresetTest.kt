package com.adamoutler.ssh.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite validating extra key presets, layout structures,
 * key spec bounds validation, JSON roundtrip serialization, and
 * accessibility helper transformations.
 */
class ExtraKeysPresetTest {

    @Test
    fun testAllPresetIdsReturnValidStructuredRows() {
        for (presetId in ExtraKeysPresetId.values()) {
            val preset = ExtraKeysPresets.getPreset(presetId)
            assertNotNull("Preset for $presetId should not be null", preset)
            assertTrue("Preset for $presetId should have at least 2 rows", preset.size >= 2)
            assertTrue("Preset for $presetId must pass validatePreset", ExtraKeysPresets.validatePreset(preset))
        }
    }

    @Test
    fun testDefaultPresetContainsRequiredKeys() {
        val preset = ExtraKeysPresets.getPreset(ExtraKeysPresetId.DEFAULT)
        val allKeys = preset.flatten()
        val labels = allKeys.map { it.label }.toSet()

        val expected = setOf("Esc", "Ctrl-C", "Menu", "↑", "Tab", "Home", "Ctrl", "Alt", "←", "↓", "→", "End")
        for (exp in expected) {
            assertTrue("DEFAULT preset missing key: $exp", labels.contains(exp))
        }
    }

    @Test
    fun testVimCodingPresetContainsRequiredKeys() {
        val preset = ExtraKeysPresets.getPreset(ExtraKeysPresetId.VIM_CODING)
        val allKeys = preset.flatten()
        val labels = allKeys.map { it.label }.toSet()

        val required = setOf("Esc", ":", "/", "|", "~", "Tab", "{", "}")
        for (req in required) {
            assertTrue("VIM_CODING preset missing required key: $req", labels.contains(req))
        }
    }

    @Test
    fun testTmuxPresetContainsRequiredKeys() {
        val preset = ExtraKeysPresets.getPreset(ExtraKeysPresetId.TMUX)
        val allKeys = preset.flatten()
        val labels = allKeys.map { it.label }.toSet()

        val required = setOf("Ctrl-B", "%", "\"", "z", "c", "n", "p", "d")
        for (req in required) {
            assertTrue("TMUX preset missing required key: $req", labels.contains(req))
        }
    }

    @Test
    fun testNavigationPresetContainsRequiredKeys() {
        val preset = ExtraKeysPresets.getPreset(ExtraKeysPresetId.NAVIGATION)
        val allKeys = preset.flatten()
        val labels = allKeys.map { it.label }.toSet()

        val required = setOf("Home", "End", "PgUp", "PgDn", "↑", "↓", "←", "→")
        for (req in required) {
            assertTrue("NAVIGATION preset missing required key: $req", labels.contains(req))
        }
    }

    @Test
    fun testValidateKeySpec_validKeySucceeds() {
        val validKey = ExtraKeySpec(
            id = "custom_1",
            label = "F1",
            contentDescription = "Function Key 1",
            payload = "\u001bOP",
            isModifier = false,
            isRepeatable = false,
        )
        assertTrue(ExtraKeysPresets.validateKeySpec(validKey))
    }

    @Test
    fun testValidateKeySpec_rejectsInvalidKeys() {
        // Blank id
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("", "F1", "F1", "payload")))
        // Invalid chars in id
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("invalid id!", "F1", "F1", "payload")))
        // Blank label
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("f1", "", "F1", "payload")))
        // Blank content description
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("f1", "F1", "", "payload")))
        // Empty payload for non-modifier
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("f1", "F1", "F1", "", isModifier = false)))
        // Overly long payload (> 128 chars)
        assertFalse(ExtraKeysPresets.validateKeySpec(ExtraKeySpec("f1", "F1", "F1", "a".repeat(129))))
    }

    @Test
    fun testValidatePreset_rejectsDuplicatesAndMalformedRows() {
        // Empty rows
        assertFalse(ExtraKeysPresets.validatePreset(emptyList()))
        // Empty row inside preset
        assertFalse(ExtraKeysPresets.validatePreset(listOf(emptyList())))
        // Duplicate key IDs
        val dupRow = listOf(
            listOf(
                ExtraKeySpec("key_1", "K1", "Desc", "val1"),
                ExtraKeySpec("key_1", "K2", "Desc", "val2"),
            ),
        )
        assertFalse(ExtraKeysPresets.validatePreset(dupRow))
    }

    @Test
    fun testSerializationRoundTrip() {
        for (presetId in ExtraKeysPresetId.values()) {
            val original = ExtraKeysPresets.getPreset(presetId)
            val jsonStr = ExtraKeysPresets.serializePreset(original)
            assertNotNull(jsonStr)
            assertTrue(jsonStr.isNotEmpty())

            val deserialized = ExtraKeysPresets.deserializePreset(jsonStr)
            assertEquals("Roundtrip preset for $presetId must match original", original, deserialized)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeserializePreset_rejectsMalformedJson() {
        ExtraKeysPresets.deserializePreset("{ invalid: json }")
    }

    @Test
    fun testTerminalAccessibilityHelper_stripAnsiCodes() {
        val raw = "\u001B[31;1mError: Connection failed\u001B[0m\r\n\u001B]0;Terminal Title\u0007Done."
        val stripped = TerminalAccessibilityHelper.stripAnsiControlCodes(raw)
        assertEquals("Error: Connection failed\nDone.", stripped)
    }

    @Test
    fun testTerminalAccessibilityHelper_extractAccessibleLines() {
        val transcript = (1..100).joinToString("\n") { "Line $it \u001B[32m[OK]\u001B[0m" }
        val lines = TerminalAccessibilityHelper.extractAccessibleLines(transcript, maxLines = 10)
        assertEquals(10, lines.size)
        assertEquals("Line 91 [OK]", lines.first())
        assertEquals("Line 100 [OK]", lines.last())
    }

    @Test
    fun testTerminalAccessibilityHelper_modifierAnnouncements() {
        val ctrlInactive = TerminalAccessibilityHelper.buildModifierAnnouncement("Ctrl", ModifierState.INACTIVE)
        val ctrlSticky = TerminalAccessibilityHelper.buildModifierAnnouncement("Ctrl", ModifierState.STICKY)
        val ctrlLocked = TerminalAccessibilityHelper.buildModifierAnnouncement("Ctrl", ModifierState.LOCKED)

        assertTrue(ctrlInactive.contains("Ctrl") && ctrlInactive.contains("Active: Off"))
        assertTrue(ctrlSticky.contains("Ctrl") && ctrlSticky.contains("Sticky"))
        assertTrue(ctrlLocked.contains("Ctrl") && ctrlLocked.contains("Locked"))
    }

    @Test
    fun testTerminalAccessibilityHelper_outputAnnouncement() {
        val shortOutput = "\u001B[33mWarning: low disk space\u001B[0m"
        val announcement = TerminalAccessibilityHelper.buildOutputAnnouncement(shortOutput, maxChars = 50)
        assertEquals("Warning: low disk space", announcement)

        val longOutput = "A".repeat(300)
        val truncated = TerminalAccessibilityHelper.buildOutputAnnouncement(longOutput, maxChars = 50)
        assertEquals(51, truncated.length) // 50 chars + ellipsis
        assertTrue(truncated.endsWith("…"))
    }
}

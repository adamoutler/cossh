package com.adamoutler.ssh.ui.components

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SettingsManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExtraKeysPresetIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsManager_extraKeysPresetDefaultAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = SettingsManager(context)

        assertEquals("DEFAULT", settings.globalExtraKeysPreset)
        assertEquals(ExtraKeysPresetId.DEFAULT, settings.extraKeysPreset)

        settings.extraKeysPreset = ExtraKeysPresetId.VIM_CODING
        assertEquals("VIM_CODING", settings.globalExtraKeysPreset)
        assertEquals(ExtraKeysPresetId.VIM_CODING, settings.extraKeysPreset)

        settings.globalExtraKeysPreset = "INVALID_UNKNOWN_PRESET"
        assertEquals(ExtraKeysPresetId.DEFAULT, settings.extraKeysPreset)
    }

    @Test
    fun terminalKeyMapper_onExtraKeyPress_handlesFallbackUtf8() {
        var sentBytes: ByteArray? = null
        val mapper = TerminalKeyMapper(
            sendRawBytes = { sentBytes = it },
        )

        // Standard mapped key
        mapper.onExtraKeyPress("Esc")
        assertArrayEquals(byteArrayOf(0x1B), sentBytes)

        // Custom unmapped key (e.g. Vim colon)
        mapper.onExtraKeyPress(":")
        assertArrayEquals(":".toByteArray(Charsets.UTF_8), sentBytes)

        // Custom multi-character fallback
        mapper.onExtraKeyPress("git status")
        assertArrayEquals("git status".toByteArray(Charsets.UTF_8), sentBytes)
    }

    @Test
    fun terminalKeyMapper_onKeySpecPress_handlesModifiersAndPayload() {
        var ctrlState = ModifierState.INACTIVE
        var sentBytes: ByteArray? = null

        val mapper = TerminalKeyMapper(
            getCtrlState = { ctrlState },
            setCtrlState = { ctrlState = it },
            sendRawBytes = { sentBytes = it },
        )

        val modifierSpec = ExtraKeySpec(
            id = "ctrl",
            label = "Ctrl",
            contentDescription = "Control modifier",
            payload = "",
            isModifier = true,
        )
        mapper.onKeySpecPress(modifierSpec)
        assertEquals(ModifierState.STICKY, ctrlState)

        val vimColonSpec = ExtraKeySpec(
            id = "vim_colon",
            label = ":",
            contentDescription = "Colon command prompt",
            payload = ":",
            isModifier = false,
        )
        mapper.onKeySpecPress(vimColonSpec)
        assertArrayEquals(":".toByteArray(Charsets.UTF_8), sentBytes)
        // Sticky modifier should be consumed
        assertEquals(ModifierState.INACTIVE, ctrlState)

        val tmuxPrefixSpec = ExtraKeySpec(
            id = "tmux_prefix",
            label = "Ctrl-B",
            contentDescription = "Tmux prefix",
            payload = "\u0002",
            isModifier = false,
        )
        mapper.onKeySpecPress(tmuxPrefixSpec)
        assertArrayEquals(byteArrayOf(0x02), sentBytes)
    }

    @Test
    fun terminalExtraKeys_rendersVimPresetWithA11yDescriptions() {
        var pressedSpec: ExtraKeySpec? = null

        composeTestRule.setContent {
            TerminalExtraKeys(
                ctrlState = ModifierState.INACTIVE,
                altState = ModifierState.INACTIVE,
                superState = ModifierState.INACTIVE,
                menuState = ModifierState.INACTIVE,
                onKeyToggle = {},
                onKeyPress = {},
                presetId = ExtraKeysPresetId.VIM_CODING,
                onKeySpecPress = { pressedSpec = it },
            )
        }

        // Verify Vim-specific keys are present
        composeTestRule.onNodeWithText(":").assertExists()
        composeTestRule.onNodeWithText("/").assertExists()
        composeTestRule.onNodeWithText("{").assertExists()
        composeTestRule.onNodeWithText("}").assertExists()

        // Verify accessibility TalkBack contentDescription
        composeTestRule.onNodeWithText(":")
            .assertContentDescriptionEquals("Colon command prompt")

        // Perform click and verify onKeySpecPress callback
        composeTestRule.onNodeWithText(":").performClick()
        assertEquals("vim_colon", pressedSpec?.id)
        assertEquals(":", pressedSpec?.payload)
    }

    @Test
    fun terminalExtraKeys_rendersTmuxPreset() {
        var pressedSpec: ExtraKeySpec? = null

        composeTestRule.setContent {
            TerminalExtraKeys(
                ctrlState = ModifierState.INACTIVE,
                altState = ModifierState.INACTIVE,
                superState = ModifierState.INACTIVE,
                menuState = ModifierState.INACTIVE,
                onKeyToggle = {},
                onKeyPress = {},
                presetId = ExtraKeysPresetId.TMUX,
                onKeySpecPress = { pressedSpec = it },
            )
        }

        composeTestRule.onNodeWithText("Ctrl-B").assertExists()
        composeTestRule.onNodeWithText("%").assertExists()
        composeTestRule.onNodeWithText("\"").assertExists()

        composeTestRule.onNodeWithText("Ctrl-B").performClick()
        assertEquals("tmux_prefix", pressedSpec?.id)
        assertEquals("\u0002", pressedSpec?.payload)
    }
}

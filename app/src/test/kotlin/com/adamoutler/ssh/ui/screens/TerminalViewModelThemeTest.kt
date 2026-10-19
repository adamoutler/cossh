package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class TerminalViewModelThemeTest {

    private lateinit var application: Application
    private lateinit var viewModel: TerminalViewModel
    private lateinit var storageManager: SecurityStorageManager
    private lateinit var settingsManager: SettingsManager
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        storageManager = SecurityStorageManager(application)
        settingsManager = SettingsManager(application)
        viewModel = TerminalViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initTheme uses profile theme when profile has colorTheme`() {
        val profile = ConnectionProfile(
            id = "profile_monokai",
            nickname = "Monokai Server",
            host = "localhost",
            colorTheme = "MONOKAI",
        )
        storageManager.saveProfile(profile)

        viewModel.initTheme("profile_monokai")
        assertEquals(TerminalTheme.MONOKAI, viewModel.colorThemeFlow.value)
    }

    @Test
    fun `test initTheme falls back to globalColorTheme when profile theme is null`() {
        settingsManager.globalColorTheme = "SOLARIZED_DARK"
        val profile = ConnectionProfile(
            id = "profile_no_theme",
            nickname = "Default Theme Server",
            host = "localhost",
            colorTheme = null,
        )
        storageManager.saveProfile(profile)

        viewModel.initTheme("profile_no_theme")
        assertEquals(TerminalTheme.SOLARIZED_DARK, viewModel.colorThemeFlow.value)
    }

    @Test
    fun `test initTheme falls back to COBALT_BLUE for unknown theme string`() {
        val profile = ConnectionProfile(
            id = "profile_invalid_theme",
            nickname = "Invalid Theme Server",
            host = "localhost",
            colorTheme = "UNKNOWN_PALETTE_XYZ",
        )
        storageManager.saveProfile(profile)

        viewModel.initTheme("profile_invalid_theme")
        assertEquals(TerminalTheme.COBALT_BLUE, viewModel.colorThemeFlow.value)
    }

    @Test
    fun `test updateTheme updates flow, profile, and settingsManager`() = runTest {
        val profile = ConnectionProfile(
            id = "profile_theme_update",
            nickname = "Update Server",
            host = "localhost",
            colorTheme = "COBALT_BLUE",
        )
        storageManager.saveProfile(profile)

        viewModel.initTheme("profile_theme_update")
        viewModel.updateTheme(TerminalTheme.DRACULA)

        assertEquals(TerminalTheme.DRACULA, viewModel.colorThemeFlow.value)
        assertEquals("DRACULA", settingsManager.globalColorTheme)

        val updatedProfile = storageManager.getProfile("profile_theme_update")
        assertEquals("DRACULA", updatedProfile?.colorTheme)
    }

    @Test
    fun `test initFont uses profile font when profile has fontChoice`() {
        val profile = ConnectionProfile(
            id = "profile_fira",
            nickname = "Fira Server",
            host = "localhost",
            fontChoice = "FIRA_CODE",
        )
        storageManager.saveProfile(profile)

        viewModel.initFont("profile_fira")
        assertEquals(TerminalFontChoice.FIRA_CODE, viewModel.fontChoiceFlow.value)
    }

    @Test
    fun `test initFont falls back to globalFontChoice when profile font is null`() {
        settingsManager.globalFontChoice = "JETBRAINS_MONO"
        val profile = ConnectionProfile(
            id = "profile_no_font",
            nickname = "Default Font Server",
            host = "localhost",
            fontChoice = null,
        )
        storageManager.saveProfile(profile)

        viewModel.initFont("profile_no_font")
        assertEquals(TerminalFontChoice.JETBRAINS_MONO, viewModel.fontChoiceFlow.value)
    }

    @Test
    fun `test initFont falls back to DEFAULT_MONOSPACE for unknown font string`() {
        val profile = ConnectionProfile(
            id = "profile_invalid_font",
            nickname = "Invalid Font Server",
            host = "localhost",
            fontChoice = "UNKNOWN_FONT_XYZ",
        )
        storageManager.saveProfile(profile)

        viewModel.initFont("profile_invalid_font")
        assertEquals(TerminalFontChoice.DEFAULT_MONOSPACE, viewModel.fontChoiceFlow.value)
    }

    @Test
    fun `test updateFont updates flow, profile, and settingsManager`() = runTest {
        val profile = ConnectionProfile(
            id = "profile_font_update",
            nickname = "Font Update Server",
            host = "localhost",
            fontChoice = "DEFAULT_MONOSPACE",
        )
        storageManager.saveProfile(profile)

        viewModel.initFont("profile_font_update")
        viewModel.updateFont(TerminalFontChoice.SOURCE_CODE_PRO)

        assertEquals(TerminalFontChoice.SOURCE_CODE_PRO, viewModel.fontChoiceFlow.value)
        assertEquals("SOURCE_CODE_PRO", settingsManager.globalFontChoice)

        val updatedProfile = storageManager.getProfile("profile_font_update")
        assertEquals("SOURCE_CODE_PRO", updatedProfile?.fontChoice)
    }

    @Test
    fun `test all TerminalTheme entries have complete ANSI 16 palettes and colors`() {
        for (theme in TerminalTheme.entries) {
            assertNotNull(theme.id)
            assertNotNull(theme.displayName)
            assertEquals(16, theme.ansiColors.size)
            // Ensure background, foreground, cursor are non-zero
            org.junit.Assert.assertNotEquals(0, theme.background)
            org.junit.Assert.assertNotEquals(0, theme.foreground)
            org.junit.Assert.assertNotEquals(0, theme.cursor)
            // Test case-insensitive lookup
            assertEquals(theme, TerminalTheme.fromId(theme.id.lowercase()))
            assertEquals(theme, TerminalTheme.fromId(theme.name))
        }
    }

    @Test
    fun `test all TerminalFontChoice entries provide valid typefaces and compose font families`() {
        for (choice in TerminalFontChoice.entries) {
            assertNotNull(choice.id)
            assertNotNull(choice.displayName)
            assertNotNull(choice.systemFontName)
            val tf = choice.getTypeface()
            assertNotNull(tf)
            assertNotNull(choice.composeFontFamily)
            assertEquals(choice, TerminalFontChoice.fromId(choice.id.lowercase()))
            assertEquals(choice, TerminalFontChoice.fromId(choice.name))
        }
    }
}

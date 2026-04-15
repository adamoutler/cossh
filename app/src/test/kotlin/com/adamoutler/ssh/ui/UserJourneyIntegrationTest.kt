package com.adamoutler.ssh.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.ui.screens.AddEditProfileScreenContent
import com.adamoutler.ssh.ui.screens.ConnectionListScreenContent
import org.junit.Rule
import org.junit.Test

class UserJourneyIntegrationTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar"
    )

    @Test
    fun step1_InitialEmptyForm() {
        paparazzi.snapshot(name = "1_InitialEmptyForm") {
            CoSSHTheme {
                AddEditProfileScreenContent(
                    profileId = null,
                    nickname = "",
                    onNicknameChange = {},
                    host = "",
                    onHostChange = {},
                    port = "22",
                    onPortChange = {},
                    username = "",
                    onUsernameChange = {},
                    password = "",
                    onPasswordChange = {},
                    authType = AuthType.PASSWORD,
                    onAuthTypeChange = {},
                    availableKeys = emptyList(),
                    keyReference = "",
                    onKeyReferenceChange = {},
                    onSave = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    @Test
    fun step2_FormFilledOut() {
        paparazzi.snapshot(name = "2_FormFilledOut") {
            CoSSHTheme {
                AddEditProfileScreenContent(
                    profileId = null,
                    nickname = "My Test Server",
                    onNicknameChange = {},
                    host = "10.0.0.1",
                    onHostChange = {},
                    port = "22",
                    onPortChange = {},
                    username = "root",
                    onUsernameChange = {},
                    password = "secretpassword123",
                    onPasswordChange = {},
                    authType = AuthType.PASSWORD,
                    onAuthTypeChange = {},
                    availableKeys = emptyList(),
                    keyReference = "",
                    onKeyReferenceChange = {},
                    onSave = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    @Test
    fun step3_ConnectionListWithNewConnection() {
        val mockProfiles = listOf(
            ConnectionProfile(
                id = "test-uuid",
                nickname = "My Test Server",
                host = "10.0.0.1",
                port = 22,
                username = "root",
                authType = AuthType.PASSWORD,
                password = "secretpassword123".toByteArray()
            )
        )
        paparazzi.snapshot(name = "3_ConnectionListWithNewConnection") {
            CoSSHTheme {
                ConnectionListScreenContent(
                    profiles = mockProfiles,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onAddConnection = {},
                    onEditConnection = {},
                    onConnect = {}
                )
            }
        }
    }
}

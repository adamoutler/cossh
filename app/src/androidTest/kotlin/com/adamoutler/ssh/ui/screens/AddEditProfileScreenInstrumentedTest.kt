package com.adamoutler.ssh.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adamoutler.ssh.data.AuthType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class AddEditProfileScreenInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test(timeout = 300000L)
    fun saveButtonInTopAppBarTriggersSave() {
        var onSaveCalled = false

        composeTestRule.setContent {
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
                onSave = {
                    onSaveCalled = true
                },
                onNavigateBack = {}
            )
        }

        // Click the save button in the Top App Bar
        composeTestRule.onNodeWithContentDescription("Save Profile").performClick()

        // Assert that onSave was called
        assertTrue(onSaveCalled)
    }
}
package com.adamoutler.ssh.ui.components

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecurePasswordEditTextTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun findEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findEditText(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    @Test
    fun testSecurePasswordEditText_IMEFlagsInMaskedAndVisibleStates() {
        var capturedPassword = ""
        composeTestRule.setContent {
            SecurePasswordEditText(
                onPasswordChanged = { chars ->
                    capturedPassword = String(chars)
                },
                hint = "Enter password",
            )
        }
        composeTestRule.waitForIdle()

        val decorView = composeTestRule.activity.window.decorView
        val editText = findEditText(decorView)
        assertNotNull("EditText should be present in hierarchy", editText)
        checkNotNull(editText)

        // 1. Initial / Masked state
        val maskedImeOptions = editText.imeOptions
        assertTrue(
            "Masked state must have IME_FLAG_NO_PERSONALIZED_LEARNING flag set",
            (maskedImeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0,
        )
        assertEquals(
            "Masked state must have incognito privateImeOptions",
            "incognito",
            editText.privateImeOptions,
        )

        // Verify typing updates password
        editText.setText("SecretPass123")
        composeTestRule.waitForIdle()
        assertEquals("SecretPass123", capturedPassword)

        // 2. Toggle to Visible state
        composeTestRule.onNodeWithContentDescription("Show password").performClick()
        composeTestRule.waitForIdle()

        val visibleImeOptions = editText.imeOptions
        assertTrue(
            "Visible state must retain IME_FLAG_NO_PERSONALIZED_LEARNING flag",
            (visibleImeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0,
        )
        assertEquals(
            "Visible state must retain incognito privateImeOptions",
            "incognito",
            editText.privateImeOptions,
        )

        // 3. Toggle back to Masked state
        composeTestRule.onNodeWithContentDescription("Hide password").performClick()
        composeTestRule.waitForIdle()

        val remaskedImeOptions = editText.imeOptions
        assertTrue(
            "Remasked state must retain IME_FLAG_NO_PERSONALIZED_LEARNING flag",
            (remaskedImeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0,
        )
        assertEquals(
            "Remasked state must retain incognito privateImeOptions",
            "incognito",
            editText.privateImeOptions,
        )

        // Clear text to test empty CharArray path
        editText.text = null
        composeTestRule.waitForIdle()
        assertEquals("", capturedPassword)
    }
}

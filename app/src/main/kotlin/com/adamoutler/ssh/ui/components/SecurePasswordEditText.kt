package com.adamoutler.ssh.ui.components

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SecurePasswordEditText(
    onPasswordChanged: (CharArray) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { ctx ->
                EditText(ctx).apply {
                    this.hint = hint
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            if (s != null) {
                                val chars = CharArray(s.length)
                                s.getChars(0, s.length, chars, 0)
                                onPasswordChanged(chars)
                            } else {
                                onPasswordChanged(CharArray(0))
                            }
                        }
                    })
                }
            },
            update = { editText ->
                val selectionStart = editText.selectionStart
                val selectionEnd = editText.selectionEnd
                editText.inputType = if (passwordVisible) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                editText.setSelection(if (selectionStart >= 0) selectionStart else 0, if (selectionEnd >= 0) selectionEnd else 0)
            },
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Icon(
                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (passwordVisible) "Hide password" else "Show password"
            )
        }
    }
}

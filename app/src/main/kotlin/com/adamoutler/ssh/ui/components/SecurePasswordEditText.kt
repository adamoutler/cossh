package com.adamoutler.ssh.ui.components

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SecurePasswordEditText(
    onPasswordChanged: (CharArray) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
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
        modifier = modifier.fillMaxWidth()
    )
}

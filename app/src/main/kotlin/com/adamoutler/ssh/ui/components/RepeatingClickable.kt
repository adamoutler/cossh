package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Modifier.repeatingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    initialDelayMillis: Long = 500,
    repeatDelayMillis: Long = 50,
    onClick: () -> Unit
): Modifier = composed {
    val currentClickListener by androidx.compose.runtime.rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()

    this.pointerInput(interactionSource, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            currentClickListener() // Initial click
            val job = coroutineScope.launch {
                delay(initialDelayMillis)
                while (true) {
                    currentClickListener()
                    delay(repeatDelayMillis)
                }
            }
            waitForUpOrCancellation()
            job.cancel()
        }
    }
}

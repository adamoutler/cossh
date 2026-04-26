package com.adamoutler.ssh.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HoldToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = 1500,
    text: String = "Hold to Accept Risk",
    baseColor: Color = MaterialTheme.colorScheme.errorContainer,
    fillColor: Color = MaterialTheme.colorScheme.error
) {
    val coroutineScope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var isConfirmed by remember { mutableStateOf(false) }
    val view = LocalView.current

    LaunchedEffect(progress.value) {
        if (progress.value >= 1f && !isConfirmed) {
            isConfirmed = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onConfirm()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(baseColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (isConfirmed) return@awaitEachGesture
                    
                    val job = coroutineScope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis, easing = LinearEasing)
                        )
                    }

                    // Wait for the pointer to be released
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })

                    // If released before 100%, cancel and animate back to 0
                    if (!isConfirmed) {
                        job.cancel()
                        coroutineScope.launch {
                            progress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(300, easing = LinearEasing)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // The fill bar
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .background(fillColor)
                .align(Alignment.CenterStart)
        )

        // The text on top
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

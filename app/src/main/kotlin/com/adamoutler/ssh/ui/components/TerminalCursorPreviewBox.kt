package com.adamoutler.ssh.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme

/**
 * Animated real-time terminal cursor preview box.
 *
 * Renders an authentic VT100 prompt with square-wave 1.0 Hz blink dynamics
 * (500ms visible, 500ms hidden) and sharp cursor geometry for Block, Underline, and Bar.
 */
@Composable
fun TerminalCursorPreviewBox(
    theme: TerminalTheme,
    fontChoice: TerminalFontChoice,
    cursorStyle: TerminalCursorStyle,
    cursorBlink: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink_transition")
    val isVisible by if (cursorBlink) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1000
                    1f at 0
                    1f at 499
                    0f at 500
                    0f at 999
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "cursor_blink_alpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = Color(theme.background), shape = RoundedCornerShape(8.dp))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Terminal cursor preview: ${cursorStyle.displayName} cursor, ${if (cursorBlink) "blinking" else "solid"}."
            },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "user@cossh:~$ ",
                    color = Color(theme.ansiColors[2]),
                    fontFamily = fontChoice.composeFontFamily,
                    fontSize = 13.sp,
                )
                Text(
                    text = "ssh-keygen -t ed25519",
                    color = Color(theme.foreground),
                    fontFamily = fontChoice.composeFontFamily,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enter passphrase: ",
                    color = Color(theme.foreground),
                    fontFamily = fontChoice.composeFontFamily,
                    fontSize = 13.sp,
                )

                // Dynamic Animated Cursor Glyphs
                if (isVisible > 0.5f) {
                    when (cursorStyle) {
                        TerminalCursorStyle.BLOCK -> {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(15.dp)
                                    .background(Color(theme.cursor)),
                            )
                        }
                        TerminalCursorStyle.UNDERLINE -> {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(15.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .background(Color(theme.cursor)),
                                )
                            }
                        }
                        TerminalCursorStyle.VERTICAL_BAR -> {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .height(15.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .fillMaxHeight()
                                        .background(Color(theme.cursor)),
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp).height(15.dp))
                }
            }
        }
    }
}

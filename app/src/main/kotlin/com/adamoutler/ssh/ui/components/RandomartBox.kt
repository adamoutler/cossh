package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ObsidianBackground = Color(0xFF0B0F19)
private val BorderCharColor = Color(0xFF64748B)
private val TitleHighlightColor = Color(0xFF38BDF8)
private val StartMarkerGreen = Color(0xFF10B981)
private val EndMarkerAmber = Color(0xFFF59E0B)
private val StandardBodyColor = Color(0xFFCBD5E1)

/**
 * Obsidian terminal component rendering OpenSSH Drunken Bishop randomart.
 * Features high-contrast monospace rendering with colored Start ('S') and End ('E') markers.
 */
@Composable
fun RandomartBox(
    randomart: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    borderColor: Color = Color(0xFF1E293B),
    testTag: String = "RandomartBox",
) {
    val annotatedString = remember(randomart) {
        buildAnnotatedRandomart(randomart)
    }

    Column(modifier = modifier) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag(testTag),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = annotatedString,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = StandardBodyColor,
            )
        }
    }
}

private fun buildAnnotatedRandomart(art: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = art.split("\n")
        lines.forEachIndexed { lineIndex, line ->
            val isHeaderOrFooter = lineIndex == 0 || lineIndex == lines.size - 1

            if (isHeaderOrFooter) {
                var inBracket = false
                line.forEach { ch ->
                    when {
                        ch == '[' -> {
                            inBracket = true
                            pushStyle(SpanStyle(color = TitleHighlightColor, fontWeight = FontWeight.Bold))
                            append(ch)
                        }
                        ch == ']' -> {
                            append(ch)
                            pop()
                            inBracket = false
                        }
                        inBracket -> {
                            append(ch)
                        }
                        ch == '+' || ch == '-' -> {
                            pushStyle(SpanStyle(color = BorderCharColor))
                            append(ch)
                            pop()
                        }
                        else -> {
                            append(ch)
                        }
                    }
                }
            } else {
                line.forEach { ch ->
                    when (ch) {
                        '|' -> {
                            pushStyle(SpanStyle(color = BorderCharColor))
                            append(ch)
                            pop()
                        }
                        'S' -> {
                            pushStyle(SpanStyle(color = StartMarkerGreen, fontWeight = FontWeight.ExtraBold))
                            append(ch)
                            pop()
                        }
                        'E' -> {
                            pushStyle(SpanStyle(color = EndMarkerAmber, fontWeight = FontWeight.ExtraBold))
                            append(ch)
                            pop()
                        }
                        else -> {
                            append(ch)
                        }
                    }
                }
            }

            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }
}

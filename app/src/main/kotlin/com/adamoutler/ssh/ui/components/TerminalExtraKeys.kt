package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalExtraKeys(
    ctrlState: ModifierState,
    altState: ModifierState,
    superState: ModifierState,
    menuState: ModifierState,
    onKeyToggle: (String) -> Unit,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    presetId: ExtraKeysPresetId = ExtraKeysPresetId.DEFAULT,
    onKeySpecPress: ((ExtraKeySpec) -> Unit)? = null,
) {
    val presetRows = ExtraKeysPresets.getPreset(presetId)
    val page1Row1 = presetRows.getOrNull(0) ?: emptyList()
    val page1Row2 = presetRows.getOrNull(1) ?: emptyList()

    val page2Row1 = listOf(
        ExtraKeySpec(id = "pgup", label = "PgUp", contentDescription = "Page Up", payload = "\u001b[5~", isRepeatable = true),
        ExtraKeySpec(id = "ins", label = "Ins", contentDescription = "Insert", payload = "\u001b[2~", isRepeatable = true),
        ExtraKeySpec(id = "prtsc", label = "PrtSc", contentDescription = "Print Screen", payload = "\u001b[32~", isRepeatable = true),
    )
    val page2Row2 = listOf(
        ExtraKeySpec(id = "pgdn", label = "PgDn", contentDescription = "Page Down", payload = "\u001b[6~", isRepeatable = true),
        ExtraKeySpec(id = "del", label = "Del", contentDescription = "Delete", payload = "\u001b[3~", isRepeatable = true),
        ExtraKeySpec(id = "pause", label = "Pause", contentDescription = "Pause", payload = "\u001a", isRepeatable = false),
    )

    val page3Row1 = listOf("F1", "F2", "F3", "F4", "F5", "F6").map { label ->
        ExtraKeySpec(id = label.lowercase(), label = label, contentDescription = "$label function key", payload = "")
    }
    val page3Row2 = listOf("F7", "F8", "F9", "F10", "F11", "F12").map { label ->
        ExtraKeySpec(id = label.lowercase(), label = label, contentDescription = "$label function key", payload = "")
    }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFF222222))
            .focusProperties { canFocus = false }
            .padding(4.dp),
    ) { page ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val items1 = when (page) {
                    0 -> page1Row1
                    1 -> page2Row1
                    else -> page3Row1
                }
                items1.forEach { spec ->
                    ExtraKeyButton(
                        text = spec.label,
                        state = getModifierState(spec.label, ctrlState, altState, superState, menuState),
                        modifier = Modifier.weight(1f),
                        contentDescription = spec.contentDescription,
                        isRepeatable = spec.isRepeatable,
                    ) {
                        if (onKeySpecPress != null) {
                            onKeySpecPress(spec)
                        } else {
                            handleKey(spec.label, onKeyToggle, onKeyPress)
                        }
                    }
                }
                if (items1.size < 6) {
                    repeat(6 - items1.size) {
                        Spacer(modifier = Modifier.weight(1f).padding(horizontal = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val items2 = when (page) {
                    0 -> page1Row2
                    1 -> page2Row2
                    else -> page3Row2
                }
                items2.forEach { spec ->
                    ExtraKeyButton(
                        text = spec.label,
                        state = getModifierState(spec.label, ctrlState, altState, superState, menuState),
                        modifier = Modifier.weight(1f),
                        contentDescription = spec.contentDescription,
                        isRepeatable = spec.isRepeatable,
                    ) {
                        if (onKeySpecPress != null) {
                            onKeySpecPress(spec)
                        } else {
                            handleKey(spec.label, onKeyToggle, onKeyPress)
                        }
                    }
                }
                if (items2.size < 6) {
                    repeat(6 - items2.size) {
                        Spacer(modifier = Modifier.weight(1f).padding(horizontal = 2.dp))
                    }
                }
            }
        }
    }
}

private fun getModifierState(
    key: String,
    ctrlState: ModifierState,
    altState: ModifierState,
    superState: ModifierState,
    menuState: ModifierState,
): ModifierState = when (key) {
    "Ctrl" -> ctrlState
    "Alt" -> altState
    "Super" -> superState
    "Menu" -> menuState
    else -> ModifierState.INACTIVE
}

private fun handleKey(key: String, onKeyToggle: (String) -> Unit, onKeyPress: (String) -> Unit) {
    if (key == "Ctrl" || key == "Alt" || key == "Super" || key == "Menu") {
        onKeyToggle(key)
    } else {
        onKeyPress(key)
    }
}

@Composable
fun ExtraKeyButton(
    text: String,
    state: ModifierState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isRepeatable: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val repeatableKeys = setOf("↑", "↓", "←", "→", "Ctrl-C", "PgUp", "PgDn", "Del", "Ins", "Home", "End")
    val shouldRepeat = isRepeatable || text in repeatableKeys

    val backgroundColor = when (state) {
        ModifierState.INACTIVE -> Color(0xFF444444)
        ModifierState.STICKY -> MaterialTheme.colorScheme.primary
        ModifierState.LOCKED -> MaterialTheme.colorScheme.tertiary // Distinctive color for Locked
    }

    val contentColor = when (state) {
        ModifierState.INACTIVE -> Color.White
        else -> MaterialTheme.colorScheme.onPrimary
    }

    val a11yDesc = contentDescription ?: text

    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(48.dp)
            .background(backgroundColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .focusProperties { canFocus = false }
            .semantics {
                this.contentDescription = a11yDesc
            }
            .then(
                if (shouldRepeat) {
                    Modifier.repeatingClickable(interactionSource = interactionSource) { onClick() }
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current,
                    ) { onClick() }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            // Slightly smaller text to fit
            fontSize = 12.sp,
            fontWeight = if (state == ModifierState.LOCKED) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun ExtraKeyButton(
    text: String,
    state: ModifierState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = ExtraKeyButton(
    text = text,
    state = state,
    modifier = modifier,
    contentDescription = null,
    isRepeatable = false,
    onClick = onClick,
)

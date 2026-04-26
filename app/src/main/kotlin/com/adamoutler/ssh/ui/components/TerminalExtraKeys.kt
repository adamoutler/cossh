package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TerminalExtraKeys(
    ctrlActive: Boolean,
    altActive: Boolean,
    superActive: Boolean,
    menuActive: Boolean,
    onKeyToggle: (String) -> Unit,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0
) {
    val page1Row1 = listOf("Esc", "Ctrl-C", "Menu", "↑", "Tab", "Home")
    val page1Row2 = listOf("Ctrl", "Alt", "←", "↓", "→", "End")
    val page2Row1 = listOf("PgUp", "Ins", "PrtSc")
    val page2Row2 = listOf("PgDn", "Del", "Pause")
    val page3Row1 = listOf("F1", "F2", "F3", "F4", "F5", "F6")
    val page3Row2 = listOf("F7", "F8", "F9", "F10", "F11", "F12")

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Color(0xFF222222))
            .focusProperties { canFocus = false }
            .padding(4.dp)
    ) { page ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val items1 = when (page) {
                    0 -> page1Row1
                    1 -> page2Row1
                    else -> page3Row1
                }
                items1.forEach { key ->
                    ExtraKeyButton(key, isActive(key, ctrlActive, altActive, superActive, menuActive), Modifier.weight(1f)) {
                        handleKey(key, onKeyToggle, onKeyPress)
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
                items2.forEach { key ->
                    ExtraKeyButton(key, isActive(key, ctrlActive, altActive, superActive, menuActive), Modifier.weight(1f)) {
                        handleKey(key, onKeyToggle, onKeyPress)
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

private fun isActive(key: String, ctrlActive: Boolean, altActive: Boolean, superActive: Boolean, menuActive: Boolean): Boolean {
    return when (key) {
        "Ctrl" -> ctrlActive
        "Alt" -> altActive
        "Super" -> superActive
        "Menu" -> menuActive
        else -> false
    }
}

private fun handleKey(key: String, onKeyToggle: (String) -> Unit, onKeyPress: (String) -> Unit) {
    if (key == "Ctrl" || key == "Alt" || key == "Menu") {
        onKeyToggle(key)
    } else {
        onKeyPress(key)
    }
}

@Composable
fun ExtraKeyButton(text: String, isActive: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val repeatableKeys = setOf("↑", "↓", "←", "→", "Ctrl-C", "PgUp", "PgDn", "Del", "Ins", "Home", "End")
    
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(48.dp)
            .background(if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF444444), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .focusProperties { canFocus = false }
            .then(
                if (text in repeatableKeys) {
                    Modifier.repeatingClickable(interactionSource = interactionSource) { onClick() }
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.foundation.LocalIndication.current
                    ) { onClick() }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
            fontSize = 12.sp, // Slightly smaller text to fit
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
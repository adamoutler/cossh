package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TerminalExtraKeys(
    ctrlActive: Boolean,
    altActive: Boolean,
    superActive: Boolean,
    menuActive: Boolean,
    onKeyToggle: (String) -> Unit,
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 2 })

    val page1Row1 = listOf("Esc", "Super", "Menu", "Up", "Tab", "Home")
    val page1Row2 = listOf("Ctrl", "Alt", "Left", "Down", "Right", "End")
    
    val page2Row1 = listOf("PgUp", "Ins", "PrtSc")
    val page2Row2 = listOf("PgDn", "Del", "Pause")

    HorizontalPager(state = pagerState, modifier = modifier.fillMaxWidth().background(Color(0xFF222222))) { page ->
        Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            if (page == 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    page1Row1.forEach { key ->
                        ExtraKeyButton(key, isActive(key, ctrlActive, altActive, superActive, menuActive)) {
                            handleKey(key, onKeyToggle, onKeyPress)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    page1Row2.forEach { key ->
                        ExtraKeyButton(key, isActive(key, ctrlActive, altActive, superActive, menuActive)) {
                            handleKey(key, onKeyToggle, onKeyPress)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    page2Row1.forEach { key ->
                        ExtraKeyButton(key, false) { handleKey(key, onKeyToggle, onKeyPress) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    page2Row2.forEach { key ->
                        ExtraKeyButton(key, false) { handleKey(key, onKeyToggle, onKeyPress) }
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
    if (key == "Ctrl" || key == "Alt" || key == "Super" || key == "Menu") {
        onKeyToggle(key)
    } else {
        onKeyPress(key)
    }
}

@Composable
fun ExtraKeyButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .background(if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF444444), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

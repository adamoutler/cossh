package com.adamoutler.ssh.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.adamoutler.ssh.data.TerminalCursorStyle
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.ui.theme.TerminalFontChoice
import com.adamoutler.ssh.ui.theme.TerminalTheme
import org.junit.Rule
import org.junit.Test

class TerminalCursorScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun testTerminalCursor_BlockShape_Preview() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        TerminalCursorPreviewBox(
                            theme = TerminalTheme.COBALT_BLUE,
                            fontChoice = TerminalFontChoice.JETBRAINS_MONO,
                            cursorStyle = TerminalCursorStyle.BLOCK,
                            cursorBlink = false,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testTerminalCursor_UnderlineShape_Preview() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        TerminalCursorPreviewBox(
                            theme = TerminalTheme.SOLARIZED_DARK,
                            fontChoice = TerminalFontChoice.FIRA_CODE,
                            cursorStyle = TerminalCursorStyle.UNDERLINE,
                            cursorBlink = false,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun testTerminalCursor_VerticalBarShape_Preview() {
        paparazzi.snapshot {
            CoSSHTheme(darkTheme = true) {
                Surface {
                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        TerminalCursorPreviewBox(
                            theme = TerminalTheme.DRACULA,
                            fontChoice = TerminalFontChoice.SOURCE_CODE_PRO,
                            cursorStyle = TerminalCursorStyle.VERTICAL_BAR,
                            cursorBlink = false,
                        )
                    }
                }
            }
        }
    }
}

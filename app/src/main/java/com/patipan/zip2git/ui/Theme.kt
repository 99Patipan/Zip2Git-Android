package com.patipan.zip2git.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Bg = Color(0xFF0B1020)
internal val Panel = Color(0xFF151D30)
internal val Accent = Color(0xFF58A6FF)
internal val Muted = Color(0xFF91A0B8)
internal val Good = Color(0xFF70D6A2)
internal val Danger = Color(0xFFFF7B72)

@Composable
fun Zip2GitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel),
        content = content,
    )
}

package com.patipan.zip2git.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---- Dark Frosted Glass palette ----
internal val BgTop = Color(0xFF050A16)
internal val BgMid = Color(0xFF0A1226)
internal val BgBottom = Color(0xFF060C1A)
internal val GlassTint = Color(0xFF0B1328).copy(alpha = 0.35f)

internal val TextPrimary = Color(0xFFF2F6FF)
internal val Muted = Color(0xFF9FB0CC)
internal val Accent = Color(0xFF4DA3FF)
internal val AccentLight = Color(0xFF8CC4FF)
internal val AccentDeep = Color(0xFF2F6BFF)
internal val Violet = Color(0xFF8B5CF6)
internal val Good = Color(0xFF6EE7B7)
internal val Danger = Color(0xFFFF8A80)

private class Glow(val color: Color, val cx: Float, val cy: Float, val radiusFraction: Float)

private val Glows = listOf(
    Glow(AccentDeep.copy(alpha = 0.50f), 0.05f, 0.10f, 0.95f),
    Glow(Color(0xFF22D3EE).copy(alpha = 0.20f), 1.00f, 0.02f, 0.70f),
    Glow(Color(0xFF10B981).copy(alpha = 0.32f), 0.00f, 0.62f, 0.90f),
    Glow(Violet.copy(alpha = 0.34f), 1.00f, 0.42f, 0.85f),
    Glow(AccentDeep.copy(alpha = 0.30f), 0.60f, 0.98f, 0.90f),
)

/** Dark gradient with soft colored glows; the translucent glass cards sit on top of it. */
internal fun Modifier.glassBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(BgTop, BgMid, BgBottom)))
    for (g in Glows) {
        val center = Offset(size.width * g.cx, size.height * g.cy)
        val radius = size.width * g.radiusFraction
        drawCircle(
            brush = Brush.radialGradient(listOf(g.color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center,
        )
    }
}

@Composable
fun Zip2GitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = BgMid,
            surface = BgMid,
            onSurface = TextPrimary,
            surfaceContainer = Color(0xFF14203D), // dropdown menu background
        ),
        content = content,
    )
}

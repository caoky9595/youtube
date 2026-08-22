package com.youtube.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Bang mau lay theo app YouTube tren TV: nen gan den tuyet doi, nhan do. */
val YtBg = Color(0xFF0F0F0F)
val YtPanel = Color(0xFF1C1C1C)
val YtHover = Color(0xFF272727)
val YtText = Color(0xFFF1F1F1)
val YtDim = Color(0xFFAAAAAA)
val YtRed = Color(0xFFFF0033)

private val colors = darkColorScheme(
    primary = YtText,
    onPrimary = Color.Black,
    secondary = YtHover,
    onSecondary = YtText,
    background = YtBg,
    onBackground = YtText,
    surface = YtPanel,
    onSurface = YtText,
    surfaceVariant = YtHover,
    onSurfaceVariant = YtDim,
    error = YtRed,
)

@Composable
fun YouTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

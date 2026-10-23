package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EbSynthDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = OnCyberCyan,
    primaryContainer = CyberCyanContainer,
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = AnimeMagenta,
    onSecondary = OnAnimeMagenta,
    secondaryContainer = AnimeMagentaContainer,
    onSecondaryContainer = Color(0xFFFFD8E8),
    tertiary = FlowViolet,
    onTertiary = Color.White,
    background = StudioObsidian,
    onBackground = TextPrimaryLight,
    surface = StudioSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = TextSecondaryMuted,
    outline = Color(0xFF34405E)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EbSynthDarkColorScheme,
        typography = Typography,
        content = content
    )
}

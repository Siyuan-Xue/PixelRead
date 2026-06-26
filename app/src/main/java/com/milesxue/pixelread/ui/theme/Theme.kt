package com.milesxue.pixelread.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PixelReadColorScheme = darkColorScheme(
    primary = ClaudeClay,
    onPrimary = ClaudeGray950,
    secondary = PixelSuccess,
    onSecondary = ClaudeGray950,
    tertiary = PixelInfo,
    onTertiary = ClaudeGray950,
    background = ClaudeGray950,
    onBackground = ClaudeGray050,
    surface = ClaudeGray850,
    onSurface = ClaudeGray050,
    surfaceVariant = ClaudeGray750,
    onSurfaceVariant = ClaudeGray050,
    error = PixelError,
    onError = ClaudeGray950,
    outline = ClaudeGray600,
)

@Composable
fun PixelReadTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PixelReadColorScheme,
        typography = Typography,
        content = content
    )
}

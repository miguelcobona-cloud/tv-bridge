package com.tvbridge.sender.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF0F172A)
private val Surface = Color(0xFF1E293B)
private val Primary = Color(0xFF3B82F6)
private val OnBackground = Color(0xFFF1F5F9)
private val OnSurfaceVariant = Color(0xFF94A3B8)
private val Error = Color(0xFFEF4444)

private val ColorScheme = darkColorScheme(
    primary = Primary,
    background = Background,
    surface = Surface,
    onBackground = OnBackground,
    onSurface = OnBackground,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
)

@Composable
fun TvBridgeSenderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}

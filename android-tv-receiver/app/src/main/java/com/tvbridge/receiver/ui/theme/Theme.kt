package com.tvbridge.receiver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.tvbridge.receiver.ui.theme.TvBridgeBackground
import com.tvbridge.receiver.ui.theme.TvBridgeError
import com.tvbridge.receiver.ui.theme.TvBridgeOnPrimary
import com.tvbridge.receiver.ui.theme.TvBridgePrimary
import com.tvbridge.receiver.ui.theme.TvBridgeSurface
import com.tvbridge.receiver.ui.theme.TvBridgeTextPrimary
import com.tvbridge.receiver.ui.theme.TvBridgeTextSecondary

private val DarkColorScheme = darkColorScheme(
    primary = TvBridgePrimary,
    onPrimary = TvBridgeOnPrimary,
    background = TvBridgeBackground,
    onBackground = TvBridgeTextPrimary,
    surface = TvBridgeSurface,
    onSurface = TvBridgeTextPrimary,
    error = TvBridgeError,
    onError = TvBridgeOnPrimary,
    surfaceVariant = TvBridgeSurface,
    onSurfaceVariant = TvBridgeTextSecondary,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColorScheme else DarkColorScheme,
        typography = TvBridgeTypography,
        content = content,
    )
}

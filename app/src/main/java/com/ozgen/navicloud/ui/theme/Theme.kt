package com.ozgen.navicloud.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Spotify-inspired dark palette with a violet accent
val Accent = Color(0xFF7C6CFF)
val AccentDim = Color(0xFF5A4EC7)
val Background = Color(0xFF0F0F14)
val Surface = Color(0xFF17171E)
val SurfaceHigh = Color(0xFF22222B)
val TextPrimary = Color(0xFFF2F2F7)
val TextSecondary = Color(0xFFA0A0AC)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentDim,
    onPrimaryContainer = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHigh,
    outline = Color(0xFF3A3A46),
)

@Composable
fun NaviCloudTheme(content: @Composable () -> Unit) {
    // Always dark: content-first, Spotify-style identity
    MaterialTheme(
        colorScheme = DarkColors,
        typography = NaviTypography,
        content = content,
    )
}

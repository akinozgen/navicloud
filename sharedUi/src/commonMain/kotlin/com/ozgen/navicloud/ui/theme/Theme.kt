package com.ozgen.navicloud.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.ozgen.navicloud.data.AccentColor
import com.ozgen.navicloud.data.AppearancePreferences
import com.ozgen.navicloud.ui.extractArtColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFF0F0F14)
private val Surface = Color(0xFF17171E)
private val SurfaceHigh = Color(0xFF22222B)
private val TextPrimary = Color(0xFFF2F2F7)
private val TextSecondary = Color(0xFFA0A0AC)

/** Kapak glow tercihini common UI bileşenlerine taşır; ayrı pencereler kendi theme provider'ını kurar. */
val LocalAppearancePreferences = staticCompositionLocalOf { AppearancePreferences() }

private const val DEFAULT_ACCENT_ARGB = 0xFF7C6CFF

private fun darkColors(appearance: AppearancePreferences, autoAccent: Color?) = run {
    val accent = appearance.accentColor.argb?.let { Color(it) } ?: autoAccent ?: Color(DEFAULT_ACCENT_ARGB)
    val background = if (appearance.preferPitchBlack) Color.Black else Background
    val surface = if (appearance.preferPitchBlack) Color.Black else Surface
    val surfaceHigh = if (appearance.preferPitchBlack) Color.Black else SurfaceHigh
    val onAccent = if (accent.luminance() >= 0.42f) Color(0xFF101014) else Color.White
    val accentContainer = accent.copy(alpha = 0.72f).compositeOver(background)

    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = TextPrimary,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = TextPrimary,
        tertiary = accent,
        onTertiary = onAccent,
        background = background,
        onBackground = TextPrimary,
        surface = surface,
        onSurface = TextPrimary,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = TextSecondary,
        surfaceDim = surface,
        surfaceBright = surfaceHigh,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHigh,
        outline = if (appearance.preferPitchBlack) Color(0xFF303038) else Color(0xFF3A3A46),
    )
}

@Composable
fun NaviCloudTheme(
    appearance: AppearancePreferences = AppearancePreferences(),
    autoAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    // Always dark: content-first identity; accent/surface preferences stay live.
    val colors = remember(appearance.accentColor, appearance.preferPitchBlack, autoAccent) {
        darkColors(appearance, autoAccent)
    }
    CompositionLocalProvider(LocalAppearancePreferences provides appearance) {
        MaterialTheme(
            colorScheme = colors,
            typography = naviTypography(),
            content = content,
        )
    }
}

/** AUTO seçiliyken kapak accent'ini iki platformda aynı extractor üzerinden çözer. */
@Composable
fun rememberAutoAccent(
    appearance: AppearancePreferences,
    artworkUrl: String?,
    cacheKey: String?,
): Color? {
    var accent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(appearance.accentColor, artworkUrl, cacheKey) {
        accent = if (appearance.accentColor == AccentColor.AUTO && artworkUrl != null) {
            withContext(Dispatchers.IO) {
                runCatching { extractArtColors(artworkUrl, cacheKey) }.getOrNull()?.second
            }
        } else {
            null
        }
    }
    return accent
}

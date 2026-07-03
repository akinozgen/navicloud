package com.ozgen.navicloud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Masaüstünde sistem geri tuşu yok; Esc bağlaması D5'te pencere düzeyinde
}

@Composable
actual fun rememberToaster(): (String) -> Unit = { msg -> println("TOAST: $msg") }

actual suspend fun extractArtColors(artUrl: String, cacheKey: String?): Pair<Color, Color?>? {
    // MVP: masaüstünde palet çıkarımı yok — varsayılan koyu tema renkleri.
    // (Skia bitmap'ten basit baskın renk D5 sonrası.)
    return null
}

package com.ozgen.navicloud.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.ozgen.navicloud.i18n.EnStrings
import com.ozgen.navicloud.i18n.Strings

/**
 * O anki dil metin kataloğu. Kök (NaviCloudRoot) seçili dile göre sağlar;
 * dil değişince tüm alt ağaç yeniden çizilir (static = geniş invalidation, istenen).
 */
val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

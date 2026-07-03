package com.ozgen.navicloud.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

// Classpath kaynağından; variable font Skia'da varsayılan enstantane ile
// yüklenir — ağırlık farkları sınırlı olabilir, D5 sonrası rafine edilir
private val SpaceGrotesk = FontFamily(
    Font("font/space_grotesk.ttf", weight = FontWeight.Normal, style = FontStyle.Normal),
    Font("font/space_grotesk.ttf", weight = FontWeight.Medium, style = FontStyle.Normal),
    Font("font/space_grotesk.ttf", weight = FontWeight.SemiBold, style = FontStyle.Normal),
    Font("font/space_grotesk.ttf", weight = FontWeight.Bold, style = FontStyle.Normal),
)

@Composable
actual fun spaceGroteskFamily(): FontFamily = SpaceGrotesk

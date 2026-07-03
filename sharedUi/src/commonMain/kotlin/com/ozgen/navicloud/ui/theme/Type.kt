package com.ozgen.navicloud.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Space Grotesk (variable): karakterli ama okunur — "stock Compose" hissini
 * kıran ana hamle. Yükleme platforma özgü: Android res/font + FontVariation,
 * masaüstü classpath kaynağı.
 */
@Composable
expect fun spaceGroteskFamily(): FontFamily

@Composable
fun naviTypography(): Typography {
    val f = spaceGroteskFamily()
    return Typography(
        headlineLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
        titleMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 16.sp),
        bodyMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodySmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        labelLarge = TextStyle(fontFamily = f, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        labelMedium = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = TextStyle(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
    )
}

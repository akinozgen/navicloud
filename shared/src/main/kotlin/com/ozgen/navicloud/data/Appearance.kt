package com.ozgen.navicloud.data

/**
 * Android ve masaüstünün paylaştığı sabit accent paleti.
 * Renkler koyu zeminde canlı kalacak kadar doygun, pastel hissi koruyacak kadar yumuşaktır.
 */
enum class AccentColor(val argb: Long?) {
    /** Çalan parçanın kapağından türetilir; kapak yoksa NaviCloud violet kullanılır. */
    AUTO(null),
    VIOLET(0xFF7C6CFF),
    ROSE(0xFFFF8FB1),
    PEACH(0xFFFFB38A),
    AMBER(0xFFFFD166),
    MINT(0xFF72D6B3),
    SKY(0xFF78B7FF),
}

/** İki platformda aynı varsayımlarla kullanılan görünüm tercihleri. */
data class AppearancePreferences(
    val accentColor: AccentColor = AccentColor.AUTO,
    val preferPitchBlack: Boolean = false,
    val albumArtGlow: Boolean = true,
)

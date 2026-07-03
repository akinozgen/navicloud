package com.ozgen.navicloud.data

/** Akış kalitesi: RAW = transcode yok; diğerleri sunucu tarafında MP3'e düşürür. */
enum class StreamQuality(val kbps: Int?, val label: String) {
    RAW(null, "Orijinal (transcode yok)"),
    HIGH(320, "Yüksek • 320 kbps"),
    MEDIUM(192, "Normal • 192 kbps"),
    LOW(128, "Düşük • 128 kbps"),
}

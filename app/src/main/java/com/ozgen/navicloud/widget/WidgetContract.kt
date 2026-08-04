package com.ozgen.navicloud.widget

/**
 * Ana ekran widget'larının paylaştığı sabitler ve durum modeli.
 *
 * Widget'lar RemoteViews ile çizilir (Glance değil): sıfır bağımlılık, minimal
 * yüzey, piksel kontrol. Transport = MediaSession media-button PendingIntent'leri
 * (Media3 sürümüne dokunmadan çalışır). Bkz. masaüstü karşılıkları
 * MiniPlayer.kt (Bar) ve MiniVinylWindow.kt (Vinyl).
 */
object WidgetContract {
    // PendingIntent request kodları. Media-button intent'leri YALNIZ extra'daki
    // KeyEvent'te ayrışır; PendingIntent eşitliği extra'ya bakmaz → aynı request
    // code kullanılırsa play/next/prev tek intent'e çakışır. Her biri ayrı olmalı.
    const val REQ_PLAY_PAUSE = 8101
    const val REQ_NEXT = 8102
    const val REQ_PREV = 8103
    const val REQ_OPEN = 8104

    // Kapak/blur bitmap'leri hücre boyutuna örneklenir; cross-process ~6MB
    // sınırına takılmamak için üst limitler (yazılım bitmap = w*h*4 bayt).
    const val MAX_BG_W = 1200
    const val MAX_BG_H = 600
    const val MAX_DISC = 512

    /** Varsayılan accent (kapaktan renk çıkmazsa) — uygulama moru. */
    const val DEFAULT_ACCENT = 0xFF8E5BFF.toInt()
}

/**
 * Player'dan (sıcak yol) ana thread'de okunan ham durum. Player referansı
 * TAŞIMAZ → okunduktan sonra IO'da güvenle kullanılır.
 */
data class RawPlayback(
    val hasContent: Boolean,
    val title: String?,
    val artist: String?,
    val coverArtId: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        val EMPTY = RawPlayback(false, null, null, null, false, 0L, 0L)
    }
}

/**
 * Widget'ın çizeceği çözülmüş durum. Bitmap/accent burada YOK — onları
 * renderer kapak id'sinden üretir (böylece snapshot ucuz ve serileştirilebilir).
 *
 * Durumlar:
 *  - hasContent=false → **idle** (hiç çalmadı / kuyruk boş): marka + CTA, transport gizli.
 *  - hasContent=true, isCold=false → sıcak: transport = media-button.
 *  - hasContent=true, isCold=true → soğuk (servis ölü, kalıcı kuyruktan): transport = app aç/resume.
 *  - isOffline: küçük "çevrimdışı" rozeti; sıcaksa kontroller cache'ten çalışır.
 */
data class WidgetSnapshot(
    val hasContent: Boolean,
    val title: String?,
    val artist: String?,
    val coverArtId: String?,
    val isPlaying: Boolean,
    val isCold: Boolean,
    val isOffline: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    val isIdle: Boolean get() = !hasContent

    companion object {
        fun idle(offline: Boolean) =
            WidgetSnapshot(false, null, null, null, false, false, offline, 0L, 0L)
    }
}

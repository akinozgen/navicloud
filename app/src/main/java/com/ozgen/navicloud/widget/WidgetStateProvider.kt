package com.ozgen.navicloud.widget

import androidx.media3.common.Player
import com.ozgen.navicloud.playback.MediaKeys
import com.ozgen.navicloud.playback.QueueCore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget durumunu üretir:
 *  - **Sıcak**: canlı player'dan ([readPlayer], ana thread'de çağrılır).
 *  - **Soğuk**: servis ölüyse kalıcı kuyruk deposundan son parça ([resolve]).
 *
 * Player'ın hangi thread'de okunacağı ayrımı önemli: Media3 player'ına yalnız
 * kendi (ana) thread'inden dokunulur; [readPlayer] ham veriyi kopyalar,
 * [resolve] ise player'a hiç dokunmadan (offline bayrağı + soğuk restore) çalışır.
 */
@Singleton
class WidgetStateProvider @Inject constructor(
    private val queueCore: QueueCore,
) {
    /** Ana thread'de: canlı player durumunu ham veriye kopyalar. */
    fun readPlayer(player: Player): RawPlayback {
        val item = player.currentMediaItem
        if (item == null || player.mediaItemCount == 0) return RawPlayback.EMPTY
        val meta = item.mediaMetadata
        val dur = player.duration
        return RawPlayback(
            hasContent = true,
            title = meta.title?.toString(),
            artist = meta.artist?.toString(),
            coverArtId = meta.extras?.getString(MediaKeys.COVER_ART),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (dur > 0) dur else 0L,
        )
    }

    /**
     * [raw] == null → servis ölü (soğuk yol). [raw] var ama hasContent=false →
     * player yaşıyor ama kuyruk boş (idle). Player'a dokunmaz; IO'da çağrılabilir.
     */
    suspend fun resolve(raw: RawPlayback?): WidgetSnapshot {
        val offline = runCatching { queueCore.isOffline() }.getOrDefault(false)

        if (raw != null && raw.hasContent) {
            return WidgetSnapshot(
                hasContent = true,
                title = raw.title,
                artist = raw.artist,
                coverArtId = raw.coverArtId,
                isPlaying = raw.isPlaying,
                isCold = false,
                isOffline = offline,
                positionMs = raw.positionMs,
                durationMs = raw.durationMs,
            )
        }
        // Player yaşıyor ama kuyruk boş → idle
        if (raw != null) return WidgetSnapshot.idle(offline)

        // Soğuk: kalıcı kuyruktan son parça (paused gösterilir, transport → app aç)
        val restored = runCatching { queueCore.restore() }.getOrNull()
        if (restored == null || restored.songs.isEmpty()) return WidgetSnapshot.idle(offline)
        val song = restored.songs.getOrNull(restored.index) ?: restored.songs.first()
        return WidgetSnapshot(
            hasContent = true,
            title = song.title,
            artist = song.artist,
            coverArtId = song.coverArt,
            isPlaying = false,
            isCold = true,
            isOffline = offline,
            positionMs = restored.positionMs,
            durationMs = song.duration * 1000L,
        )
    }
}

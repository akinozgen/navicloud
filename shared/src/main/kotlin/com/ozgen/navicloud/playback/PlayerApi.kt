package com.ozgen.navicloud.playback

import com.ozgen.navicloud.audio.EMPTY_SLEEP_TIMER
import com.ozgen.navicloud.audio.SleepTimerPreset
import com.ozgen.navicloud.audio.SleepTimerState
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.StreamQuality
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Platform-bağımsız çalma API'si. UI yalnızca bu dosyadaki tiplerle konuşur —
 * Media3 (ya da ileride masaüstünde vlcj) tipleri UI'a SIZMAZ. KMP'ye
 * geçişte bu dosya ortak modüle taşınır, her platform kendi
 * implementasyonunu bind eder (Android: [Media3PlayerController]).
 */
/**
 * What started playback — drives endless continuation when the queue runs out.
 * `@Serializable` (polimorfik): uzaktan kumandada SET_QUEUE ile aktarılır → alıcı endless'ı sürdürür (RC-0).
 */
@Serializable
sealed interface PlaybackContext {
    @Serializable data class Album(val albumId: String, val artistId: String?) : PlaybackContext
    @Serializable data class Artist(val artistId: String) : PlaybackContext
    @Serializable data class Playlist(val playlistId: String) : PlaybackContext
    @Serializable data object AllSongs : PlaybackContext
    @Serializable data class Genre(val genre: String) : PlaybackContext
}

enum class RepeatMode { OFF, ALL, ONE }

/**
 * Kuyruktaki bir girdi. Aynı şarkı kuyruğa iki kez eklenebilir; [uid] her
 * girdiye özeldir ve satır kimliği/reorder/swipe işlemlerinde TEK doğru
 * referanstır (indeksler bayatlayabilir, uid bayatlamaz).
 */
data class QueueTrack(
    val uid: String,
    val song: Song,
    /** Çözülmüş kapak URL'i (Android'de auth'lu Subsonic URL'i). */
    val artworkUrl: String?,
)

data class PlayerUiState(
    val currentTrack: QueueTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val queue: List<QueueTrack> = emptyList(),
    val currentIndex: Int = 0,
)

interface PlayerController {
    val state: StateFlow<PlayerUiState>

    /** Kuyruk başlığındaki "Şuradan çalınıyor: X" etiketi. */
    val contextLabel: StateFlow<String?>

    /** Şu an çalan bağlam — koleksiyon sayfaları play butonunu pause'a çevirir. */
    val currentContext: StateFlow<PlaybackContext?>

    /** Endless/otomatik oynatma anahtarı. */
    val endless: StateFlow<Boolean>

    /** Bildirimden "player'ı aç" istekleri (sayaç artar, UI sheet'i açar). */
    val expandRequests: StateFlow<Int>

    /** Aktif akış kalitesi (codec rozetindeki transcode göstergesi için). */
    val streamQuality: StateFlow<StreamQuality>

    val positionMs: Long
    val durationMs: Long

    fun play(
        songs: List<Song>,
        startIndex: Int = 0,
        context: PlaybackContext? = null,
        contextLabel: String? = null,
        /** Başlangıç parçasında başlanacak konum (ms) — cihazlar arası "kaldığın yerden" için. */
        startPositionMs: Long = 0L,
    )

    /** Şarkıların KOPYASINI çalanın hemen arkasına ekler (taşıma değil). */
    fun playNext(songs: List<Song>)
    fun addToQueue(songs: List<Song>)

    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun seekToQueueItem(index: Int)

    // UID tabanlı kuyruk işlemleri — UI closure'ları bayat indeks dondurabilir,
    // uid her zaman çağrı anında çözülür
    fun seekToUid(uid: String)
    fun removeQueueItemByUid(uid: String)
    fun moveQueueItemUidTo(uid: String, target: Int)

    /** Girdiyi çalanın hemen arkasına TAŞIR (kuyruk içi işlem). */
    fun playNextByUid(uid: String)

    fun toggleShuffle()
    fun cycleRepeat()
    fun toggleEndless()

    /** Çalmayı durdurur, kuyruğu ve kalıcı kuyruğu temizler, player UI kapanır. */
    fun stop()

    fun requestExpand()

    // --- Uyku zamanlayıcı (saf app mantığı; platforma göre override) ---
    // Default'lar no-op: masaüstü kendi pass'inde doldurana dek derlenmeye devam eder.
    val sleepTimer: StateFlow<SleepTimerState> get() = EMPTY_SLEEP_TIMER
    fun startSleepTimer(preset: SleepTimerPreset) {}
    fun cancelSleepTimer() {}
}

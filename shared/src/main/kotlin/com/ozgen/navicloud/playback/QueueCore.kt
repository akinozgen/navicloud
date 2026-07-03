package com.ozgen.navicloud.playback

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.OfflineModeSource
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Player implementasyonlarının (Media3/Android, mpv/masaüstü) paylaştığı
 * kuyruk mantığı: endless devam stratejileri, kuyruk kalıcılığı, offline
 * filtresi ve scrobble kuralı. Player'a dokunmaz — saf karar/veri katmanı.
 */

// Kalıcı kuyruk şeması: Android'in mevcut DataStore kaydıyla birebir aynı
// alan adları — geçiş sonrası eski kayıt okunmaya devam eder.
@Serializable
data class PersistedTrack(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val samplingRate: Int? = null,
    val starred: Boolean = false,
)

@Serializable
data class PersistedQueueState(
    val tracks: List<PersistedTrack>,
    val index: Int,
    val positionMs: Long,
    val contextLabel: String? = null,
)

/** Kalıcı kuyruğun ham JSON deposu: Android DataStore, masaüstü dosya. */
interface QueueStateStore {
    suspend fun save(json: String)
    suspend fun load(): String?
    suspend fun clear()
}

/**
 * Navidrome scrobble kuralı: doğal bitiş HER ZAMAN submission; erken
 * geçişte sürenin yarısı ya da 4 dakika (hangisi önce) çalındıysa submission.
 */
fun shouldSubmitScrobble(durationSec: Int, playedMs: Long, naturalEnd: Boolean): Boolean =
    naturalEnd || (durationSec > 0 && playedMs >= minOf(durationSec * 500L, 240_000L))

class QueueCore(
    private val music: MusicRepository,
    private val downloads: DownloadsPort,
    private val offline: OfflineModeSource,
    private val store: QueueStateStore,
    private val json: Json,
) {
    suspend fun isOffline(): Boolean = offline.offlineMode.first()

    /**
     * Offline modda yalnız indirilenler çalınır. Boş dönerse çağıran
     * kullanıcıya söyler (platform bildirimi player tarafında).
     */
    suspend fun filterForOffline(songs: List<Song>): List<Song> {
        if (!isOffline()) return songs
        val ids = downloads.downloadedIds.first().toSet()
        return songs.filter { it.id in ids }
    }

    /**
     * Endless devamı: bağlama göre yeni şarkılar. Offline'da ASLA sunucuya
     * gitmez; sadece indirilenlerden besler (tükenince boş döner → durur).
     */
    suspend fun fetchContinuation(context: PlaybackContext?, chunk: Int = 20): List<Song> =
        runCatching {
            if (isOffline()) {
                return@runCatching downloads.allDownloadedSongs().shuffled()
            }
            when (context) {
                is PlaybackContext.Album -> {
                    // Aynı sanatçının başka bir albümüyle devam
                    val artistId = context.artistId
                    if (artistId != null) {
                        val albums = music.artist(artistId).albums.shuffled()
                        albums.firstOrNull { it.id != context.albumId }?.let { album ->
                            music.album(album.id).songs
                        } ?: music.randomSongs(chunk)
                    } else {
                        music.randomSongs(chunk)
                    }
                }
                is PlaybackContext.Artist ->
                    music.similarSongs(context.artistId, chunk)
                        .ifEmpty { music.randomSongs(chunk) }
                is PlaybackContext.Playlist,
                is PlaybackContext.Genre,
                PlaybackContext.AllSongs,
                null,
                -> music.randomSongs(chunk)
            }
        }.getOrDefault(emptyList())

    // ---- Kuyruk kalıcılığı ----

    suspend fun persist(songs: List<Song>, index: Int, positionMs: Long, contextLabel: String?) {
        runCatching {
            val payload = PersistedQueueState(
                tracks = songs.map { s ->
                    PersistedTrack(
                        id = s.id, title = s.title, artist = s.artist, album = s.album,
                        albumId = s.albumId, artistId = s.artistId, coverArt = s.coverArt,
                        duration = s.duration, suffix = s.suffix, bitRate = s.bitRate,
                        samplingRate = s.samplingRate, starred = s.starred,
                    )
                },
                index = index,
                positionMs = positionMs,
                contextLabel = contextLabel,
            )
            store.save(json.encodeToString(PersistedQueueState.serializer(), payload))
        }
    }

    data class RestoredQueue(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        val contextLabel: String?,
    )

    suspend fun restore(): RestoredQueue? = runCatching {
        val encoded = store.load() ?: return null
        val saved = json.decodeFromString(PersistedQueueState.serializer(), encoded)
        if (saved.tracks.isEmpty()) return null
        RestoredQueue(
            songs = saved.tracks.map { t ->
                Song(
                    id = t.id, title = t.title, album = t.album, albumId = t.albumId,
                    artist = t.artist, artistId = t.artistId, coverArt = t.coverArt,
                    duration = t.duration, track = null, discNumber = null, year = null,
                    bitRate = t.bitRate, suffix = t.suffix, contentType = null,
                    size = null, starred = t.starred, samplingRate = t.samplingRate,
                )
            },
            index = saved.index,
            positionMs = saved.positionMs,
            contextLabel = saved.contextLabel,
        )
    }.getOrNull()

    suspend fun clearPersisted() {
        runCatching { store.clear() }
    }
}

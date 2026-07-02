package com.ozgen.navicloud.data

import com.ozgen.navicloud.core.model.Album
import com.ozgen.navicloud.core.model.Artist
import com.ozgen.navicloud.core.model.HomeSection
import com.ozgen.navicloud.core.model.HomeSectionType
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.core.model.LyricsLine
import com.ozgen.navicloud.core.model.Playlist
import com.ozgen.navicloud.core.model.SearchResult
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.core.network.dto.AlbumDto
import com.ozgen.navicloud.core.network.dto.ArtistDto
import com.ozgen.navicloud.core.network.dto.PlaylistDto
import com.ozgen.navicloud.core.network.dto.SongDto
import com.ozgen.navicloud.core.network.unwrap
import com.ozgen.navicloud.data.db.ApiCacheDao
import com.ozgen.navicloud.data.db.ApiCacheEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private fun AlbumDto.toModel() = Album(
    id = id,
    name = name ?: album ?: title ?: "",
    artist = artist ?: "",
    artistId = artistId,
    coverArt = coverArt,
    songCount = songCount,
    duration = duration,
    year = year,
    genre = genre,
    starred = starred != null,
)

private fun SongDto.toModel() = Song(
    id = id,
    title = title,
    album = album,
    albumId = albumId,
    artist = artist,
    artistId = artistId,
    coverArt = coverArt,
    duration = duration,
    track = track,
    discNumber = discNumber,
    year = year,
    bitRate = bitRate,
    suffix = suffix,
    contentType = contentType,
    size = size,
    starred = starred != null,
    samplingRate = samplingRate,
)

private fun ArtistDto.toModel() = Artist(
    id = id,
    name = name,
    coverArt = coverArt,
    artistImageUrl = artistImageUrl,
    albumCount = albumCount,
    starred = starred != null,
)

private fun PlaylistDto.toModel() = Playlist(
    id = id,
    name = name,
    comment = comment,
    songCount = songCount,
    duration = duration,
    coverArt = coverArt,
)

@Serializable
data class AlbumDetail(val album: Album, val songs: List<Song>)
@Serializable
data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val biography: String?,
    val similar: List<Artist>,
    /** Real artist photo URL if the server has one; placeholders filtered out. */
    val imageUrl: String? = null,
)

// Last.fm's well-known star placeholder — Navidrome serves it when no agent is configured
private const val LASTFM_PLACEHOLDER_HASH = "2a96cbd8b46e442fc41c2b86b821562f"

private fun String?.realImageUrlOrNull(): String? =
    this?.takeIf { it.isNotBlank() && !it.contains(LASTFM_PLACEHOLDER_HASH) }
@Serializable
data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

// TTL'ler: entity ne kadar oynaksa o kadar kısa
private const val TTL_HOME = 30L * 60 * 1000
private const val TTL_LISTS = 6L * 60 * 60 * 1000
private const val TTL_STABLE = 24L * 60 * 60 * 1000
private const val TTL_SEARCH = 15L * 60 * 1000
private const val TTL_LYRICS = 7L * 24 * 60 * 60 * 1000

@Singleton
class MusicRepository @Inject constructor(
    private val servers: ServerRepository,
    private val cacheDao: ApiCacheDao,
    private val json: Json,
    private val settings: SettingsRepository,
) {
    private suspend fun api() = servers.activeClient().api

    private suspend fun serverPrefix(): String =
        "${servers.activeServer.first()?.id ?: 0}:"

    /**
     * Offline-first okuma: TTL içinde cache'ten (ağa hiç çıkmadan), bayatsa
     * ağdan çekip cache'i tazeler; ağ hatasında bayat cache'e düşer. Offline
     * modda her zaman cache — yoksa açık hata. force=true (pull-to-refresh)
     * TTL'i atlar ama hata durumunda yine cache'e düşer.
     */
    private suspend inline fun <reified T> cached(
        key: String,
        ttlMs: Long,
        force: Boolean = false,
        fetch: () -> T,
    ): T {
        val fullKey = serverPrefix() + key
        val offline = settings.offlineMode.first()
        val entry = cacheDao.get(fullKey)
        val decoded: T? = entry?.let { e ->
            runCatching { json.decodeFromString<T>(e.json) }.getOrNull()
        }
        val fresh = entry != null && System.currentTimeMillis() - entry.updatedAt < ttlMs
        if (decoded != null && (offline || (fresh && !force))) return decoded
        if (offline) throw IllegalStateException("Çevrimdışı: bu içerik önbellekte yok")
        return try {
            val value = fetch()
            cacheDao.put(ApiCacheEntity(fullKey, json.encodeToString(value), System.currentTimeMillis()))
            value
        } catch (e: Exception) {
            decoded ?: throw e
        }
    }

    /** Mutasyon sonrası ilgili cache anahtarlarını düşürür (prefix eşleşmesi). */
    private suspend fun invalidate(vararg keyPrefixes: String) {
        val prefix = serverPrefix()
        keyPrefixes.forEach { cacheDao.deleteByPrefix(prefix + it) }
    }

    suspend fun homeSections(force: Boolean = false): List<HomeSection> =
        cached("home", TTL_HOME, force) {
            coroutineScope {
                HomeSectionType.entries.map { type ->
                    async {
                        runCatching {
                            val albums = api().getAlbumList2(type.subsonicType, size = 20)
                                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
                            HomeSection(type, albums)
                        }.getOrElse { HomeSection(type, emptyList()) }
                    }
                }.map { it.await() }.filter { it.albums.isNotEmpty() }
            }
        }

    suspend fun albumList(type: HomeSectionType, size: Int = 50, offset: Int = 0, force: Boolean = false): List<Album> =
        cached("albums:${type.name}:$size:$offset", TTL_LISTS, force) {
            api().getAlbumList2(type.subsonicType, size, offset)
                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
        }

    suspend fun albumsAlphabetical(size: Int = 100, offset: Int = 0, force: Boolean = false): List<Album> =
        cached("albums:alpha:$size:$offset", TTL_STABLE, force) {
            api().getAlbumList2("alphabeticalByName", size, offset)
                .unwrap().albumList2?.album.orEmpty().map { it.toModel() }
        }

    suspend fun album(id: String, force: Boolean = false): AlbumDetail =
        cached("album:$id", TTL_STABLE, force) {
            val dto = api().getAlbum(id).unwrap().album
                ?: throw IllegalStateException("Albüm bulunamadı")
            AlbumDetail(dto.toModel(), dto.song.map { it.toModel() })
        }

    suspend fun artists(force: Boolean = false): List<Artist> =
        cached("artists", TTL_STABLE, force) {
            api().getArtists().unwrap().artists?.index.orEmpty()
                .flatMap { it.artist }.map { it.toModel() }
        }

    suspend fun artist(id: String, force: Boolean = false): ArtistDetail =
        cached("artist:$id", TTL_STABLE, force) {
            coroutineScope {
                val artistDeferred = async { api().getArtist(id).unwrap().artist }
                val infoDeferred = async {
                    runCatching { api().getArtistInfo2(id).unwrap().artistInfo2 }.getOrNull()
                }
                val dto = artistDeferred.await() ?: throw IllegalStateException("Sanatçı bulunamadı")
                val info = infoDeferred.await()
                ArtistDetail(
                    artist = dto.toModel(),
                    albums = dto.album.map { it.toModel() },
                    biography = info?.biography?.takeIf { it.isNotBlank() },
                    similar = info?.similarArtist.orEmpty().map { it.toModel() },
                    imageUrl = info?.largeImageUrl.realImageUrlOrNull()
                        ?: dto.artistImageUrl.realImageUrlOrNull(),
                )
            }
        }

    suspend fun topSongs(artistName: String, force: Boolean = false): List<Song> =
        runCatching {
            cached("topsongs:$artistName", TTL_STABLE, force) {
                api().getTopSongs(artistName).unwrap().topSongs?.song.orEmpty().map { it.toModel() }
            }
        }.getOrElse { emptyList() }

    suspend fun playlists(force: Boolean = false): List<Playlist> =
        cached("playlists", TTL_LISTS, force) {
            api().getPlaylists().unwrap().playlists?.playlist.orEmpty().map { it.toModel() }
        }

    suspend fun playlist(id: String, force: Boolean = false): PlaylistDetail =
        cached("playlist:$id", TTL_LISTS, force) {
            val dto = api().getPlaylist(id).unwrap().playlist
                ?: throw IllegalStateException("Çalma listesi bulunamadı")
            PlaylistDetail(dto.toModel(), dto.entry.map { it.toModel() })
        }

    suspend fun search(query: String): SearchResult =
        cached("search:$query", TTL_SEARCH) {
            val r = api().search3(query).unwrap().searchResult3
            SearchResult(
                artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
                albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
                songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
            )
        }

    suspend fun starred(force: Boolean = false): SearchResult =
        cached("starred", TTL_SEARCH, force) {
            val r = api().getStarred2().unwrap().starred2
            SearchResult(
                artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
                albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
                songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
            )
        }

    /** Full library song list, paged. Navidrome returns everything for an empty query. */
    suspend fun allSongs(offset: Int, size: Int = 200, force: Boolean = false): List<Song> =
        cached("allsongs:$offset:$size", TTL_STABLE, force) {
            api().search3("", artistCount = 0, albumCount = 0, songCount = size, songOffset = offset)
                .unwrap().searchResult3?.song.orEmpty().map { it.toModel() }
        }

    suspend fun randomSongs(size: Int = 50): List<Song> =
        api().getRandomSongs(size).unwrap().randomSongs?.song.orEmpty().map { it.toModel() }

    suspend fun setStarred(starred: Boolean, songId: String? = null, albumId: String? = null, artistId: String? = null) {
        if (starred) api().star(songId, albumId, artistId).unwrap()
        else api().unstar(songId, albumId, artistId).unwrap()
        // Favori değişikliği görünen listeleri etkiler — ilgili cache'ler düşer
        invalidate("starred", "home")
        albumId?.let { invalidate("album:$it") }
        artistId?.let { invalidate("artist:$it") }
    }

    suspend fun scrobble(songId: String, submission: Boolean) {
        runCatching { api().scrobble(songId, submission).unwrap() }
    }

    suspend fun addToPlaylist(playlistId: String, songId: String) {
        api().updatePlaylist(playlistId, songIdToAdd = songId).unwrap()
        invalidate("playlist:$playlistId", "playlists")
    }

    suspend fun similarSongs(artistId: String, count: Int = 25): List<Song> =
        runCatching {
            api().getSimilarSongs2(artistId, count).unwrap().similarSongs2?.song.orEmpty().map { it.toModel() }
        }.getOrElse { emptyList() }

    suspend fun lyrics(songId: String): Lyrics? = runCatching {
        cached<Lyrics?>("lyrics:$songId", TTL_LYRICS) {
            val list = runCatching {
                api().getLyricsBySongId(songId).unwrap().lyricsList?.structuredLyrics
            }.getOrNull().orEmpty()
            val best = list.firstOrNull { it.synced } ?: list.firstOrNull()
            best?.let {
                Lyrics(synced = it.synced, lines = it.line.map { l -> LyricsLine(l.start, l.value) })
            }
        }
    }.getOrNull()

    suspend fun coverArtUrl(coverArtId: String?, size: Int? = null): String? =
        coverArtId?.let { servers.activeClient().coverArtUrl(it, size) }

    suspend fun streamUrl(songId: String, maxKbps: Int? = null, format: String? = null): String =
        servers.activeClient().streamUrl(songId, maxKbps, format)

    suspend fun startScan(fullScan: Boolean): Pair<Boolean, Long> {
        val s = api().startScan(fullScan).unwrap().scanStatus
        return (s?.scanning ?: true) to (s?.count ?: 0)
    }

    suspend fun scanStatus(): Pair<Boolean, Long> {
        val s = api().getScanStatus().unwrap().scanStatus
        return (s?.scanning ?: false) to (s?.count ?: 0)
    }
}

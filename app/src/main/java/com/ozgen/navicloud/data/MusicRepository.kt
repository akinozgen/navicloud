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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

data class AlbumDetail(val album: Album, val songs: List<Song>)
data class ArtistDetail(
    val artist: Artist,
    val albums: List<Album>,
    val biography: String?,
    val similar: List<Artist>,
)
data class PlaylistDetail(val playlist: Playlist, val songs: List<Song>)

@Singleton
class MusicRepository @Inject constructor(
    private val servers: ServerRepository,
) {
    private suspend fun api() = servers.activeClient().api

    suspend fun homeSections(): List<HomeSection> = coroutineScope {
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

    suspend fun albumList(type: HomeSectionType, size: Int = 50, offset: Int = 0): List<Album> =
        api().getAlbumList2(type.subsonicType, size, offset)
            .unwrap().albumList2?.album.orEmpty().map { it.toModel() }

    suspend fun albumsAlphabetical(size: Int = 100, offset: Int = 0): List<Album> =
        api().getAlbumList2("alphabeticalByName", size, offset)
            .unwrap().albumList2?.album.orEmpty().map { it.toModel() }

    suspend fun album(id: String): AlbumDetail {
        val dto = api().getAlbum(id).unwrap().album
            ?: throw IllegalStateException("Albüm bulunamadı")
        return AlbumDetail(dto.toModel(), dto.song.map { it.toModel() })
    }

    suspend fun artists(): List<Artist> =
        api().getArtists().unwrap().artists?.index.orEmpty()
            .flatMap { it.artist }.map { it.toModel() }

    suspend fun artist(id: String): ArtistDetail = coroutineScope {
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
        )
    }

    suspend fun topSongs(artistName: String): List<Song> =
        runCatching {
            api().getTopSongs(artistName).unwrap().topSongs?.song.orEmpty().map { it.toModel() }
        }.getOrElse { emptyList() }

    suspend fun playlists(): List<Playlist> =
        api().getPlaylists().unwrap().playlists?.playlist.orEmpty().map { it.toModel() }

    suspend fun playlist(id: String): PlaylistDetail {
        val dto = api().getPlaylist(id).unwrap().playlist
            ?: throw IllegalStateException("Çalma listesi bulunamadı")
        return PlaylistDetail(dto.toModel(), dto.entry.map { it.toModel() })
    }

    suspend fun search(query: String): SearchResult {
        val r = api().search3(query).unwrap().searchResult3
        return SearchResult(
            artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
            albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
            songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
        )
    }

    suspend fun starred(): SearchResult {
        val r = api().getStarred2().unwrap().starred2
        return SearchResult(
            artists = r?.artist.orEmpty().map { it.toModel() }.distinctBy { it.id },
            albums = r?.album.orEmpty().map { it.toModel() }.distinctBy { it.id },
            songs = r?.song.orEmpty().map { it.toModel() }.distinctBy { it.id },
        )
    }

    /** Full library song list, paged. Navidrome returns everything for an empty query. */
    suspend fun allSongs(offset: Int, size: Int = 200): List<Song> =
        api().search3("", artistCount = 0, albumCount = 0, songCount = size, songOffset = offset)
            .unwrap().searchResult3?.song.orEmpty().map { it.toModel() }

    suspend fun randomSongs(size: Int = 50): List<Song> =
        api().getRandomSongs(size).unwrap().randomSongs?.song.orEmpty().map { it.toModel() }

    suspend fun setStarred(starred: Boolean, songId: String? = null, albumId: String? = null, artistId: String? = null) {
        if (starred) api().star(songId, albumId, artistId).unwrap()
        else api().unstar(songId, albumId, artistId).unwrap()
    }

    suspend fun scrobble(songId: String, submission: Boolean) {
        runCatching { api().scrobble(songId, submission).unwrap() }
    }

    suspend fun lyrics(songId: String): Lyrics? {
        val list = runCatching {
            api().getLyricsBySongId(songId).unwrap().lyricsList?.structuredLyrics
        }.getOrNull().orEmpty()
        val best = list.firstOrNull { it.synced } ?: list.firstOrNull() ?: return null
        return Lyrics(
            synced = best.synced,
            lines = best.line.map { LyricsLine(it.start, it.value) },
        )
    }

    suspend fun coverArtUrl(coverArtId: String?, size: Int? = null): String? =
        coverArtId?.let { servers.activeClient().coverArtUrl(it, size) }

    suspend fun streamUrl(songId: String): String = servers.activeClient().streamUrl(songId)
}

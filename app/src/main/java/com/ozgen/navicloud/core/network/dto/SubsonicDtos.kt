package com.ozgen.navicloud.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicEnvelope(
    @SerialName("subsonic-response") val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String = "failed",
    val error: ErrorDto? = null,
    val albumList2: AlbumList2Dto? = null,
    val album: AlbumDto? = null,
    val artists: ArtistsDto? = null,
    val artist: ArtistDto? = null,
    val artistInfo2: ArtistInfo2Dto? = null,
    val playlists: PlaylistsDto? = null,
    val playlist: PlaylistDto? = null,
    val searchResult3: SearchResult3Dto? = null,
    val starred2: Starred2Dto? = null,
    val randomSongs: SongListDto? = null,
    val topSongs: TopSongsDto? = null,
    val lyricsList: LyricsListDto? = null,
    val scanStatus: ScanStatusDto? = null,
)

@Serializable
data class ErrorDto(val code: Int = 0, val message: String? = null)

@Serializable
data class AlbumList2Dto(val album: List<AlbumDto> = emptyList())

@Serializable
data class AlbumDto(
    val id: String,
    val name: String? = null,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class SongDto(
    val id: String,
    val title: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val bitRate: Int? = null,
    val suffix: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
    val starred: String? = null,
)

@Serializable
data class ArtistsDto(val index: List<ArtistIndexDto> = emptyList())

@Serializable
data class ArtistIndexDto(val name: String = "", val artist: List<ArtistDto> = emptyList())

@Serializable
data class ArtistDto(
    val id: String,
    val name: String = "",
    val coverArt: String? = null,
    val artistImageUrl: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    val album: List<AlbumDto> = emptyList(),
)

@Serializable
data class ArtistInfo2Dto(
    val biography: String? = null,
    val largeImageUrl: String? = null,
    val similarArtist: List<ArtistDto> = emptyList(),
)

@Serializable
data class PlaylistsDto(val playlist: List<PlaylistDto> = emptyList())

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String = "",
    val comment: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val entry: List<SongDto> = emptyList(),
)

@Serializable
data class SearchResult3Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class Starred2Dto(
    val artist: List<ArtistDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class SongListDto(val song: List<SongDto> = emptyList())

@Serializable
data class TopSongsDto(val song: List<SongDto> = emptyList())

@Serializable
data class LyricsListDto(val structuredLyrics: List<StructuredLyricsDto> = emptyList())

@Serializable
data class StructuredLyricsDto(
    val lang: String? = null,
    val synced: Boolean = false,
    val line: List<LyricsLineDto> = emptyList(),
)

@Serializable
data class LyricsLineDto(val start: Long? = null, val value: String = "")

@Serializable
data class ScanStatusDto(val scanning: Boolean = false, val count: Long = 0)

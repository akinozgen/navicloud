package com.ozgen.navicloud.core.model

import kotlinx.serialization.Serializable

data class Server(
    val id: Long,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
)

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String?,
    val coverArt: String?,
    val songCount: Int,
    val duration: Int,
    val year: Int?,
    val genre: String?,
    val starred: Boolean,
)

@Serializable
data class Song(
    val id: String,
    val title: String,
    val album: String?,
    val albumId: String?,
    val artist: String?,
    val artistId: String?,
    val coverArt: String?,
    val duration: Int,
    val track: Int?,
    val discNumber: Int?,
    val year: Int?,
    val bitRate: Int?,
    val suffix: String?,
    val contentType: String?,
    val size: Long?,
    val starred: Boolean,
    val samplingRate: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val coverArt: String?,
    val artistImageUrl: String?,
    val albumCount: Int,
    val starred: Boolean,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val comment: String?,
    val songCount: Int,
    val duration: Int,
    val coverArt: String?,
)

@Serializable
data class SearchResult(
    val artists: List<Artist>,
    val albums: List<Album>,
    val songs: List<Song>,
)

@Serializable
data class LyricsLine(val startMs: Long?, val text: String)

@Serializable
data class Lyrics(
    val synced: Boolean,
    val lines: List<LyricsLine>,
)

enum class HomeSectionType(val subsonicType: String, val title: String) {
    RECENT("recent", "Son çalınanlar"),
    NEWEST("newest", "Yeni eklenenler"),
    FREQUENT("frequent", "Sık çalınanlar"),
    RANDOM("random", "Senin için karışık"),
    STARRED("starred", "Favori albümler"),
}

@Serializable
data class HomeSection(val type: HomeSectionType, val albums: List<Album>)

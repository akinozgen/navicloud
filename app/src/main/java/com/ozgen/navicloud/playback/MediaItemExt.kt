package com.ozgen.navicloud.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.ozgen.navicloud.core.model.Song

object MediaKeys {
    const val ALBUM_ID = "albumId"
    const val ARTIST_ID = "artistId"
    const val COVER_ART = "coverArt"
    const val DURATION = "durationSec"
    const val STARRED = "starred"
}

fun Song.toMediaItem(streamUrl: String, artworkUrl: String?): MediaItem {
    val extras = Bundle().apply {
        putString(MediaKeys.ALBUM_ID, albumId)
        putString(MediaKeys.ARTIST_ID, artistId)
        putString(MediaKeys.COVER_ART, coverArt)
        putInt(MediaKeys.DURATION, duration)
        putBoolean(MediaKeys.STARRED, starred)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUrl?.toUri())
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(Uri.parse(streamUrl))
        .setMediaMetadata(metadata)
        .build()
}

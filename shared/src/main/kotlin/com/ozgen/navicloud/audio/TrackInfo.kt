package com.ozgen.navicloud.audio

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.StreamQuality

/**
 * Parça teknik bilgisi — platformdan bağımsız alan seti. Kaynak alanlar
 * Subsonic metadata'sından; "çıkış" alanları akış kalitesi/transcode
 * durumundan türetilir. Boş alan gösterilmez (UI null'ları atlar).
 */
data class TrackInfo(
    val title: String,
    val artist: String?,
    val album: String?,
    /** Kaynak dosya. */
    val codec: String?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val channels: Int?,
    val bitDepth: Int?,
    val durationSec: Int,
    val sizeBytes: Long?,
    /** true → sunucu bu parçayı transcode ederek gönderir (kaynak → çıkış). */
    val transcoded: Boolean,
    val outputCodec: String?,
    val outputBitrateKbps: Int?,
)

/**
 * [Song] + aktif akış kalitesinden [TrackInfo] üretir.
 *
 * @param isLocal parça indirilmişse true → ham dosya çalınır, transcode YOK
 *   (kalite ayarı yoksayılır).
 */
fun buildTrackInfo(song: Song, quality: StreamQuality, isLocal: Boolean): TrackInfo {
    val transcoded = !isLocal && quality.kbps != null
    return TrackInfo(
        title = song.title,
        artist = song.artist,
        album = song.album,
        codec = (song.suffix ?: song.contentType)?.uppercase(),
        bitrateKbps = song.bitRate,
        sampleRateHz = song.samplingRate,
        channels = song.channelCount,
        bitDepth = song.bitDepth,
        durationSec = song.duration,
        sizeBytes = song.size,
        transcoded = transcoded,
        outputCodec = if (transcoded) "MP3" else null,
        outputBitrateKbps = if (transcoded) quality.kbps else null,
    )
}

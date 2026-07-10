package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.audio.buildTrackInfo
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.i18n.Strings
import com.ozgen.navicloud.ui.i18n.LocalStrings

/**
 * Parça teknik bilgisi bottom sheet'i. Değerler Subsonic metadata'sından;
 * "Akış" satırı transcode varsa "kaynak → çıkış" gösterir. Boş alanlar atlanır.
 */
@Composable
fun TrackInfoSheet(song: Song, quality: StreamQuality, isLocal: Boolean) {
    val strings = LocalStrings.current
    val info = remember(song.id, quality, isLocal) { buildTrackInfo(song, quality, isLocal) }
    val navBarPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = navBarPad + 16.dp)) {
        Text(
            info.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val subtitle = listOfNotNull(info.artist, info.album).joinToString(" • ")
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        InfoRow(strings.trackInfoFormat, info.codec)
        InfoRow(strings.trackInfoBitRate, info.bitrateKbps?.let { "$it kbps" })
        InfoRow(strings.trackInfoSampleRate, info.sampleRateHz?.let { "%.1f kHz".format(it / 1000f) })
        InfoRow(strings.trackInfoBitDepth, info.bitDepth?.let { "$it bit" })
        InfoRow(strings.trackInfoChannels, info.channels?.let { channelLabel(it, strings) })
        InfoRow(strings.trackInfoDuration, formatDuration(info.durationSec))
        InfoRow(strings.trackInfoSize, info.sizeBytes?.let { formatBytes(it) })

        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        if (info.transcoded) {
            val source = listOfNotNull(info.codec, info.bitrateKbps?.let { "$it kbps" }).joinToString(" • ")
            val output = listOfNotNull(info.outputCodec, info.outputBitrateKbps?.let { "$it kbps" }).joinToString(" • ")
            InfoRow(strings.trackInfoStream, "$source  →  $output")
        } else {
            InfoRow(strings.trackInfoStream, strings.trackInfoStreamOriginal)
        }
    }
}

/** Etiket solda (soluk), değer sağda. value null ise satır çizilmez. */
@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun channelLabel(n: Int, strings: Strings): String = when (n) {
    1 -> strings.trackInfoChannelMono
    2 -> strings.trackInfoChannelStereo
    else -> strings.trackInfoChannelOther(n)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

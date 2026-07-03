package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.core.model.Song
import kotlin.random.Random

/**
 * Playlist cover: 2x2 mosaic of 4 covers picked deterministically from the
 * playlist id — same picks on every render.
 */
@Composable
fun PlaylistCoverMosaic(
    playlistId: String,
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    val picks = remember(playlistId, songs.size) {
        songs.mapNotNull { it.coverArt }.distinct()
            .shuffled(Random(playlistId.hashCode()))
            .take(4)
    }
    if (picks.size < 4) {
        Artwork(picks.firstOrNull(), sizePx = 800, cornerRadius = 12.dp, modifier = modifier)
        return
    }
    Column(modifier.clip(RoundedCornerShape(12.dp))) {
        Row(Modifier.weight(1f)) {
            Artwork(picks[0], sizePx = 300, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            Artwork(picks[1], sizePx = 300, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
        }
        Row(Modifier.weight(1f)) {
            Artwork(picks[2], sizePx = 300, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
            Artwork(picks[3], sizePx = 300, cornerRadius = 0.dp, modifier = Modifier.weight(1f).fillMaxSize())
        }
    }
}

enum class DownloadState { NONE, DOWNLOADING, DONE }

/**
 * Album/playlist action row: options menu on the left, big play in the middle,
 * stateful download button on the right.
 */
@Composable
fun CollectionActionRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
    /** This collection is the active playback context and playing → show pause. */
    isPlaying: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Seçenekler",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Karıştırarak çal") },
                    leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                    onClick = { menuOpen = false; onShuffle() },
                )
                DropdownMenuItem(
                    text = { Text("Sıradakine ekle") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
                    onClick = { menuOpen = false; onPlayNext() },
                )
                DropdownMenuItem(
                    text = { Text("Kuyruğa ekle") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    onClick = { menuOpen = false; onAddToQueue() },
                )
                if (downloadState != DownloadState.NONE) {
                    DropdownMenuItem(
                        text = { Text("İndirilenleri kaldır") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; onRemoveDownload() },
                    )
                }
            }
        }

        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Duraklat" else "Çal",
                modifier = Modifier.size(36.dp),
            )
        }

        when (downloadState) {
            DownloadState.DOWNLOADING -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            DownloadState.DONE -> IconButton(onClick = onRemoveDownload) {
                Icon(
                    Icons.Rounded.DownloadDone,
                    contentDescription = "İndirildi",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DownloadState.NONE -> IconButton(onClick = onDownload) {
                Icon(
                    Icons.Rounded.DownloadForOffline,
                    contentDescription = "İndir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

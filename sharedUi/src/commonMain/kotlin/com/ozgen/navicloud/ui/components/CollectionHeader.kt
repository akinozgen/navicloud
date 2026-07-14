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
import androidx.compose.material.icons.rounded.Edit
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
import com.ozgen.navicloud.ui.i18n.LocalStrings
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
    /** Playlist: overflow'a "Yeniden adlandır" ekler (albüm ekranı geçmez → değişmez). */
    onRename: (() -> Unit)? = null,
    /** Playlist: overflow'a kırmızı "Listeyi sil" ekler. */
    onDelete: (() -> Unit)? = null,
    /** Boş koleksiyonda çalma/indirme kapalı; overflow (sil/adlandır) AKTİF kalır. */
    playbackEnabled: Boolean = true,
) {
    val strings = LocalStrings.current
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
                    contentDescription = strings.commonOptions,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(strings.collectionShufflePlay) },
                    leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                    enabled = playbackEnabled,
                    onClick = { menuOpen = false; onShuffle() },
                )
                DropdownMenuItem(
                    text = { Text(strings.commonPlayNext) },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
                    enabled = playbackEnabled,
                    onClick = { menuOpen = false; onPlayNext() },
                )
                DropdownMenuItem(
                    text = { Text(strings.commonAddToQueue) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                    enabled = playbackEnabled,
                    onClick = { menuOpen = false; onAddToQueue() },
                )
                if (onRename != null) {
                    DropdownMenuItem(
                        text = { Text(strings.playlistRename) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; onRename() },
                    )
                }
                if (downloadState != DownloadState.NONE) {
                    DropdownMenuItem(
                        text = { Text(strings.collectionRemoveDownloads) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; onRemoveDownload() },
                    )
                }
                if (onDelete != null) {
                    // Tehlike bölgesi: en altta, error renginde
                    DropdownMenuItem(
                        text = { Text(strings.playlistDelete, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }

        FilledIconButton(
            onClick = onPlay,
            enabled = playbackEnabled,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) strings.commonPause else strings.commonPlay,
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
                    contentDescription = strings.collectionDownloaded,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DownloadState.NONE -> IconButton(onClick = onDownload, enabled = playbackEnabled) {
                Icon(
                    Icons.Rounded.DownloadForOffline,
                    contentDescription = strings.commonDownload,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

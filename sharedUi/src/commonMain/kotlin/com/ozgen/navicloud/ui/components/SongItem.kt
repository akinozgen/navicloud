package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozgen.navicloud.core.model.Song

/**
 * Central song actions. Provided once (MainShell), consumed by every SongItem.
 * playNext / addToQueue semantics live in PlayerController — these lambdas just delegate.
 */
data class SongMenuActions(
    val playNext: (Song) -> Unit,
    val addToQueue: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
    val goToAlbum: (String) -> Unit,
    val goToArtist: (String) -> Unit,
    val download: (Song) -> Unit,
    val removeDownload: (String) -> Unit,
    val setStarred: (Song, Boolean) -> Unit,
    val removeFromQueue: (String) -> Unit,
    val isDownloaded: (String) -> Boolean,
    val showInfo: (Song) -> Unit,
)

val LocalSongMenu = staticCompositionLocalOf<SongMenuActions?> { null }

/** The one true song row. Every song list in the app renders this. */
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
    trackNumber: Int? = null,
    highlighted: Boolean = false,
    inQueue: Boolean = false,
    queueUid: String? = null,
    /** null = off; true/false = show equalizer bars (animating / frozen). */
    playingBars: Boolean? = null,
    barsTint: Color? = null,
    trailingContent: (@Composable () -> Unit)? = null,
)  {
    val actions = LocalSongMenu.current
    var menuOpen by remember { mutableStateOf(false) }
    // Star state is server-side; track the toggle locally so the menu reflects it immediately
    var starred by rememberSaveable(song.id) { mutableStateOf(song.starred) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (trackNumber != null) {
            Text(
                trackNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp),
            )
        } else if (showArt) {
            Box {
                Artwork(song.coverArt, sizePx = 150, cornerRadius = 6.dp, modifier = Modifier.size(48.dp))
                if (playingBars != null) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(
                                Color.Black.copy(alpha = 0.45f),
                                androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayingBars(
                            playing = playingBars,
                            tint = barsTint ?: MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.artist, formatDuration(song.duration)).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.invoke()
        if (actions != null) {
            // Menu must be anchored inside a Box with its button; as a bare Row
            // child it takes layout space (icons shift) and anchors at the wrong edge
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Şarkı menüsü",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SongContextMenu(
                    song = song,
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    inQueue = inQueue,
                    queueUid = queueUid,
                )
            }
        }
    }
}

/** The shared song context menu — anchor it inside a Box with its trigger. */
@Composable
fun SongContextMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    inQueue: Boolean = false,
    queueUid: String? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SongContextMenuItems(song, onDismiss, inQueue, queueUid)
    }
}

/**
 * Şarkı menüsü öğeleri — bir DropdownMenu içeriğinde yer alır. Player overflow'u
 * gibi başka menüler de kendi öğelerinin ardına bunları ekleyebilir.
 */
@Composable
fun SongContextMenuItems(
    song: Song,
    onDismiss: () -> Unit,
    inQueue: Boolean = false,
    queueUid: String? = null,
) {
    val actions = LocalSongMenu.current ?: return
    var starred by rememberSaveable(song.id) { mutableStateOf(song.starred) }

    run {
        DropdownMenuItem(
            text = { Text("Sıradakine ekle") },
            leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, null) },
            onClick = { onDismiss(); actions.playNext(song) },
        )
        DropdownMenuItem(
            text = { Text("Kuyruğa ekle") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
            onClick = { onDismiss(); actions.addToQueue(song) },
        )
        DropdownMenuItem(
            text = { Text("Çalma listesine ekle") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = { onDismiss(); actions.addToPlaylist(song) },
        )
        song.albumId?.let { albumId ->
            DropdownMenuItem(
                text = { Text("Albüme git") },
                leadingIcon = { Icon(Icons.Rounded.Album, null) },
                onClick = { onDismiss(); actions.goToAlbum(albumId) },
            )
        }
        song.artistId?.let { artistId ->
            DropdownMenuItem(
                text = { Text("Sanatçıya git") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                onClick = { onDismiss(); actions.goToArtist(artistId) },
            )
        }
        if (inQueue && queueUid != null) {
            DropdownMenuItem(
                text = { Text("Kuyruktan kaldır") },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                onClick = { onDismiss(); actions.removeFromQueue(queueUid) },
            )
        }
        if (actions.isDownloaded(song.id)) {
            DropdownMenuItem(
                text = { Text("İndirileni kaldır") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { onDismiss(); actions.removeDownload(song.id) },
            )
        } else {
            DropdownMenuItem(
                text = { Text("İndir") },
                leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, null) },
                onClick = { onDismiss(); actions.download(song) },
            )
        }
        DropdownMenuItem(
            text = { Text(if (starred) "Favorilerden çıkar" else "Favorilere ekle") },
            leadingIcon = {
                Icon(if (starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
            },
            onClick = {
                onDismiss()
                starred = !starred
                actions.setStarred(song, starred)
            },
        )
        DropdownMenuItem(
            text = { Text("Bilgi") },
            leadingIcon = { Icon(Icons.Rounded.Info, null) },
            onClick = { onDismiss(); actions.showInfo(song) },
        )
    }
}

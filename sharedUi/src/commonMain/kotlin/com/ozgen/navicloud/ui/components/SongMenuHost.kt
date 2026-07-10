package com.ozgen.navicloud.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozgen.navicloud.ui.containerViewModel
import com.ozgen.navicloud.ui.i18n.LocalStrings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Playlist
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.rememberToaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SongMenuViewModel(
    val player: PlayerController,
    private val downloads: DownloadsPort,
    private val repo: MusicRepository,
) : ViewModel() {

    val downloadedIds: StateFlow<Set<String>> =
        downloads.downloadedIds.map { it.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    fun loadPlaylists() {
        viewModelScope.launch {
            runCatching { repo.playlists() }.onSuccess { _playlists.value = it }
        }
    }

    fun addToPlaylist(playlistId: String, song: Song, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { repo.addToPlaylist(playlistId, song.id) }.isSuccess
            onDone(ok)
        }
    }

    fun setStarred(song: Song, starred: Boolean) {
        viewModelScope.launch { runCatching { repo.setStarred(starred, songId = song.id) } }
    }

    fun download(song: Song) = downloads.enqueue(listOf(song))
    fun removeDownload(songId: String) = viewModelScope.launch { downloads.delete(songId) }
}

/**
 * Provides [LocalSongMenu] to everything underneath and hosts the
 * add-to-playlist picker dialog. Wrap the app shell with this once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuHost(
    navController: NavController,
    onBeforeNavigate: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val vm: SongMenuViewModel = containerViewModel { SongMenuViewModel(it.player, it.downloads, it.music) }
    val toast = rememberToaster()
    val strings = LocalStrings.current
    val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var infoSong by remember { mutableStateOf<Song?>(null) }

    val actions = remember(navController, strings) {
        SongMenuActions(
            playNext = { song ->
                vm.player.playNext(listOf(song))
                toast(strings.songMenuToastPlayNext)
            },
            addToQueue = { song ->
                vm.player.addToQueue(listOf(song))
                toast(strings.songMenuToastAddedToQueue)
            },
            addToPlaylist = { song ->
                vm.loadPlaylists()
                playlistPickerSong = song
            },
            // Navigating from an open player/queue must dismiss the overlay first
            goToAlbum = { albumId ->
                onBeforeNavigate()
                navController.navigate("album/$albumId")
            },
            goToArtist = { artistId ->
                onBeforeNavigate()
                navController.navigate("artist/$artistId")
            },
            download = { song ->
                vm.download(song)
                toast(strings.commonDownloadQueued)
            },
            removeDownload = { songId -> vm.removeDownload(songId) },
            setStarred = { song, starred -> vm.setStarred(song, starred) },
            removeFromQueue = { uid -> vm.player.removeQueueItemByUid(uid) },
            isDownloaded = { songId -> songId in downloadedIds },
            showInfo = { song -> infoSong = song },
        )
    }

    CompositionLocalProvider(LocalSongMenu provides actions) {
        content()
    }

    val pickerSong = playlistPickerSong
    if (pickerSong != null) {
        val playlists by vm.playlists.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { playlistPickerSong = null },
            title = { Text(strings.songMenuAddToPlaylist) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (playlists.isEmpty()) {
                        Text(strings.songMenuPlaylistsLoading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    playlists.forEach { pl ->
                        Text(
                            pl.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.addToPlaylist(pl.id, pickerSong) { ok ->
                                        toast(if (ok) strings.songMenuToastAddedToPlaylist(pl.name) else strings.songMenuToastAddFailed)
                                    }
                                    playlistPickerSong = null
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { playlistPickerSong = null }) { Text(strings.commonCancel) }
            },
        )
    }

    val info = infoSong
    if (info != null) {
        val quality by vm.player.streamQuality.collectAsStateWithLifecycle()
        ModalBottomSheet(onDismissRequest = { infoSong = null }) {
            TrackInfoSheet(
                song = info,
                quality = quality,
                isLocal = info.id in downloadedIds,
            )
        }
    }
}

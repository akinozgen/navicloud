package com.ozgen.navicloud.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.data.AlbumDetail
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AmbientBackdrop
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.CollectionActionRow
import com.ozgen.navicloud.ui.components.DownloadState
import com.ozgen.navicloud.ui.components.SongItem
import com.ozgen.navicloud.ui.components.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val detail: AlbumDetail? = null,
    val starred: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val downloadRepo: com.ozgen.navicloud.data.DownloadRepository,
    val player: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val albumId: String = savedStateHandle.get<String>("id").orEmpty()
    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state

    init {
        viewModelScope.launch {
            runCatching { repo.album(albumId) }
                .onSuccess {
                    _state.value = AlbumUiState(loading = false, detail = it, starred = it.album.starred)
                }
                .onFailure { _state.value = AlbumUiState(loading = false, error = it.message) }
        }
    }

    fun downloadAll(): Boolean {
        val detail = _state.value.detail ?: return false
        downloadRepo.enqueue(detail.songs)
        return true
    }

    fun playbackContext(): PlaybackContext =
        PlaybackContext.Album(albumId, _state.value.detail?.album?.artistId)

    fun contextLabel(): String? = _state.value.detail?.album?.name

    val downloadedIds = downloadRepo.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet<String>())
    val activeDownload = downloadRepo.active

    fun removeDownloads() {
        val songs = _state.value.detail?.songs ?: return
        viewModelScope.launch { songs.forEach { downloadRepo.delete(it.id) } }
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            runCatching { repo.album(albumId, force = true) }
                .onSuccess {
                    _state.value = _state.value.copy(refreshing = false, detail = it, starred = it.album.starred)
                }
                .onFailure { _state.value = _state.value.copy(refreshing = false) }
        }
    }

    fun toggleStar() {
        val current = _state.value
        val newValue = !current.starred
        _state.value = current.copy(starred = newValue)
        viewModelScope.launch {
            runCatching { repo.setStarred(newValue, albumId = albumId) }
                .onFailure { _state.value = _state.value.copy(starred = !newValue) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(navController: NavController, albumId: String, vm: AlbumViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Albüm yüklenemedi", color = MaterialTheme.colorScheme.error)
        }
        else -> {
            val detail = state.detail!!
            val context = LocalContext.current
            Box(Modifier.fillMaxSize()) {
            AmbientBackdrop(detail.album.coverArt)
            LazyColumn(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { vm.toggleStar() }) {
                            Icon(
                                if (state.starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favori",
                                tint = if (state.starred) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Small centered meta line above the cover (YT mindset)
                        Text(
                            listOfNotNull(
                                "Albüm",
                                detail.album.year?.toString(),
                                "${detail.songs.size} şarkı",
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Artwork(
                            detail.album.coverArt,
                            sizePx = 800,
                            cornerRadius = 12.dp,
                            modifier = Modifier
                                .size(240.dp)
                                .shadow(
                                    20.dp,
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    clip = false,
                                ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            detail.album.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            detail.album.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
                        val active by vm.activeDownload.collectAsStateWithLifecycle()
                        val songIds = detail.songs.map { it.id }
                        val downloadState = when {
                            active != null && songIds.contains(active!!.songId) -> DownloadState.DOWNLOADING
                            songIds.isNotEmpty() && songIds.all { it in downloadedIds } -> DownloadState.DONE
                            else -> DownloadState.NONE
                        }
                        val playerUi by vm.player.state.collectAsStateWithLifecycle()
                        val currentCtx by vm.player.currentContext.collectAsStateWithLifecycle()
                        val isThisPlaying =
                            (currentCtx as? PlaybackContext.Album)?.albumId == detail.album.id
                        CollectionActionRow(
                            isPlaying = isThisPlaying && playerUi.isPlaying,
                            onPlay = {
                                if (isThisPlaying) {
                                    vm.player.togglePlayPause()
                                } else {
                                    vm.player.play(detail.songs, context = vm.playbackContext(), contextLabel = vm.contextLabel())
                                }
                            },
                            onShuffle = { vm.player.play(detail.songs.shuffled(), context = vm.playbackContext(), contextLabel = vm.contextLabel()) },
                            onPlayNext = { vm.player.playNext(detail.songs) },
                            onAddToQueue = { vm.player.addToQueue(detail.songs) },
                            onDownload = {
                                if (vm.downloadAll()) {
                                    Toast.makeText(context, "İndirme kuyruğa alındı", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRemoveDownload = { vm.removeDownloads() },
                            downloadState = downloadState,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(detail.songs.size, key = { detail.songs[it].id }, contentType = { "song" }) { i ->
                    SongItem(
                        detail.songs[i],
                        trackNumber = detail.songs[i].track ?: (i + 1),
                        showArt = false,
                        onClick = { vm.player.play(detail.songs, i, context = vm.playbackContext(), contextLabel = vm.contextLabel()) },
                    )
                }
                item(key = "footer") {
                    Text(
                        "${detail.songs.size} şarkı • ${formatDuration(detail.album.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            }
        }
    }
}

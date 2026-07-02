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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.data.AlbumDetail
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.SongRow
import com.ozgen.navicloud.ui.components.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            runCatching { repo.album(albumId) }
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
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
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
                        IconButton(onClick = {
                            if (vm.downloadAll()) {
                                Toast.makeText(context, "İndirme kuyruğa alındı", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                Icons.Rounded.DownloadForOffline,
                                contentDescription = "İndir",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        Artwork(
                            detail.album.coverArt,
                            sizePx = 800,
                            cornerRadius = 12.dp,
                            modifier = Modifier.size(240.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            detail.album.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Text(
                            listOfNotNull(
                                detail.album.artist,
                                detail.album.year?.toString(),
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { vm.player.play(detail.songs) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Çal")
                            }
                            OutlinedButton(onClick = { vm.player.play(detail.songs.shuffled()) }) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Karıştır")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(detail.songs.size, key = { detail.songs[it].id }, contentType = { "song" }) { i ->
                    SongRow(
                        detail.songs[i],
                        trackNumber = detail.songs[i].track ?: (i + 1),
                        showArt = false,
                        onClick = { vm.player.play(detail.songs, i) },
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

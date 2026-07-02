package com.ozgen.navicloud.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.SearchResult
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AlbumCard
import com.ozgen.navicloud.ui.components.ArtistCard
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val title: String) {
    ALL("Tümü"),
    SONGS("Şarkılar"),
    ALBUMS("Albümler"),
    ARTISTS("Sanatçılar"),
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val searching: Boolean = false,
    val result: SearchResult? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: MusicRepository,
    val player: PlayerController,
) : ViewModel() {
    private val queryFlow = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state

    init {
        viewModelScope.launch {
            queryFlow.debounce(350).distinctUntilChanged().collect { q ->
                if (q.length < 2) {
                    _state.value = _state.value.copy(searching = false, result = null)
                    return@collect
                }
                _state.value = _state.value.copy(searching = true)
                runCatching { repo.search(q) }
                    .onSuccess { _state.value = _state.value.copy(searching = false, result = it) }
                    .onFailure { _state.value = _state.value.copy(searching = false) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        queryFlow.value = q
    }

    fun onFilterChange(f: SearchFilter) {
        _state.value = _state.value.copy(filter = f)
    }
}

@Composable
fun SearchScreen(navController: NavController, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val result = state.result

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Şarkı, albüm veya sanatçı ara") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Temizle")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { vm.onFilterChange(f) },
                    label = { Text(f.title) },
                )
            }
        }

        if (result == null) return@Column

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        ) {
            when (state.filter) {
                SearchFilter.ALL -> {
                    val topSongs = result.songs.take(6)
                    if (topSongs.isNotEmpty()) {
                        item(key = "h-songs") { SectionTitle("Şarkılar") }
                        items(topSongs.size, key = { "s-" + topSongs[it].id }, contentType = { "song" }) { i ->
                            SongItem(topSongs[i], onClick = { vm.player.play(topSongs, i, context = PlaybackContext.AllSongs) })
                        }
                    }
                    if (result.albums.isNotEmpty()) {
                        item(key = "h-albums") { SectionTitle("Albümler") }
                        item(key = "row-albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(result.albums.size, key = { result.albums[it].id }) { i ->
                                    val album = result.albums[i]
                                    AlbumCard(album, onClick = { navController.navigate("album/${album.id}") })
                                }
                            }
                        }
                    }
                    if (result.artists.isNotEmpty()) {
                        item(key = "h-artists") { SectionTitle("Sanatçılar") }
                        item(key = "row-artists") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(result.artists.size, key = { result.artists[it].id }) { i ->
                                    val artist = result.artists[i]
                                    ArtistCard(artist, onClick = { navController.navigate("artist/${artist.id}") })
                                }
                            }
                        }
                    }
                }
                SearchFilter.SONGS -> {
                    items(result.songs.size, key = { result.songs[it].id }, contentType = { "song" }) { i ->
                        SongItem(result.songs[i], onClick = { vm.player.play(result.songs, i, context = PlaybackContext.AllSongs) })
                    }
                }
                SearchFilter.ALBUMS -> {
                    items(result.albums.size, key = { result.albums[it].id }, contentType = { "album-row" }) { i ->
                        val album = result.albums[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("album/${album.id}") }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(album.coverArt, sizePx = 150, cornerRadius = 6.dp, modifier = Modifier.size(56.dp))
                            Column {
                                Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    album.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                SearchFilter.ARTISTS -> {
                    items(result.artists.size, key = { result.artists[it].id }, contentType = { "artist-row" }) { i ->
                        val artist = result.artists[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("artist/${artist.id}") }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(artist.coverArt, sizePx = 150, cornerRadius = 28.dp, modifier = Modifier.size(56.dp))
                            Text(artist.name, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

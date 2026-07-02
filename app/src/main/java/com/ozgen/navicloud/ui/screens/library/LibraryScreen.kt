package com.ozgen.navicloud.ui.screens.library

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Album
import com.ozgen.navicloud.core.model.Artist
import com.ozgen.navicloud.core.model.Playlist
import com.ozgen.navicloud.core.model.SearchResult
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.ActiveDownload
import com.ozgen.navicloud.data.DownloadRepository
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.ServerRepository
import com.ozgen.navicloud.data.toSong
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AlbumCard
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val title: String) {
    PLAYLISTS("Çalma Listeleri"),
    ALBUMS("Albümler"),
    ARTISTS("Sanatçılar"),
    SONGS("Şarkılar"),
    FAVORITES("Favoriler"),
    DOWNLOADS("İndirilenler"),
}

private const val SONGS_PAGE = 200

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.PLAYLISTS,
    val loading: Boolean = false,
    val query: String = "",
    val playlists: List<Playlist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val songs: List<Song> = emptyList(),
    val songsEndReached: Boolean = false,
    val songsLoadingMore: Boolean = false,
    val starred: SearchResult? = null,
    val downloads: List<Song> = emptyList(),
    val activeDownload: ActiveDownload? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: MusicRepository,
    private val servers: ServerRepository,
    private val downloadRepo: DownloadRepository,
    val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    init {
        selectTab(LibraryTab.PLAYLISTS)
        viewModelScope.launch {
            servers.activeServer.filterNotNull().flatMapLatest { server ->
                downloadRepo.downloadsFor(server.id)
            }.collect { list ->
                _state.value = _state.value.copy(downloads = list.map { it.toSong() })
            }
        }
        viewModelScope.launch {
            downloadRepo.active.collect { _state.value = _state.value.copy(activeDownload = it) }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _state.value = _state.value.copy(tab = tab)
        refresh()
    }

    fun refresh() {
        val tab = _state.value.tab
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    LibraryTab.PLAYLISTS -> _state.value = _state.value.copy(playlists = repo.playlists())
                    LibraryTab.ALBUMS -> _state.value = _state.value.copy(albums = repo.albumsAlphabetical(size = 500))
                    LibraryTab.ARTISTS -> _state.value = _state.value.copy(artists = repo.artists())
                    LibraryTab.SONGS -> {
                        val first = repo.allSongs(offset = 0, size = SONGS_PAGE).distinctBy { it.id }
                        _state.value = _state.value.copy(
                            songs = first,
                            songsEndReached = first.size < SONGS_PAGE,
                        )
                    }
                    LibraryTab.FAVORITES -> _state.value = _state.value.copy(starred = repo.starred())
                    LibraryTab.DOWNLOADS -> Unit // reactive flow keeps it fresh
                }
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun loadMoreSongs() {
        val s = _state.value
        if (s.songsLoadingMore || s.songsEndReached || s.tab != LibraryTab.SONGS) return
        _state.value = s.copy(songsLoadingMore = true)
        viewModelScope.launch {
            runCatching { repo.allSongs(offset = _state.value.songs.size, size = SONGS_PAGE) }
                .onSuccess { page ->
                    // Server pages can overlap; duplicate ids crash LazyColumn keys
                    _state.value = _state.value.copy(
                        songs = (_state.value.songs + page).distinctBy { it.id },
                        songsEndReached = page.size < SONGS_PAGE,
                        songsLoadingMore = false,
                    )
                }
                .onFailure { _state.value = _state.value.copy(songsLoadingMore = false) }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    fun playSongs(songs: List<Song>, index: Int) = player.play(songs, index)
    fun removeDownload(songId: String) = viewModelScope.launch { downloadRepo.delete(songId) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, vm: LibraryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val q = state.query.trim()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Kitaplık",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(
                    selected = state.tab == tab,
                    onClick = { vm.selectTab(tab) },
                    label = { Text(tab.title) },
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Bu listede ara") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (state.tab) {
                LibraryTab.PLAYLISTS -> {
                    val items = state.playlists.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "playlist" }) { i ->
                            val pl = items[i]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("playlist/${pl.id}") }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Artwork(pl.coverArt, sizePx = 150, cornerRadius = 6.dp, modifier = Modifier.size(56.dp))
                                Column {
                                    Text(pl.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${pl.songCount} şarkı",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                LibraryTab.ALBUMS -> {
                    val items = state.albums.filter {
                        q.isEmpty() || it.name.contains(q, true) || it.artist.contains(q, true)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(items.size, key = { items[it].id }, contentType = { "album" }) { i ->
                            val album = items[i]
                            AlbumCard(
                                album,
                                onClick = { navController.navigate("album/${album.id}") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                LibraryTab.ARTISTS -> {
                    val items = state.artists.filter { q.isEmpty() || it.name.contains(q, true) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "artist" }) { i ->
                            val artist = items[i]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { navController.navigate("artist/${artist.id}") }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Artwork(artist.coverArt, sizePx = 150, cornerRadius = 28.dp, modifier = Modifier.size(56.dp))
                                Column {
                                    Text(artist.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${artist.albumCount} albüm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                LibraryTab.SONGS -> {
                    val items = if (q.isEmpty()) state.songs
                    else state.songs.filter { it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    val listState: LazyListState = rememberLazyListState()
                    // Load next page as the user approaches the end (only meaningful unfiltered)
                    LaunchedEffect(listState, state.songs.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .collect { last ->
                                if (q.isEmpty() && last >= state.songs.size - 30) vm.loadMoreSongs()
                            }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                            SongItem(items[i], onClick = { vm.playSongs(items, i) })
                        }
                        if (state.songsLoadingMore) {
                            item(key = "loading-more") {
                                LinearProgressIndicator(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
                LibraryTab.FAVORITES -> {
                    val all = state.starred?.songs.orEmpty()
                    val items = all.filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                            SongItem(items[i], onClick = { vm.playSongs(items, i) })
                        }
                    }
                }
                LibraryTab.DOWNLOADS -> {
                    val items = state.downloads.filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        val active = state.activeDownload
                        if (active != null) {
                            item(key = "active-download") {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        "İndiriliyor: ${active.title}" +
                                            if (active.queued > 1) " (+${active.queued - 1} sırada)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LinearProgressIndicator(
                                        progress = { active.progress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                        items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                            SongItem(items[i], onClick = { vm.playSongs(items, i) })
                        }
                        if (items.isEmpty() && active == null) {
                            item(key = "empty") {
                                Text(
                                    "Henüz indirilen şarkı yok. Albüm veya çalma listesi sayfasındaki indirme düğmesini kullan.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

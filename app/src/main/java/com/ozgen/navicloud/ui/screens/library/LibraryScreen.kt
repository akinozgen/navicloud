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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AlbumCard
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.NaviChip
import com.ozgen.navicloud.ui.components.PillSearchField
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
    val downloadsGrouped: Boolean = true,
    val albumsByRecent: Boolean = false,
    val albumsAsGrid: Boolean = true,
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

    fun toggleAlbumSort() {
        _state.value = _state.value.copy(albumsByRecent = !_state.value.albumsByRecent)
        if (_state.value.tab == LibraryTab.ALBUMS) refresh()
    }

    fun toggleAlbumView() {
        _state.value = _state.value.copy(albumsAsGrid = !_state.value.albumsAsGrid)
    }

    fun refresh() {
        val tab = _state.value.tab
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    LibraryTab.PLAYLISTS -> _state.value = _state.value.copy(playlists = repo.playlists())
                    LibraryTab.ALBUMS -> _state.value = _state.value.copy(
                        albums = if (_state.value.albumsByRecent) {
                            repo.albumList(com.ozgen.navicloud.core.model.HomeSectionType.NEWEST, size = 500)
                        } else {
                            repo.albumsAlphabetical(size = 500)
                        }
                    )
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

    /**
     * Shuffle all: iki aşamalı — küçük ilk parti ANINDA çalmaya başlar
     * (1-2 sn'lik 'suratıma bakıyor' beklemesi buydu), kalanı arkadan eklenir.
     */
    fun shuffleAll() {
        viewModelScope.launch {
            runCatching { repo.randomSongs(40) }.onSuccess { first ->
                if (first.isNotEmpty()) {
                    player.play(first, context = PlaybackContext.AllSongs, contextLabel = "Karışık çalma")
                    runCatching { repo.randomSongs(160) }.onSuccess { more ->
                        val seen = first.map { it.id }.toSet()
                        val extra = more.filter { it.id !in seen }
                        if (extra.isNotEmpty()) player.addToQueue(extra)
                    }
                }
            }
        }
    }

    fun toggleDownloadsGrouped() {
        _state.value = _state.value.copy(downloadsGrouped = !_state.value.downloadsGrouped)
    }

    fun playSongs(songs: List<Song>, index: Int, context: PlaybackContext? = null, label: String? = null) =
        player.play(songs, index, context, label)
    fun removeDownload(songId: String) = viewModelScope.launch { downloadRepo.delete(songId) }
}

/** Collapses the shuffle FAB once the list is scrolled away from the very top. */
@Composable
private fun SyncFabExpansion(listState: LazyListState, expanded: androidx.compose.runtime.MutableState<Boolean>) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24 }
            .collect { expanded.value = it }
    }
}

@Composable
private fun SyncFabExpansionGrid(gridState: LazyGridState, expanded: androidx.compose.runtime.MutableState<Boolean>) {
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 24 }
            .collect { expanded.value = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(navController: NavController, vm: LibraryViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val q = state.query.trim()
    val fabExpanded = remember { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Kitaplık",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { navController.navigate("servers") }) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Sunucular / Ayarlar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryTab.entries.forEach { tab ->
                NaviChip(
                    selected = state.tab == tab,
                    label = tab.title,
                    onClick = { vm.selectTab(tab) },
                )
            }
        }
        PillSearchField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = "Bu listede ara",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when (state.tab) {
                LibraryTab.PLAYLISTS -> {
                    val items = remember(state.playlists, q) { state.playlists.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) } }
                    val listState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
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
                                        "Çalma listesi • ${pl.songCount} şarkı",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                LibraryTab.ALBUMS -> {
                    val items = remember(state.albums, q) {
                        state.albums.filter {
                            q.isEmpty() || it.name.contains(q, true) || it.artist.contains(q, true)
                        }
                    }
                    Column(Modifier.fillMaxSize()) {
                        // Sort (left) + view toggle (right)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.TextButton(onClick = { vm.toggleAlbumSort() }) {
                                Text(if (state.albumsByRecent) "Son eklenen" else "Ada göre")
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.toggleAlbumView() }) {
                                Icon(
                                    if (state.albumsAsGrid) Icons.AutoMirrored.Rounded.List
                                    else Icons.Rounded.GridView,
                                    contentDescription = "Görünüm",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.albumsAsGrid) {
                            val gridState = rememberLazyGridState()
                            SyncFabExpansionGrid(gridState, fabExpanded)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                state = gridState,
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
                        } else {
                            val listState = rememberLazyListState()
                            SyncFabExpansion(listState, fabExpanded)
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(items.size, key = { items[it].id }, contentType = { "album-row" }) { i ->
                                    val album = items[i]
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
                                                "Albüm • ${album.artist}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                LibraryTab.ARTISTS -> {
                    val items = remember(state.artists, q) { state.artists.filter { q.isEmpty() || it.name.contains(q, true) } }
                    val listState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
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
                                        "Sanatçı • ${artist.albumCount} albüm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                LibraryTab.SONGS -> {
                    val items = remember(state.songs, q) {
                        if (q.isEmpty()) state.songs
                        else state.songs.filter { it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    }
                    val listState: LazyListState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    // Load next page as the user approaches the end (only meaningful unfiltered)
                    LaunchedEffect(listState, state.songs.size) {
                        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .collect { last ->
                                if (q.isEmpty() && last >= state.songs.size - 30) vm.loadMoreSongs()
                            }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                            SongItem(items[i], onClick = { vm.playSongs(items, i, PlaybackContext.AllSongs, "Tüm şarkılar") })
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
                    val items = remember(state.starred, q) {
                        state.starred?.songs.orEmpty()
                            .filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    }
                    val listState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                            SongItem(items[i], onClick = { vm.playSongs(items, i) })
                        }
                    }
                }
                LibraryTab.DOWNLOADS -> {
                    val items = remember(state.downloads, q) {
                        state.downloads.filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    }
                    val listState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
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
                        // Group toggle
                        item(key = "group-toggle") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (state.downloadsGrouped) "Albüme göre gruplu" else "Düz liste",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.toggleDownloadsGrouped() }) {
                                    Icon(
                                        if (state.downloadsGrouped) Icons.AutoMirrored.Rounded.List
                                        else Icons.Rounded.GridView,
                                        contentDescription = "Gruplamayı değiştir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (state.downloadsGrouped) {
                            val groups = items.groupBy { it.album ?: "Bilinmeyen Albüm" }.toSortedMap()
                            groups.forEach { (albumName, songs) ->
                                item(key = "hdr-$albumName") {
                                    Text(
                                        albumName,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                items(songs.size, key = { "g-${songs[it].id}" }, contentType = { "song" }) { i ->
                                    SongItem(songs[i], onClick = { vm.playSongs(songs, i) })
                                }
                            }
                        } else {
                            items(items.size, key = { items[it].id }, contentType = { "song" }) { i ->
                                SongItem(items[i], onClick = { vm.playSongs(items, i) })
                            }
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

    ExtendedFloatingActionButton(
        onClick = { vm.shuffleAll() },
        expanded = fabExpanded.value,
        icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
        text = { Text("Shuffle all") },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    )
    }
}

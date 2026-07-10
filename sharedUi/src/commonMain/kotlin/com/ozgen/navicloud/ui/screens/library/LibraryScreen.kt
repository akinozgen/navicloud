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
import androidx.compose.material.icons.rounded.Refresh
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
import com.ozgen.navicloud.ui.components.NaviRefreshBox
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ozgen.navicloud.ui.containerViewModel
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
import com.ozgen.navicloud.data.DownloadsPort
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.ServerSource
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AlbumCard
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.NaviChip
import com.ozgen.navicloud.ui.components.PillSearchField
import com.ozgen.navicloud.ui.components.SongItem
import com.ozgen.navicloud.ui.i18n.LocalStrings
import com.ozgen.navicloud.i18n.I18n
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { PLAYLISTS, ALBUMS, ARTISTS, SONGS, FAVORITES, DOWNLOADS }

@Composable
private fun LibraryTab.label(): String {
    val s = LocalStrings.current
    return when (this) {
        LibraryTab.PLAYLISTS -> s.libraryTabPlaylists
        LibraryTab.ALBUMS -> s.libraryTabAlbums
        LibraryTab.ARTISTS -> s.libraryTabArtists
        LibraryTab.SONGS -> s.libraryTabSongs
        LibraryTab.FAVORITES -> s.libraryTabFavorites
        LibraryTab.DOWNLOADS -> s.libraryTabDownloads
    }
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
    val downloadsGrouped: Boolean = true,
    val albumsByRecent: Boolean = false,
    val albumsAsGrid: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repo: MusicRepository,
    private val servers: ServerSource,
    private val downloadRepo: DownloadsPort,
    private val settings: OfflineModeSource,
    val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    // İndirilenler ayrı StateFlow — _state.copy() race'ine takılmaz (eski bug buydu)
    val downloads: StateFlow<List<Song>> =
        servers.activeServer.filterNotNull()
            .flatMapLatest { server -> downloadRepo.downloadsFor(server.id) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeDownload: StateFlow<ActiveDownload?> =
        downloadRepo.active.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val offlineMode: StateFlow<Boolean> =
        settings.offlineMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        selectTab(LibraryTab.PLAYLISTS)
    }

    fun selectTab(tab: LibraryTab) {
        _state.value = _state.value.copy(tab = tab)
        load(force = false)
    }

    fun toggleAlbumSort() {
        _state.value = _state.value.copy(albumsByRecent = !_state.value.albumsByRecent)
        if (_state.value.tab == LibraryTab.ALBUMS) load(force = false)
    }

    fun toggleAlbumView() {
        _state.value = _state.value.copy(albumsAsGrid = !_state.value.albumsAsGrid)
    }

    /** Pull-to-refresh: TTL'i atla. Sekme geçişleri cache'ten döner. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) {
        val tab = _state.value.tab
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    LibraryTab.PLAYLISTS -> _state.value = _state.value.copy(playlists = repo.playlists(force))
                    LibraryTab.ALBUMS -> _state.value = _state.value.copy(
                        albums = if (_state.value.albumsByRecent) {
                            repo.albumList(com.ozgen.navicloud.core.model.HomeSectionType.NEWEST, size = 500, force = force)
                        } else {
                            repo.albumsAlphabetical(size = 500, force = force)
                        }
                    )
                    LibraryTab.ARTISTS -> _state.value = _state.value.copy(artists = repo.artists(force))
                    LibraryTab.SONGS -> {
                        val first = repo.allSongs(offset = 0, size = SONGS_PAGE, force = force).distinctBy { it.id }
                        _state.value = _state.value.copy(
                            songs = first,
                            songsEndReached = first.size < SONGS_PAGE,
                        )
                    }
                    LibraryTab.FAVORITES -> _state.value = _state.value.copy(starred = repo.starred(force))
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
     * Context-aware shuffle: aktif sekme neyse ONU karıştırır.
     * Favoriler → favoriler, İndirilenler (veya offline) → indirilenler,
     * diğer sekmeler → tüm kütüphane (sunucudan iki aşamalı random).
     */
    fun shuffleAll() {
        val tab = _state.value.tab
        val offline = offlineMode.value
        when {
            tab == LibraryTab.DOWNLOADS || offline -> {
                val dl = downloads.value
                if (dl.isNotEmpty()) {
                    player.play(dl.shuffled(), context = PlaybackContext.AllSongs, contextLabel = I18n.strings.libraryTabDownloads)
                }
            }
            tab == LibraryTab.FAVORITES -> {
                viewModelScope.launch {
                    val favs = _state.value.starred?.songs
                        ?: runCatching { repo.starred().songs }.getOrDefault(emptyList())
                    if (favs.isNotEmpty()) {
                        player.play(favs.shuffled(), context = PlaybackContext.AllSongs, contextLabel = I18n.strings.libraryTabFavorites)
                    }
                }
            }
            else -> viewModelScope.launch {
                runCatching { repo.randomSongs(40) }.onSuccess { first ->
                    if (first.isNotEmpty()) {
                        player.play(first, context = PlaybackContext.AllSongs, contextLabel = I18n.strings.libraryCtxShuffleMix)
                        runCatching { repo.randomSongs(160) }.onSuccess { more ->
                            val seen = first.map { it.id }.toSet()
                            val extra = more.filter { it.id !in seen }
                            if (extra.isNotEmpty()) player.addToQueue(extra)
                        }
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
fun LibraryScreen(navController: NavController, vm: LibraryViewModel = containerViewModel { LibraryViewModel(it.music, it.servers, it.downloads, it.offline, it.player) }) {
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val activeDownload by vm.activeDownload.collectAsStateWithLifecycle()
    val q = state.query.trim()
    val fabExpanded = remember { mutableStateOf(true) }
    val strings = LocalStrings.current

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                strings.libraryTitle,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            // Aktif indirme varsa ilerleme rozeti — dokununca indirme yönetimi
            val activeDl = activeDownload
            if (activeDl != null) {
                IconButton(onClick = { navController.navigate("servers") }) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = { activeDl.progress },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            }
            IconButton(onClick = { vm.refresh() }) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = strings.commonRefresh,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { navController.navigate("servers") }) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = strings.commonSettings,
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
                    label = tab.label(),
                    onClick = { vm.selectTab(tab) },
                )
            }
        }
        PillSearchField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = strings.librarySearchHint,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        NaviRefreshBox(
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
                                        strings.libraryPlaylistSubtitle(pl.songCount),
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
                                Text(if (state.albumsByRecent) strings.librarySortRecent else strings.librarySortAlpha)
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { vm.toggleAlbumView() }) {
                                Icon(
                                    if (state.albumsAsGrid) Icons.AutoMirrored.Rounded.List
                                    else Icons.Rounded.GridView,
                                    contentDescription = strings.libraryView,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (state.albumsAsGrid) {
                            val gridState = rememberLazyGridState()
                            SyncFabExpansionGrid(gridState, fabExpanded)
                            LazyVerticalGrid(
                                // Genişledikçe kolon eklenir (tablet/rail düzeni)
                                columns = GridCells.Adaptive(minSize = 160.dp),
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
                                                strings.libraryAlbumSubtitle(album.artist),
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
                                        strings.libraryArtistSubtitle(artist.albumCount),
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
                            SongItem(items[i], onClick = { vm.playSongs(items, i, PlaybackContext.AllSongs, strings.libraryCtxAllSongs) })
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
                    val items = remember(downloads, q) {
                        downloads.filter { q.isEmpty() || it.title.contains(q, true) || it.artist?.contains(q, true) == true }
                    }
                    val listState = rememberLazyListState()
                    SyncFabExpansion(listState, fabExpanded)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        val active = activeDownload
                        if (active != null) {
                            item(key = "active-download") {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(
                                        strings.downloadInProgress(active.title) +
                                            if (active.queued > 1) strings.downloadQueuedSuffix(active.queued - 1) else "",
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
                                    if (state.downloadsGrouped) strings.libraryDownloadsGrouped else strings.libraryDownloadsFlat,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { vm.toggleDownloadsGrouped() }) {
                                    Icon(
                                        if (state.downloadsGrouped) Icons.AutoMirrored.Rounded.List
                                        else Icons.Rounded.GridView,
                                        contentDescription = strings.libraryToggleGrouping,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (state.downloadsGrouped) {
                            val groups = items.groupBy { it.album ?: strings.libraryUnknownAlbum }.toSortedMap()
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
                                    strings.libraryDownloadsEmpty,
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
        text = { Text(strings.libraryShuffleAll) },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
    )
    }
}

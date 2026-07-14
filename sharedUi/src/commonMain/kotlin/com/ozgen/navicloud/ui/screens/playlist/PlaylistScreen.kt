package com.ozgen.navicloud.ui.screens.playlist

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.OfflineModeSource
import com.ozgen.navicloud.data.PlaylistDetail
import com.ozgen.navicloud.data.PlaylistReadOnlyException
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AmbientBackdrop
import com.ozgen.navicloud.ui.components.CollectionActionRow
import com.ozgen.navicloud.ui.components.DownloadState
import com.ozgen.navicloud.ui.components.PlaylistCoverMosaic
import com.ozgen.navicloud.ui.components.PlaylistNameDialog
import com.ozgen.navicloud.ui.components.SongItem
import com.ozgen.navicloud.ui.containerViewModel
import com.ozgen.navicloud.ui.i18n.LocalStrings
import com.ozgen.navicloud.ui.rememberToaster
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class PlaylistUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val detail: PlaylistDetail? = null,
    val error: String? = null,
)

/**
 * Kuyruktaki QueueTrack.uid'nin playlist karşılığı: aynı şarkı listede iki kez
 * olabilir (duplicate serbest) → Compose key ve drag kimliği songId OLAMAZ.
 */
data class PlaylistRow(val uid: Long, val song: Song)

/** Mutasyon hatası türü — UI doğru toast'a çevirir. */
enum class PlaylistMutationError { GENERIC, READ_ONLY }

class PlaylistViewModel(
    private val repo: MusicRepository,
    private val downloadRepo: com.ozgen.navicloud.data.DownloadsPort,
    val player: PlayerController,
    private val playlistId: String,
    offline: OfflineModeSource,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state

    /**
     * Optimistic yerel gerçek: UI yalnız bunu çizer. Mutasyonlar anında buraya
     * uygulanır; sunucu arkadan gelir. Hata → force-refresh sunucu gerçeğini geri getirir.
     */
    val rows = mutableStateListOf<PlaylistRow>()
    private var nextUid = 0L

    val offlineMode: StateFlow<Boolean> =
        offline.offlineMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Tüm mutasyonlar TEK sıralı worker'dan geçer (FIFO): her remove'un indeksi
    // enqueue anındaki yerel snapshot'a göre hesaplanır; seri çalıştığından sunucu
    // durumu o an tam o snapshot'tır. Mutex yerine Channel — adalet (FIFO) garantili.
    private val mutations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var pendingMutations = 0

    private val _errors = Channel<PlaylistMutationError>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    init {
        viewModelScope.launch {
            for (m in mutations) {
                val err = runCatching { m() }.exceptionOrNull()
                pendingMutations--
                if (err != null) {
                    // Bekleyen optimistic işlemler artık geçersiz — kuyruğu boşalt,
                    // sunucu gerçeğine dön (satırın "geri gelmesi" buradan gelir)
                    while (true) {
                        val r = mutations.tryReceive()
                        if (r.isSuccess) pendingMutations-- else break
                    }
                    _errors.trySend(
                        if (err is PlaylistReadOnlyException) PlaylistMutationError.READ_ONLY
                        else PlaylistMutationError.GENERIC
                    )
                    loadInternal(force = true)
                }
            }
        }
        viewModelScope.launch { loadInternal(force = false, initial = true) }
        // Reaktivite: başka ekrandan bu listeye şarkı eklenirse (picker, back-stack'te
        // beklerken) detay kendiliğinden tazelenir. Kendi mutasyonlarımız sırasında
        // (pendingMutations>0) sinyal ATLANIR — optimistic rows ezilmesin; zaten
        // worker hata yolunda force-refresh var.
        viewModelScope.launch {
            repo.playlistsVersion.drop(1).collect {
                if (pendingMutations == 0) loadInternal(force = false)
            }
        }
    }

    private fun enqueueMutation(block: suspend () -> Unit) {
        pendingMutations++
        mutations.trySend(block)
    }

    private fun rebuildRows(songs: List<Song>) {
        rows.clear()
        songs.forEach { rows.add(PlaylistRow(nextUid++, it)) }
    }

    private suspend fun loadInternal(force: Boolean, initial: Boolean = false) {
        runCatching { repo.playlist(playlistId, force = force) }
            .onSuccess {
                _state.value = PlaylistUiState(loading = false, detail = it)
                // Bekleyen mutasyon varken sunucu verisi yerel gerçeği EZMEZ
                if (pendingMutations == 0) {
                    rebuildRows(it.songs)
                    // Öneriler listeye girenleri göstermesin
                    val inRows = rows.mapTo(mutableSetOf()) { r -> r.song.id }
                    suggestions.removeAll { s -> s.id in inRows }
                    if (initial) loadSuggestions()
                }
            }
            .onFailure {
                if (initial) _state.value = PlaylistUiState(loading = false, error = it.message)
                else _state.value = _state.value.copy(refreshing = false, loading = false)
            }
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        viewModelScope.launch {
            loadInternal(force = true)
            _state.value = _state.value.copy(refreshing = false)
        }
    }

    // --- Mutasyonlar ---

    fun removeRow(uid: Long) {
        val index = rows.indexOfFirst { it.uid == uid }
        if (index < 0) return
        val expected = rows[index].song.id
        rows.removeAt(index) // optimistic: satır anında düşer
        enqueueMutation { repo.removeFromPlaylist(playlistId, index, expected) }
    }

    /** Drop'ta TEK commit — drag boyunca API çağrısı yok (full-replace semantiği). */
    fun commitReorder() {
        val ids = rows.map { it.song.id }
        if (ids.isEmpty()) return // çifte guard: boş replace bazı sürümlerde listeyi siler
        enqueueMutation { repo.reorderPlaylist(playlistId, ids) }
    }

    fun rename(name: String) {
        val detail = _state.value.detail ?: return
        // optimistic: başlık anında değişir; hata → force-refresh eski adı getirir
        _state.value = _state.value.copy(detail = detail.copy(playlist = detail.playlist.copy(name = name)))
        enqueueMutation { repo.renamePlaylist(playlistId, name) }
    }

    /** Silme OPTİMİSTİC DEĞİL — geri dönüşü yok; başarıda [onDone] (toast + geri dönüş). */
    fun delete(onDone: () -> Unit) {
        enqueueMutation {
            repo.deletePlaylist(playlistId)
            onDone()
        }
    }

    // --- Öneriler (YTM tarzı: seed sanatçılardan benzer şarkılar) ---

    val suggestions = mutableStateListOf<Song>()
    private val _suggestionsLoading = MutableStateFlow(false)
    val suggestionsLoading: StateFlow<Boolean> = _suggestionsLoading
    private var suggestJob: kotlinx.coroutines.Job? = null

    /** Seed = listedeki en sık 3 sanatçı (yenilemede rastgele örneklenir). */
    fun loadSuggestions(resample: Boolean = false) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            _suggestionsLoading.value = true
            try {
                val byArtist = rows.mapNotNull { r ->
                    r.song.artistId?.let { Triple(it, r.song.artist, r.song.id) }
                }
                if (byArtist.isEmpty()) { suggestions.clear(); return@launch }
                val grouped = byArtist.groupBy({ it.first }, { it.second })
                val seeds = grouped.entries
                    .let { if (resample) it.shuffled() else it.sortedByDescending { e -> e.value.size } }
                    .take(3)
                    .map { it.key to it.value.firstOrNull() }
                val exclude = rows.map { it.song.id }.toSet()
                val result = runCatching { repo.playlistSuggestions(seeds, exclude) }.getOrDefault(emptyList())
                suggestions.clear()
                suggestions.addAll(result)
            } finally {
                _suggestionsLoading.value = false
            }
        }
    }

    /** Öneriden hızlı ekleme: optimistic — satır listeye eklenir, öneri düşer. */
    fun addSuggestion(song: Song) {
        suggestions.remove(song)
        rows.add(PlaylistRow(nextUid++, song))
        enqueueMutation { repo.addToPlaylist(playlistId, listOf(song.id)) }
    }

    // --- Mevcut yardımcılar (rows'a taşındı) ---

    fun downloadAll(): Boolean {
        if (rows.isEmpty()) return false
        downloadRepo.enqueue(rows.map { it.song })
        return true
    }

    fun removeDownloads() {
        val songs = rows.map { it.song }
        viewModelScope.launch { songs.forEach { downloadRepo.delete(it.id) } }
    }

    fun playbackContext(): PlaybackContext = PlaybackContext.Playlist(playlistId)

    fun contextLabel(): String? = _state.value.detail?.playlist?.name

    val downloadedIds = downloadRepo.downloadedIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet<String>())
    val activeDownload = downloadRepo.active
}

/** Öneri yüklenirken iskelet satır — SongItem ile aynı yerleşim, yumuşak alfa nabzı. */
@Composable
private fun SuggestionGhostRow() {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "ghost")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ghostAlpha",
    )
    val tone = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .background(tone.copy(alpha = alpha)),
        )
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                    .background(tone.copy(alpha = alpha)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.32f)
                    .height(12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(tone.copy(alpha = alpha * 0.8f)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(navController: NavController, playlistId: String, vm: PlaylistViewModel = containerViewModel(key = "playlist-$playlistId") { PlaylistViewModel(it.music, it.downloads, it.player, playlistId, it.offline) }) {
    val strings = LocalStrings.current
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: strings.playlistLoadError, color = MaterialTheme.colorScheme.error)
        }
        else -> {
            val detail = state.detail!!
            val toast = rememberToaster()
            val rows = vm.rows
            val offline by vm.offlineMode.collectAsStateWithLifecycle()
            val suggestionsLoading by vm.suggestionsLoading.collectAsStateWithLifecycle()
            // Tüm düzenleme yüzeyleri tek bayraktan akar; error-50 sonrası
            // force-refresh editable=false getirir → UI kendiliğinden salt-okunura düşer
            val canEdit = detail.playlist.editable && !offline

            // Mutasyon hataları → toast (satırın geri gelmesi force-refresh'ten)
            LaunchedEffect(Unit) {
                vm.errors.collect { e ->
                    toast(
                        when (e) {
                            PlaylistMutationError.READ_ONLY -> strings.playlistToastReadOnly
                            PlaylistMutationError.GENERIC -> strings.playlistToastEditFailed
                        }
                    )
                }
            }

            var renameDialog by remember { mutableStateOf(false) }
            var deleteConfirm by remember { mutableStateOf(false) }

            // Kuyruk deseni: reorder kütüphanesi ANINDA yerel listeyi oynatır; commit drop'ta.
            // Dar düzende başlık öğeleri de LazyColumn'da → konum KEY ile çözülür, indeksle değil.
            val listState = rememberLazyListState()
            val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                val fromIdx = rows.indexOfFirst { it.uid == from.key }
                val toIdx = rows.indexOfFirst { it.uid == to.key }
                if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                    rows.add(toIdx, rows.removeAt(fromIdx))
                }
            }

            val topBar: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = strings.commonBack)
                        }
                    }
            }
            val headerBody: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Small centered meta line above the cover (YT mindset)
                        Text(
                            buildString {
                                append(strings.playlistHeaderSubtitle(rows.size))
                                if (!detail.playlist.editable) {
                                    append(" • "); append(strings.playlistReadOnlyBadge)
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        PlaylistCoverMosaic(
                            playlistId = detail.playlist.id,
                            songs = detail.songs,
                            modifier = Modifier
                                .size(220.dp)
                                .shadow(
                                    20.dp,
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    clip = false,
                                ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            detail.playlist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        val comment = detail.playlist.comment
                        if (!comment.isNullOrBlank()) {
                            Text(
                                comment,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        val downloadedIds by vm.downloadedIds.collectAsStateWithLifecycle()
                        val active by vm.activeDownload.collectAsStateWithLifecycle()
                        val songIds = rows.map { it.song.id }
                        val downloadState = when {
                            active != null && songIds.contains(active!!.songId) -> DownloadState.DOWNLOADING
                            songIds.isNotEmpty() && songIds.all { it in downloadedIds } -> DownloadState.DONE
                            else -> DownloadState.NONE
                        }
                        val playerUi by vm.player.state.collectAsStateWithLifecycle()
                        val currentCtx by vm.player.currentContext.collectAsStateWithLifecycle()
                        val isThisPlaying =
                            (currentCtx as? PlaybackContext.Playlist)?.playlistId == detail.playlist.id
                        CollectionActionRow(
                            isPlaying = isThisPlaying && playerUi.isPlaying,
                            onPlay = {
                                if (isThisPlaying) {
                                    vm.player.togglePlayPause()
                                } else {
                                    vm.player.play(rows.map { it.song }, context = vm.playbackContext(), contextLabel = vm.contextLabel())
                                }
                            },
                            onShuffle = { vm.player.play(rows.map { it.song }.shuffled(), context = vm.playbackContext(), contextLabel = vm.contextLabel()) },
                            onPlayNext = { vm.player.playNext(rows.map { it.song }) },
                            onAddToQueue = { vm.player.addToQueue(rows.map { it.song }) },
                            onDownload = {
                                if (vm.downloadAll()) {
                                    toast(strings.commonDownloadQueued)
                                }
                            },
                            onRemoveDownload = { vm.removeDownloads() },
                            downloadState = downloadState,
                            onRename = if (canEdit) ({ renameDialog = true }) else null,
                            onDelete = if (canEdit) ({ deleteConfirm = true }) else null,
                            playbackEnabled = rows.isNotEmpty(),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
            }
            val songList: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
                if (rows.isEmpty()) {
                    item(key = "empty-hint", contentType = "empty") {
                        Text(
                            strings.playlistEmptyHint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                        )
                    }
                }
                val addSuggestionAndToast: (Song) -> Unit = { s ->
                    vm.addSuggestion(s)
                    toast(strings.playlistSuggestionAdded)
                }
                items(rows.size, key = { rows[it].uid }, contentType = { "song" }) { i ->
                    val row = rows[i]
                    ReorderableItem(
                        reorderableState,
                        key = row.uid,
                        modifier = Modifier.animateItem(),
                    ) { isDragging ->
                        val dragScope = this
                        SongItem(
                            row.song,
                            onClick = { vm.player.play(rows.map { it.song }, i, context = vm.playbackContext(), contextLabel = vm.contextLabel()) },
                            inPlaylist = canEdit,
                            onRemoveFromPlaylist = if (canEdit) {
                                { vm.removeRow(row.uid); toast(strings.playlistToastRemoved) }
                            } else null,
                            modifier = Modifier.graphicsLayer { alpha = if (isDragging) 0.85f else 1f },
                            trailingContent = if (canEdit) {
                                {
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = strings.playerDragReorder,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        // Kuyruktan bilinçli fark: playlist satırında yatay swipe
                                        // yok → ANLIK handle güvenli (masaüstü mouse da bunu ister)
                                        modifier = with(dragScope) {
                                            Modifier.draggableHandle(onDragStopped = { vm.commitReorder() })
                                        },
                                    )
                                }
                            } else null,
                        )
                    }
                }
                // --- Öneriler (YTM tarzı): yalnız düzenlenebilir listede; yüklenirken ghost ---
                val suggestions = vm.suggestions
                val showSuggestions = canEdit && (suggestions.isNotEmpty() || (suggestionsLoading && rows.isNotEmpty()))
                if (showSuggestions) {
                    item(key = "sugg-header", contentType = "sugg-header") {
                        Text(
                            strings.playlistSuggestions,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
                        )
                    }
                    if (suggestionsLoading) {
                        // Ghost yer tutucular — ilk yüklemede 3, yenilemede mevcut öğe sayısı
                        // kadar (liste zıplamasın); içerik gelince gerçek satırlar yerine oturur
                        val ghostCount = if (suggestions.isEmpty()) 3 else suggestions.size
                        items(ghostCount, key = { "sugg-ghost-$it" }, contentType = { "sugg-ghost" }) {
                            SuggestionGhostRow()
                        }
                    } else {
                        items(suggestions.size, key = { "sugg-${suggestions[it].id}" }, contentType = { "suggestion" }) { i ->
                            val s = suggestions[i]
                            SongItem(
                                s,
                                onClick = { vm.player.play(listOf(s)) },
                                modifier = Modifier.animateItem(),
                                trailingContent = {
                                    IconButton(onClick = { addSuggestionAndToast(s) }) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            contentDescription = strings.songMenuAddToPlaylist,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                            )
                        }
                        item(key = "sugg-refresh", contentType = "sugg-refresh") {
                            TextButton(
                                onClick = { vm.loadSuggestions(resample = true) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            ) { Text(strings.commonRefresh) }
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 700.dp
                Box(Modifier.fillMaxSize()) {
                    AmbientBackdrop(detail.songs.firstOrNull()?.coverArt)
                    if (wide) {
                        Row(Modifier.fillMaxSize().statusBarsPadding()) {
                            Column(
                                Modifier
                                    .width(380.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                topBar()
                                headerBody()
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                            ) {
                                songList()
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().statusBarsPadding(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            item(key = "top-bar", contentType = "header") { topBar() }
                            item(key = "header-body", contentType = "header") { headerBody() }
                            songList()
                        }
                    }
                }
            }

            if (renameDialog) {
                PlaylistNameDialog(
                    title = strings.playlistRename,
                    confirmLabel = strings.commonSave,
                    initialValue = detail.playlist.name,
                    onDismiss = { renameDialog = false },
                    onConfirm = { name ->
                        renameDialog = false
                        vm.rename(name)
                    },
                )
            }
            if (deleteConfirm) {
                AlertDialog(
                    onDismissRequest = { deleteConfirm = false },
                    title = { Text(strings.playlistDeleteConfirmTitle) },
                    text = { Text(strings.playlistDeleteConfirmBody(detail.playlist.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            deleteConfirm = false
                            vm.delete {
                                toast(strings.playlistToastDeleted)
                                navController.popBackStack()
                            }
                        }) { Text(strings.playlistDelete, color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deleteConfirm = false }) { Text(strings.commonCancel) }
                    },
                )
            }
        }
    }
}

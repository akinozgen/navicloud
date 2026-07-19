package com.ozgen.navicloud.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Album
import com.ozgen.navicloud.core.model.Artist
import com.ozgen.navicloud.core.model.HomeSection
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.MixResult
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.i18n.Strings
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.ArtistCard
import com.ozgen.navicloud.ui.components.GhostCard
import com.ozgen.navicloud.ui.components.MixHeroCard
import com.ozgen.navicloud.ui.components.MixHeroStrip
import com.ozgen.navicloud.ui.components.NaviRefreshBox
import com.ozgen.navicloud.ui.components.OverlayAlbumCard
import com.ozgen.navicloud.ui.components.RadioCard
import com.ozgen.navicloud.ui.containerViewModel
import com.ozgen.navicloud.ui.i18n.LocalStrings
import com.ozgen.navicloud.ui.rememberToaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sections: List<HomeSection> = emptyList(),
    val error: String? = null,
    // For You rafları — her biri bağımsız damlar (progressive), boş+yüklenmiyor = raf gizli
    val mix: List<Song> = emptyList(),
    val mixLoading: Boolean = true,
    val mixPlaylistId: String? = null,
    val radioArtists: List<Artist> = emptyList(),
    val radioLoading: Boolean = true,
    val similarArtists: List<Artist> = emptyList(),
    val similarLoading: Boolean = true,
    val rediscover: List<Album> = emptyList(),
    val rediscoverLoading: Boolean = true,
)

class HomeViewModel(
    private val repo: MusicRepository,
    val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Şu an radyo kuyruğu hazırlanan sanatçı (kartta play→spinner morph'u). */
    val radioBusyId = MutableStateFlow<String?>(null)

    init {
        load(force = false)
        loadForYou()
        // Favori değişimi yalnız "Yeniden keşfet"i etkiler
        viewModelScope.launch {
            repo.starredVersion.drop(1).collect {
                val v = runCatching { repo.rediscoverAlbums() }.getOrDefault(emptyList())
                _state.update { it.copy(rediscover = v, rediscoverLoading = false) }
            }
        }
    }

    /** Pull-to-refresh: klasik rafları tazeler; mix YENİDEN ÜRETİLMEZ (günlük kadans). */
    fun refresh() {
        load(force = true)
        loadForYou()
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            val initial = _state.value.sections.isEmpty()
            _state.value = _state.value.copy(loading = initial, refreshing = !initial, error = null)
            runCatching { repo.homeSections(force) }
                .onSuccess { s -> _state.update { it.copy(loading = false, refreshing = false, sections = s, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, refreshing = false, error = if (it.sections.isEmpty()) e.message else it.error) } }
        }
    }

    /** For You rafları paraleldir ve klasik home'u BEKLETMEZ; hepsi sessiz fail → boş. */
    private fun loadForYou() {
        viewModelScope.launch {
            launch {
                val v = runCatching { repo.radioArtists() }.getOrDefault(emptyList())
                _state.update { it.copy(radioArtists = v, radioLoading = false) }
            }
            launch {
                val v = runCatching { repo.similarArtistShelf() }.getOrDefault(emptyList())
                _state.update { it.copy(similarArtists = v, similarLoading = false) }
            }
            launch {
                val v = runCatching { repo.rediscoverAlbums() }.getOrDefault(emptyList())
                _state.update { it.copy(rediscover = v, rediscoverLoading = false) }
            }
            launch {
                val v = runCatching { repo.naviCloudMix() }.getOrDefault(emptyList())
                _state.update { it.copy(mix = v, mixLoading = false, mixPlaylistId = runCatching { repo.naviCloudMixPlaylistId() }.getOrNull()) }
                // Materyalize tetiği TEK yer burası (home iki platformda da startDestination)
                val r = runCatching { repo.materializeNaviCloudMix() }.getOrDefault(MixResult.FAILED)
                println("NaviCloudMix: materialize=$r")
                if (r == MixResult.WRITTEN) {
                    _state.update { it.copy(mixPlaylistId = runCatching { repo.naviCloudMixPlaylistId() }.getOrNull()) }
                }
            }
        }
    }

    fun playMix(label: String) {
        val songs = _state.value.mix
        if (songs.isEmpty()) return
        player.play(songs, context = _state.value.mixPlaylistId?.let { PlaybackContext.Playlist(it) }, contextLabel = label)
    }

    /** Radyo: topSongs+benzerler kuyruğu — Artist bağlamıyla endless sürer. */
    fun playRadio(artist: Artist, label: String, onEmpty: () -> Unit) {
        if (radioBusyId.value != null) return
        viewModelScope.launch {
            radioBusyId.value = artist.id
            val songs = runCatching { repo.artistRadio(artist.id, artist.name) }.getOrDefault(emptyList())
            radioBusyId.value = null
            if (songs.isEmpty()) onEmpty()
            else player.play(songs, context = PlaybackContext.Artist(artist.id), contextLabel = label)
        }
    }
}

private fun greeting(s: Strings): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> s.homeGreetingMorning
    in 12..17 -> s.homeGreetingAfternoon
    else -> s.homeGreetingEvening
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = containerViewModel { HomeViewModel(it.music, it.player) },
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val strings = LocalStrings.current
    val toast = rememberToaster()
    val radioBusyId by vm.radioBusyId.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.refresh() }) { Text(strings.commonRetry) }
            }
        }
        else -> NaviRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            greeting(strings),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { navController.navigate("servers") }) {
                            Icon(
                                Icons.Rounded.Dns,
                                contentDescription = strings.homeServers,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // --- For You: Günün Mix'i — TAM GENİŞLİK. Geniş ekran (≥600dp) ince tek-satır
                // strip, telefon 2x yüksek kolaj hero. Yükleniyorken aynı biçimde ghost.
                if (state.mixLoading || state.mix.isNotEmpty()) {
                    item(key = "mix-hero", contentType = "mix-hero") {
                        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            val wide = maxWidth >= 600.dp
                            when {
                                state.mix.isEmpty() && wide ->
                                    GhostCard(Modifier.fillMaxWidth().height(92.dp), RoundedCornerShape(16.dp))
                                state.mix.isEmpty() ->
                                    GhostCard(Modifier.fillMaxWidth().aspectRatio(1.9f), RoundedCornerShape(16.dp))
                                wide -> MixHeroStrip(
                                    songs = state.mix,
                                    onPlay = { vm.playMix(strings.homeMixTitle) },
                                    onOpen = state.mixPlaylistId?.let { id -> { navController.navigate("playlist/$id") } },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                else -> MixHeroCard(
                                    songs = state.mix,
                                    onPlay = { vm.playMix(strings.homeMixTitle) },
                                    onBadgeClick = state.mixPlaylistId?.let { id -> { navController.navigate("playlist/$id") } },
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1.9f),
                                )
                            }
                        }
                    }
                }
                // İlk bölüm: hızlı erişim grid'i (üstte bilgi yoğunluğu). Kolon sayısı içerik
                // genişliğinden türer — telefonda 2, geniş ekranda 3-4 (karolar devleşmesin).
                val firstSection = state.sections.firstOrNull()
                if (firstSection != null) {
                    item(key = "quick-grid", contentType = "grid") {
                        BoxWithConstraints(Modifier.padding(horizontal = 16.dp)) {
                            val cols = (maxWidth / 220.dp).toInt().coerceIn(2, 4)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                firstSection.albums.take(cols * 3).chunked(cols).forEach { rowAlbums ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        rowAlbums.forEach { album ->
                                            OverlayAlbumCard(
                                                album,
                                                onClick = { navController.navigate("album/${album.id}") },
                                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                            )
                                        }
                                        repeat(cols - rowAlbums.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                // --- For You: Sanatçı radyosu (kart tık = direkt çal, morph'lu spinner) ---
                if (state.radioLoading || state.radioArtists.isNotEmpty()) {
                    item(key = "radio-shelf", contentType = "foryou-shelf") {
                        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                            Text(
                                strings.homeRadioSection,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (state.radioArtists.isEmpty()) {
                                    items(4, key = { "radio-ghost-$it" }) { GhostCard(Modifier.size(160.dp)) }
                                } else {
                                    items(state.radioArtists.size, key = { state.radioArtists[it].id }) { j ->
                                        val artist = state.radioArtists[j]
                                        RadioCard(
                                            artist = artist,
                                            busy = radioBusyId == artist.id,
                                            onClick = {
                                                vm.playRadio(
                                                    artist,
                                                    strings.homeArtistRadioLabel(artist.name),
                                                    onEmpty = { toast(strings.playlistToastEditFailed) },
                                                )
                                            },
                                            modifier = Modifier.size(160.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // Kalan bölümler: yatay raflar, overlay kartlar
                val shelves = state.sections.drop(1)
                items(
                    shelves.size,
                    key = { shelves[it].type.name },
                    contentType = { "section" },
                ) { i ->
                    val section = shelves[i]
                    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("section/${section.type.name}") }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                strings.homeSectionTitle(section.type),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = strings.homeSeeAll,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(section.albums.size, key = { section.albums[it].id }) { j ->
                                val album = section.albums[j]
                                OverlayAlbumCard(
                                    album,
                                    onClick = { navController.navigate("album/${album.id}") },
                                    modifier = Modifier.width(160.dp).aspectRatio(1f),
                                )
                            }
                        }
                    }
                }
                // --- For You: Sevdiklerine benzer (tık = sanatçı sayfası; çalmaz) ---
                if (state.similarLoading || state.similarArtists.isNotEmpty()) {
                    item(key = "similar-shelf", contentType = "foryou-shelf") {
                        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                            Text(
                                strings.homeSimilarSection,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (state.similarArtists.isEmpty()) {
                                    items(4, key = { "similar-ghost-$it" }) { GhostCard(Modifier.size(140.dp), shape = CircleShape) }
                                } else {
                                    items(state.similarArtists.size, key = { state.similarArtists[it].id }) { j ->
                                        val artist = state.similarArtists[j]
                                        ArtistCard(artist, onClick = { navController.navigate("artist/${artist.id}") })
                                    }
                                }
                            }
                        }
                    }
                }
                // --- For You: Yeniden keşfet (favorilerden son çalınanlarda olmayanlar) ---
                if (state.rediscoverLoading || state.rediscover.isNotEmpty()) {
                    item(key = "rediscover-shelf", contentType = "foryou-shelf") {
                        Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                            Text(
                                strings.homeRediscoverSection,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (state.rediscover.isEmpty()) {
                                    items(4, key = { "rediscover-ghost-$it" }) { GhostCard(Modifier.size(160.dp)) }
                                } else {
                                    items(state.rediscover.size, key = { state.rediscover[it].id }) { j ->
                                        val album = state.rediscover[j]
                                        OverlayAlbumCard(
                                            album,
                                            onClick = { navController.navigate("album/${album.id}") },
                                            modifier = Modifier.width(160.dp).aspectRatio(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

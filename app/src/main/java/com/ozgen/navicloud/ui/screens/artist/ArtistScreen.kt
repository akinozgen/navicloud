package com.ozgen.navicloud.ui.screens.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.ArtistDetail
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.AlbumCard
import com.ozgen.navicloud.ui.components.ArtistCard
import com.ozgen.navicloud.ui.components.Artwork
import com.ozgen.navicloud.ui.components.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val detail: ArtistDetail? = null,
    val topSongs: List<Song> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repo: MusicRepository,
    val player: PlayerController,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId: String = savedStateHandle.get<String>("id").orEmpty()
    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state

    init { load() }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { repo.artist(artistId) }
                .onSuccess { detail ->
                    _state.value = _state.value.copy(loading = false, refreshing = false, detail = detail, error = null)
                    // getTopSongs needs a Last.fm agent on the server; fall back to album tracks
                    val top = repo.topSongs(detail.artist.name).ifEmpty {
                        detail.albums.take(3).flatMap { album ->
                            runCatching { repo.album(album.id).songs }.getOrDefault(emptyList())
                        }.take(10)
                    }
                    _state.value = _state.value.copy(topSongs = top)
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, refreshing = false, error = it.message)
                }
        }
    }

    fun playbackContext(): PlaybackContext = PlaybackContext.Artist(artistId)

    /** Top songs when available, otherwise everything from the artist's albums would be heavy — shuffle albums instead. */
    fun playArtist(shuffle: Boolean) {
        val top = _state.value.topSongs
        if (top.isNotEmpty()) {
            player.play(if (shuffle) top.shuffled() else top, context = playbackContext())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(navController: NavController, artistId: String, vm: ArtistViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Sanatçı yüklenemedi", color = MaterialTheme.colorScheme.error)
        }
        else -> {
            val detail = state.detail!!
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "hero") {
                        Box(Modifier.fillMaxWidth().height(340.dp)) {
                            Artwork(
                                detail.artist.coverArt,
                                sizePx = 900,
                                cornerRadius = 0.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Bottom fade into the page background so the name sits on solid ground
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.55f to Color.Transparent,
                                            1f to MaterialTheme.colorScheme.background,
                                        )
                                    ),
                            )
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x66000000)),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Geri", tint = Color.White)
                            }
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text(
                                    detail.artist.name,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${detail.albums.size} albüm",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xB3FFFFFF),
                                )
                            }
                        }
                    }
                    item(key = "actions") {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = { vm.playArtist(shuffle = false) },
                                enabled = state.topSongs.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Çal")
                            }
                            OutlinedButton(
                                onClick = { vm.playArtist(shuffle = true) },
                                enabled = state.topSongs.isNotEmpty(),
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Karıştır")
                            }
                        }
                    }
                    if (state.topSongs.isNotEmpty()) {
                        item(key = "h-popular") { SectionTitle("Popüler") }
                        val top = state.topSongs.take(10)
                        items(top.size, key = { "top-" + top[it].id }, contentType = { "song" }) { i ->
                            SongItem(top[i], onClick = { vm.player.play(top, i, context = vm.playbackContext()) })
                        }
                    }
                    if (detail.albums.isNotEmpty()) {
                        item(key = "h-albums") { SectionTitle("Albümler") }
                        item(key = "row-albums") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(detail.albums.size, key = { detail.albums[it].id }) { i ->
                                    val album = detail.albums[i]
                                    AlbumCard(album, onClick = { navController.navigate("album/${album.id}") })
                                }
                            }
                        }
                    }
                    if (detail.similar.isNotEmpty()) {
                        item(key = "h-similar") { SectionTitle("Benzer sanatçılar") }
                        item(key = "row-similar") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(detail.similar.size, key = { detail.similar[it].id }) { i ->
                                    val artist = detail.similar[i]
                                    ArtistCard(artist, onClick = { navController.navigate("artist/${artist.id}") })
                                }
                            }
                        }
                    }
                    val bio = detail.biography
                    if (!bio.isNullOrBlank()) {
                        item(key = "bio") {
                            var expanded by remember { mutableStateOf(false) }
                            Column(Modifier.padding(16.dp)) {
                                Text("Hakkında", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    bio.replace(Regex("<[^>]*>"), ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                                Text(
                                    if (expanded) "Daha az göster" else "Devamını oku",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable { expanded = !expanded },
                                )
                            }
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

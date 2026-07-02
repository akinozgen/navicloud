package com.ozgen.navicloud.ui.screens.nowplaying

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ozgen.navicloud.core.model.Lyrics
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.playback.MediaKeys
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.ui.components.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NowPlayingUiState(
    val starred: Boolean = false,
    val lyrics: Lyrics? = null,
    val lyricsLoading: Boolean = false,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val repo: MusicRepository,
    val player: PlayerController,
) : ViewModel() {
    private val _state = MutableStateFlow(NowPlayingUiState())
    val state: StateFlow<NowPlayingUiState> = _state

    private var lyricsForSongId: String? = null

    fun onSongChanged(mediaId: String?, starredExtra: Boolean) {
        _state.value = _state.value.copy(starred = starredExtra)
        if (mediaId != lyricsForSongId) {
            _state.value = _state.value.copy(lyrics = null)
            lyricsForSongId = null
        }
    }

    fun toggleStar(songId: String) {
        val newValue = !_state.value.starred
        _state.value = _state.value.copy(starred = newValue)
        viewModelScope.launch {
            runCatching { repo.setStarred(newValue, songId = songId) }
                .onFailure { _state.value = _state.value.copy(starred = !newValue) }
        }
    }

    fun loadLyrics(songId: String) {
        if (lyricsForSongId == songId) return
        lyricsForSongId = songId
        viewModelScope.launch {
            _state.value = _state.value.copy(lyricsLoading = true)
            val lyrics = runCatching { repo.lyrics(songId) }.getOrNull()
            _state.value = _state.value.copy(lyrics = lyrics, lyricsLoading = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    vm: NowPlayingViewModel = hiltViewModel(),
) {
    val playerState by vm.player.state.collectAsStateWithLifecycle()
    val uiState by vm.state.collectAsStateWithLifecycle()
    val item = playerState.currentItem
    val context = LocalContext.current

    var dominantColor by remember { mutableStateOf(Color(0xFF17171E)) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Back closes the overlay instead of exiting the app
    BackHandler(onBack = onClose)

    // Track song changes: star state + palette extraction
    LaunchedEffect(item?.mediaId) {
        vm.onSongChanged(
            item?.mediaId,
            item?.mediaMetadata?.extras?.getBoolean(MediaKeys.STARRED, false) ?: false,
        )
        val artUri = item?.mediaMetadata?.artworkUri
        if (artUri != null) {
            withContext(Dispatchers.IO) {
                val result = ImageLoader(context).execute(
                    ImageRequest.Builder(context).data(artUri).allowHardware(false).size(128).build()
                )
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val rgb = palette.getDarkVibrantColor(
                        palette.getDarkMutedColor(palette.getMutedColor(0xFF17171E.toInt()))
                    )
                    // Light covers can yield bright colors; clamp so white text stays readable
                    val base = Color(rgb)
                    dominantColor = if (base.luminance() > 0.25f) {
                        lerp(base, Color.Black, 0.55f)
                    } else base
                }
            }
        } else {
            dominantColor = Color(0xFF17171E)
        }
    }

    LaunchedEffect(playerState.isPlaying, item?.mediaId) {
        while (true) {
            positionMs = vm.player.positionMs
            durationMs = vm.player.durationMs
            delay(500)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(dominantColor, MaterialTheme.colorScheme.background),
                    endY = 1800f,
                )
            )
            .pointerInput(Unit) {
                // Swipe down anywhere on the player closes it
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 18f) onClose()
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Kapat", tint = Color.White)
                }
                Text(
                    item?.mediaMetadata?.albumTitle?.toString() ?: "Şu an çalıyor",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Kuyruk", tint = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            // Artwork
            AsyncImage(
                model = item?.mediaMetadata?.artworkUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Spacer(Modifier.weight(1f))

            // Title + star
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item?.mediaMetadata?.title?.toString() ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        item?.mediaMetadata?.artist?.toString() ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xB3FFFFFF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { item?.mediaId?.let { vm.toggleStar(it) } }) {
                    Icon(
                        if (uiState.starred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (uiState.starred) MaterialTheme.colorScheme.primary else Color(0xB3FFFFFF),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Seek bar
            val sliderValue = if (dragging) dragValue
            else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Slider(
                value = sliderValue.coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    vm.player.seekTo((dragValue * durationMs).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x4DFFFFFF),
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration((if (dragging) (dragValue * durationMs / 1000).toInt() else (positionMs / 1000).toInt())),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF),
                )
                Text(
                    formatDuration((durationMs / 1000).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0x99FFFFFF),
                )
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { vm.player.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Karıştır",
                        tint = if (playerState.shuffle) MaterialTheme.colorScheme.primary else Color(0xB3FFFFFF),
                    )
                }
                IconButton(onClick = { vm.player.skipPrevious() }) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Önceki",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledIconButton(
                    onClick = { vm.player.togglePlayPause() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F0F14),
                    ),
                ) {
                    Icon(
                        if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Duraklat" else "Çal",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.player.skipNext() }) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Sonraki",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { vm.player.cycleRepeat() }) {
                    Icon(
                        if (playerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = "Tekrar",
                        tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else Color(0xB3FFFFFF),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Lyrics button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = {
                    item?.mediaId?.let { vm.loadLyrics(it) }
                    showLyrics = true
                }) {
                    Icon(
                        Icons.Rounded.Lyrics,
                        contentDescription = "Şarkı sözleri",
                        tint = Color(0xB3FFFFFF),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showLyrics) {
        ModalBottomSheet(onDismissRequest = { showLyrics = false }) {
            LyricsSheet(
                lyrics = uiState.lyrics,
                loading = uiState.lyricsLoading,
                positionMsProvider = { vm.player.positionMs },
            )
        }
    }

    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }) {
            QueueSheet(vm.player)
        }
    }
}

@Composable
private fun LyricsSheet(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMsProvider: () -> Long,
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = positionMsProvider()
            delay(300)
        }
    }

    when {
        loading -> Box(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Sözler yükleniyor…") }
        lyrics == null || lyrics.lines.isEmpty() -> Box(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Bu şarkı için söz bulunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> {
            val listState = rememberLazyListState()
            val activeIndex = if (lyrics.synced) {
                lyrics.lines.indexOfLast { (it.startMs ?: 0) <= positionMs }.coerceAtLeast(0)
            } else -1

            LaunchedEffect(activeIndex) {
                if (activeIndex >= 0) listState.animateScrollToItem(maxOf(activeIndex - 3, 0))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(500.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            ) {
                items(lyrics.lines.size) { i ->
                    val line = lyrics.lines[i]
                    Text(
                        line.text.ifBlank { "♪" },
                        style = MaterialTheme.typography.titleLarge,
                        color = when {
                            !lyrics.synced -> MaterialTheme.colorScheme.onSurface
                            i == activeIndex -> MaterialTheme.colorScheme.primary
                            i < activeIndex -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSheet(player: PlayerController) {
    val state by player.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(500.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        items(state.queue.size) { i ->
            val queueItem = state.queue[i]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { player.seekToQueueItem(i) }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        queueItem.mediaMetadata.title?.toString() ?: "",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (i == state.currentIndex) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        queueItem.mediaMetadata.artist?.toString() ?: "",
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

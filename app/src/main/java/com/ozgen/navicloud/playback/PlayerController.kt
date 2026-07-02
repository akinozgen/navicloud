package com.ozgen.navicloud.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.net.toUri
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.DownloadRepository
import com.ozgen.navicloud.data.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val currentItem: MediaItem? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<MediaItem> = emptyList(),
    val currentIndex: Int = 0,
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val downloads: DownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    init {
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val mediaController = MediaController.Builder(context, token).buildAsync().await()
            mediaController.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    syncState(player)
                }
            })
            _controller.value = mediaController
            syncState(mediaController)
        }
    }

    private fun syncState(player: Player) {
        _state.value = PlayerUiState(
            currentItem = player.currentMediaItem,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = List(player.mediaItemCount) { player.getMediaItemAt(it) },
            currentIndex = player.currentMediaItemIndex,
        )
    }

    val positionMs: Long get() = _controller.value?.currentPosition ?: 0L
    val durationMs: Long get() = _controller.value?.duration?.coerceAtLeast(0) ?: 0L

    /** Prefers the downloaded local file; falls back to an authenticated stream URL. */
    private suspend fun Song.toItem(): MediaItem {
        val localUri = downloads.localFile(id)?.toUri()?.toString()
        val uri = localUri ?: musicRepository.streamUrl(id)
        val art = runCatching { musicRepository.coverArtUrl(coverArt, 600) }.getOrNull()
        return toMediaItem(streamUrl = uri, artworkUrl = art)
    }

    fun play(songs: List<Song>, startIndex: Int = 0) {
        scope.launch {
            val items = songs.map { it.toItem() }
            _controller.value?.run {
                setMediaItems(items, startIndex, 0L)
                prepare()
                play()
            }
        }
    }

    fun playNext(songs: List<Song>) = enqueue(songs, next = true)
    fun addToQueue(songs: List<Song>) = enqueue(songs, next = false)

    private fun enqueue(songs: List<Song>, next: Boolean) {
        scope.launch {
            val items = songs.map { it.toItem() }
            _controller.value?.run {
                if (mediaItemCount == 0) {
                    setMediaItems(items)
                    prepare()
                    play()
                } else if (next) {
                    addMediaItems(currentMediaItemIndex + 1, items)
                } else {
                    addMediaItems(items)
                }
            }
        }
    }

    fun togglePlayPause() {
        _controller.value?.run { if (isPlaying) pause() else play() }
    }

    fun seekTo(positionMs: Long) { _controller.value?.seekTo(positionMs) }
    fun skipNext() { _controller.value?.seekToNextMediaItem() }
    fun skipPrevious() { _controller.value?.seekToPrevious() }
    fun seekToQueueItem(index: Int) { _controller.value?.seekTo(index, 0L) }
    fun removeQueueItem(index: Int) { _controller.value?.removeMediaItem(index) }
    fun moveQueueItem(from: Int, to: Int) { _controller.value?.moveMediaItem(from, to) }

    // Endless/autoplay switch — continuation logic hooks in here (playback context)
    private val _endless = MutableStateFlow(false)
    val endless: StateFlow<Boolean> = _endless
    fun toggleEndless() { _endless.value = !_endless.value }

    fun toggleShuffle() {
        _controller.value?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    fun cycleRepeat() {
        _controller.value?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}

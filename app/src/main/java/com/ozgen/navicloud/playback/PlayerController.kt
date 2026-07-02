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

/** What started playback — drives endless continuation when the queue runs out. */
sealed interface PlaybackContext {
    data class Album(val albumId: String, val artistId: String?) : PlaybackContext
    data class Artist(val artistId: String) : PlaybackContext
    data class Playlist(val playlistId: String) : PlaybackContext
    data object AllSongs : PlaybackContext
    data class Genre(val genre: String) : PlaybackContext
}

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
                    maybeContinueEndless(player)
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
        // Full-width player art on 1080p screens — 600px looked soft
        val art = runCatching { musicRepository.coverArtUrl(coverArt, 1200) }.getOrNull()
        return toMediaItem(streamUrl = uri, artworkUrl = art)
    }

    // Queue header's "Şuradan çalınıyor: X" label
    private val _contextLabel = MutableStateFlow<String?>(null)
    val contextLabel: StateFlow<String?> = _contextLabel

    // What's playing right now — collection pages flip their play button to pause
    private val _currentContext = MutableStateFlow<PlaybackContext?>(null)
    val currentContext: StateFlow<PlaybackContext?> = _currentContext

    fun play(
        songs: List<Song>,
        startIndex: Int = 0,
        context: PlaybackContext? = null,
        contextLabel: String? = null,
    ) {
        playbackContext = context
        _currentContext.value = context
        _contextLabel.value = contextLabel
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

    // UID-based queue ops: UI closures can freeze stale indices (remember'd
    // callbacks), but an item's UID never lies. Always resolve at call time.
    private fun indexOfUid(uid: String): Int? {
        val c = _controller.value ?: return null
        repeat(c.mediaItemCount) { i ->
            if (c.getMediaItemAt(i).queueUid() == uid) return i
        }
        return null
    }

    fun seekToUid(uid: String) {
        indexOfUid(uid)?.let { _controller.value?.seekTo(it, 0L) }
    }

    fun removeQueueItemByUid(uid: String) {
        indexOfUid(uid)?.let { _controller.value?.removeMediaItem(it) }
    }

    fun moveQueueItemUidTo(uid: String, target: Int) {
        val c = _controller.value ?: return
        val from = indexOfUid(uid) ?: return
        c.moveMediaItem(from, target.coerceIn(0, c.mediaItemCount - 1))
    }

    /** Moves the item right after the currently playing one. */
    fun playNextByUid(uid: String) {
        val c = _controller.value ?: return
        val from = indexOfUid(uid) ?: return
        var target = c.currentMediaItemIndex + 1
        if (from < target) target--
        if (from != target) c.moveMediaItem(from, target.coerceIn(0, c.mediaItemCount - 1))
    }

    // Bildirimden gelen "player'ı aç" istekleri (MainActivity intent'i tetikler)
    private val _expandRequests = MutableStateFlow(0)
    val expandRequests: StateFlow<Int> = _expandRequests
    fun requestExpand() { _expandRequests.value++ }

    // Endless/autoplay switch — continuation logic hooks in here (playback context)
    private val _endless = MutableStateFlow(false)
    val endless: StateFlow<Boolean> = _endless
    fun toggleEndless() {
        _endless.value = !_endless.value
        _controller.value?.let { maybeContinueEndless(it) }
    }

    private var playbackContext: PlaybackContext? = null
    private var fetchingContinuation = false
    private val continuationChunk = 20

    /** Near the end of the queue with endless on → append a small chunk based on context. */
    private fun maybeContinueEndless(player: Player) {
        if (!_endless.value || fetchingContinuation) return
        val count = player.mediaItemCount
        if (count == 0 || player.currentMediaItemIndex < count - 3) return
        fetchingContinuation = true
        scope.launch {
            try {
                val c = _controller.value ?: return@launch
                val existingIds = buildSet {
                    repeat(c.mediaItemCount) { add(c.getMediaItemAt(it).mediaId) }
                }
                val candidates = fetchContinuation().filter { it.id !in existingIds }
                if (candidates.isNotEmpty()) {
                    val items = candidates.take(continuationChunk).map { it.toItem() }
                    _controller.value?.addMediaItems(items)
                }
            } finally {
                fetchingContinuation = false
            }
        }
    }

    private suspend fun fetchContinuation(): List<Song> = runCatching {
        when (val ctx = playbackContext) {
            is PlaybackContext.Album -> {
                // Continue with more from the same artist
                val artistId = ctx.artistId
                if (artistId != null) {
                    val albums = musicRepository.artist(artistId).albums.shuffled()
                    albums.firstOrNull { it.id != ctx.albumId }?.let { album ->
                        musicRepository.album(album.id).songs
                    } ?: musicRepository.randomSongs(continuationChunk)
                } else {
                    musicRepository.randomSongs(continuationChunk)
                }
            }
            is PlaybackContext.Artist ->
                musicRepository.similarSongs(ctx.artistId, continuationChunk)
                    .ifEmpty { musicRepository.randomSongs(continuationChunk) }
            is PlaybackContext.Playlist,
            is PlaybackContext.Genre,
            PlaybackContext.AllSongs,
            null,
            -> musicRepository.randomSongs(continuationChunk)
        }
    }.getOrDefault(emptyList())

    fun toggleShuffle() {
        _controller.value?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    /** Stop control: halts playback, clears the queue, closes the player UI (currentItem → null). */
    fun stop() {
        _controller.value?.run {
            stop()
            clearMediaItems()
        }
        playbackContext = null
        _currentContext.value = null
        _contextLabel.value = null
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

package com.ozgen.navicloud.desktop

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import com.ozgen.navicloud.playback.QueueTrack
import com.ozgen.navicloud.playback.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * [PlayerController]'ın masaüstü (libmpv) implementasyonu.
 *
 * Media3'ün aksine mpv'de kuyruk modeli kullanmıyoruz: kuyruk BURADA
 * yaşar (tek doğruluk kaynağı), mpv'ye her seferinde tek dosya verilir.
 * Parça bitişi poll ile yakalanır (idle'a düşüş) ve kuyruk ilerletilir.
 * UID işlemleri Android'dekiyle aynı sözleşmeye uyar.
 */
class MpvPlayerController(
    private val engine: MpvEngine,
    private val musicRepository: MusicRepository,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val queue = mutableListOf<QueueTrack>()
    private var index = -1
    private var loadedAtMs = 0L
    private var trackActive = false // loadfile sonrası gerçekten çalmaya başladı mı

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state

    private val _contextLabel = MutableStateFlow<String?>(null)
    override val contextLabel: StateFlow<String?> = _contextLabel

    private val _currentContext = MutableStateFlow<PlaybackContext?>(null)
    override val currentContext: StateFlow<PlaybackContext?> = _currentContext

    private val _endless = MutableStateFlow(false)
    override val endless: StateFlow<Boolean> = _endless

    private val _expandRequests = MutableStateFlow(0)
    override val expandRequests: StateFlow<Int> = _expandRequests

    override val streamQuality: StateFlow<StreamQuality> = MutableStateFlow(StreamQuality.RAW)

    override val positionMs: Long get() = (engine.positionSec * 1000).toLong()
    override val durationMs: Long get() = (engine.durationSec * 1000).toLong()

    private var playbackContext: PlaybackContext? = null
    private var fetchingContinuation = false

    init {
        // Parça bitişi + durum senkronu: mpv idle'a düştüyse ve parça
        // gerçekten başlamıştı → sıradakine geç
        scope.launch {
            while (true) {
                val idle = engine.isIdle
                if (trackActive && idle && System.currentTimeMillis() - loadedAtMs > 3000) {
                    trackActive = false
                    advance()
                }
                if (!idle && engine.positionSec > 0.5) trackActive = true
                syncState()
                maybeContinueEndless()
                delay(500)
            }
        }
    }

    private fun syncState() {
        _state.value = PlayerUiState(
            currentTrack = queue.getOrNull(index),
            isPlaying = queue.isNotEmpty() && index >= 0 && !engine.isPaused && !engine.isIdle,
            isBuffering = false,
            shuffle = shuffle,
            repeat = repeat,
            queue = queue.toList(),
            currentIndex = index,
        )
    }

    private var shuffle = false
    private var repeat = RepeatMode.OFF

    private suspend fun Song.toQueueTrack(): QueueTrack = QueueTrack(
        uid = UUID.randomUUID().toString(),
        song = this,
        artworkUrl = runCatching { musicRepository.coverArtUrl(coverArt, 1200) }.getOrNull(),
    )

    private fun playAt(i: Int) {
        val track = queue.getOrNull(i) ?: return
        index = i
        scope.launch {
            runCatching {
                val url = musicRepository.streamUrl(track.song.id)
                loadedAtMs = System.currentTimeMillis()
                trackActive = false
                engine.play(url)
                syncState()
            }
        }
    }

    /** Parça bitti: repeat/shuffle kurallarına göre sıradakini seç. */
    private fun advance() {
        if (queue.isEmpty()) return
        val next = when {
            repeat == RepeatMode.ONE -> index
            shuffle && queue.size > 1 -> {
                var r: Int
                do { r = queue.indices.random() } while (r == index)
                r
            }
            index + 1 < queue.size -> index + 1
            repeat == RepeatMode.ALL -> 0
            else -> {
                syncState()
                return // kuyruk bitti
            }
        }
        playAt(next)
    }

    /** Endless: kuyruğun sonuna yaklaşınca rastgele şarkılarla besle. */
    private fun maybeContinueEndless() {
        if (!_endless.value || fetchingContinuation) return
        if (queue.isEmpty() || index < queue.size - 3) return
        fetchingContinuation = true
        scope.launch {
            try {
                val existing = queue.map { it.song.id }.toSet()
                val candidates = runCatching { musicRepository.randomSongs(20) }
                    .getOrDefault(emptyList())
                    .filter { it.id !in existing }
                queue.addAll(candidates.map { it.toQueueTrack() })
                syncState()
            } finally {
                fetchingContinuation = false
            }
        }
    }

    override fun play(songs: List<Song>, startIndex: Int, context: PlaybackContext?, contextLabel: String?) {
        playbackContext = context
        _currentContext.value = context
        _contextLabel.value = contextLabel
        scope.launch {
            val tracks = songs.map { it.toQueueTrack() }
            queue.clear()
            queue.addAll(tracks)
            playAt(startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
        }
    }

    override fun playNext(songs: List<Song>) = enqueue(songs, next = true)
    override fun addToQueue(songs: List<Song>) = enqueue(songs, next = false)

    private fun enqueue(songs: List<Song>, next: Boolean) {
        scope.launch {
            val tracks = songs.map { it.toQueueTrack() }
            if (queue.isEmpty()) {
                queue.addAll(tracks)
                playAt(0)
            } else {
                if (next) queue.addAll(index + 1, tracks) else queue.addAll(tracks)
                syncState()
            }
        }
    }

    override fun togglePlayPause() {
        engine.setPaused(!engine.isPaused)
        syncState()
    }

    override fun seekTo(positionMs: Long) = engine.seekTo(positionMs / 1000.0)
    override fun skipNext() = advance()
    override fun skipPrevious() {
        if (positionMs > 3000 || index <= 0) engine.seekTo(0.0) else playAt(index - 1)
    }

    override fun seekToQueueItem(index: Int) = playAt(index)

    private fun indexOfUid(uid: String): Int? =
        queue.indexOfFirst { it.uid == uid }.takeIf { it >= 0 }

    override fun seekToUid(uid: String) { indexOfUid(uid)?.let { playAt(it) } }

    override fun removeQueueItemByUid(uid: String) {
        val i = indexOfUid(uid) ?: return
        val removingCurrent = i == index
        queue.removeAt(i)
        if (i < index) index--
        when {
            queue.isEmpty() -> stop()
            removingCurrent -> playAt(index.coerceIn(0, queue.size - 1))
            else -> syncState()
        }
    }

    override fun moveQueueItemUidTo(uid: String, target: Int) {
        val from = indexOfUid(uid) ?: return
        val to = target.coerceIn(0, queue.size - 1)
        if (from == to) return
        val item = queue.removeAt(from)
        queue.add(to, item)
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        syncState()
    }

    override fun playNextByUid(uid: String) {
        val from = indexOfUid(uid) ?: return
        var target = index + 1
        if (from < target) target--
        moveQueueItemUidTo(uid, target.coerceIn(0, queue.size - 1))
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        syncState()
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        syncState()
    }

    override fun toggleEndless() {
        _endless.value = !_endless.value
        maybeContinueEndless()
    }

    override fun stop() {
        engine.command("stop")
        queue.clear()
        index = -1
        trackActive = false
        playbackContext = null
        _currentContext.value = null
        _contextLabel.value = null
        syncState()
    }

    override fun requestExpand() { _expandRequests.value++ }
}

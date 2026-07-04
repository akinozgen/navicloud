package com.ozgen.navicloud.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.net.toUri
import com.ozgen.navicloud.audio.SleepTimerPreset
import com.ozgen.navicloud.audio.SleepTimerState
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.DownloadRepository
import com.ozgen.navicloud.data.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PlayerController]'ın Media3 implementasyonu — Android'de arkadaki
 * MediaSessionService'e MediaController ile bağlanır. Media3 tipleri bu
 * sınıfın dışına çıkmaz; UI'a [QueueTrack]/[PlayerUiState] verilir.
 */
@Singleton
class Media3PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val downloads: DownloadRepository,
    private val settings: com.ozgen.navicloud.data.SettingsRepository,
    private val queueCore: QueueCore,
) : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state

    // --- Uyku zamanlayıcı (saf app mantığı; motora dokunmaz) ---
    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    override val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer
    private var sleepJob: kotlinx.coroutines.Job? = null

    override fun startSleepTimer(preset: SleepTimerPreset) {
        sleepJob?.cancel()
        when (preset) {
            is SleepTimerPreset.Duration -> {
                val totalMs = preset.minutes * 60_000L
                _sleepTimer.value = SleepTimerState(active = true, preset = preset, remainingMs = totalMs)
                sleepJob = scope.launch {
                    val end = System.currentTimeMillis() + totalMs
                    while (true) {
                        val remaining = end - System.currentTimeMillis()
                        if (remaining <= 0) { fireSleepTimer(); break }
                        _sleepTimer.value = SleepTimerState(active = true, preset = preset, remainingMs = remaining)
                        kotlinx.coroutines.delay(1000)
                    }
                }
            }
            // Bound presetler: player olaylarında tetiklenir (onMediaItemTransition/STATE_ENDED)
            SleepTimerPreset.EndOfTrack, SleepTimerPreset.EndOfQueue ->
                _sleepTimer.value = SleepTimerState(active = true, preset = preset, remainingMs = null)
        }
    }

    override fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = SleepTimerState()
    }

    /** Tetiklendi: çalmayı duraklat, zamanlayıcıyı temizle. */
    private fun fireSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _sleepTimer.value = SleepTimerState()
        _controller.value?.pause()
    }

    init {
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val mediaController = MediaController.Builder(context, token).buildAsync().await()
            mediaController.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    syncState(player)
                    maybeContinueEndless(player)
                    if (events.containsAny(
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_IS_PLAYING_CHANGED,
                        )
                    ) {
                        scheduleQueueSave()
                    }
                }

                // "Parça bitince dur": çalan parça doğal biterse (auto/repeat
                // geçişi) duraklat. Son parçaysa AUTO geçiş olmaz → STATE_ENDED.
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val st = _sleepTimer.value
                    if (st.active && st.preset == SleepTimerPreset.EndOfTrack &&
                        (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
                    ) {
                        fireSleepTimer()
                    }
                }

                // "Kuyruk bitince dur" (+ tek parçalık EndOfTrack) → kuyruk sonu.
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    val st = _sleepTimer.value
                    if (st.active &&
                        (st.preset == SleepTimerPreset.EndOfQueue ||
                            st.preset == SleepTimerPreset.EndOfTrack)
                    ) {
                        fireSleepTimer()
                    }
                }
            })
            _controller.value = mediaController
            syncState(mediaController)
            restorePersistedQueue(mediaController)
            observeOfflineMode(mediaController)
            // Pozisyonu da kaybetmemek için periyodik kayıt
            while (true) {
                kotlinx.coroutines.delay(10_000)
                if (mediaController.mediaItemCount > 0) saveQueueNow()
            }
        }
    }

    /**
     * Offline moda geçilince mevcut kuyruğu offline'a uygun hale getirir:
     *  - indirilmemiş şarkılar kuyruktan atılır,
     *  - indirilmiş ama STREAM URL'li kalmış şarkılar (kuyruk offline'dan önce
     *    kurulduğu için) yerel dosya URL'iyle değiştirilir — offline'da tek bayt
     *    bile ağ trafiği olmaz.
     */
    private fun observeOfflineMode(c: MediaController) {
        scope.launch {
            settings.offlineMode.collect { offline ->
                if (!offline) return@collect
                val ids = downloads.downloadedIds.first().toSet()
                for (i in c.mediaItemCount - 1 downTo 0) {
                    val item = c.getMediaItemAt(i)
                    if (item.mediaId !in ids) {
                        c.removeMediaItem(i)
                    } else {
                        val uri = item.localConfiguration?.uri?.toString()
                        if (uri == null || !uri.startsWith("file")) {
                            val local = downloads.localFile(item.mediaId)
                            if (local != null) {
                                c.replaceMediaItem(i, item.buildUpon().setUri(local.toUri()).build())
                            }
                        }
                    }
                }
            }
        }
    }

    private var saveJob: kotlinx.coroutines.Job? = null

    private fun scheduleQueueSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            kotlinx.coroutines.delay(1000)
            saveQueueNow()
        }
    }

    private suspend fun saveQueueNow() {
        val c = _controller.value ?: return
        if (c.mediaItemCount == 0) return
        val songs = (0 until c.mediaItemCount).map { i -> c.getMediaItemAt(i).toSong() }
        queueCore.persist(songs, c.currentMediaItemIndex, c.currentPosition, _contextLabel.value)
    }

    private suspend fun restorePersistedQueue(c: MediaController) {
        if (c.mediaItemCount > 0) return // servis zaten çalıyor
        runCatching {
            val saved = queueCore.restore() ?: return
            val items = saved.songs.map { it.toItem() }
            _contextLabel.value = saved.contextLabel
            c.setMediaItems(items, saved.index.coerceIn(0, items.size - 1), saved.positionMs)
            c.prepare()
            // Bilinçli: otomatik ÇALMAZ — kaldığın yerden hazır bekler
        }
    }

    private fun MediaItem.toQueueTrack() = QueueTrack(
        uid = queueUid(),
        song = toSong(),
        artworkUrl = mediaMetadata.artworkUri?.toString(),
    )

    private fun syncState(player: Player) {
        _state.value = PlayerUiState(
            currentTrack = player.currentMediaItem?.toQueueTrack(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            },
            queue = List(player.mediaItemCount) { player.getMediaItemAt(it).toQueueTrack() },
            currentIndex = player.currentMediaItemIndex,
        )
    }

    override val positionMs: Long get() = _controller.value?.currentPosition ?: 0L
    override val durationMs: Long get() = _controller.value?.duration?.coerceAtLeast(0) ?: 0L

    /** Aktif akış kalitesi (codec rozetinde 'transcode' göstergesi için de kullanılır). */
    override val streamQuality = settings.streamQuality
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, com.ozgen.navicloud.data.StreamQuality.RAW)

    /** Prefers the downloaded local file; falls back to an authenticated stream URL. */
    private suspend fun Song.toItem(): MediaItem {
        val localUri = downloads.localFile(id)?.toUri()?.toString()
        val uri = localUri ?: run {
            val q = settings.streamQuality.first()
            musicRepository.streamUrl(id, q.kbps, if (q.kbps != null) "mp3" else null)
        }
        // Full-width player art on 1080p screens — 600px looked soft
        val art = runCatching { musicRepository.coverArtUrl(coverArt, 1200) }.getOrNull()
        return toMediaItem(streamUrl = uri, artworkUrl = art)
    }

    /** Offline modda yalnız indirilenler çalınır; hiçbiri yoksa kullanıcıya söylenir. */
    private suspend fun filterForOffline(songs: List<Song>): List<Song> {
        val filtered = queueCore.filterForOffline(songs)
        if (filtered.size == songs.size) return songs
        if (filtered.isEmpty() && songs.isNotEmpty()) {
            android.widget.Toast.makeText(
                context,
                "Offline mod: bu içerikte indirilmiş şarkı yok",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
        return filtered
    }

    // Queue header's "Şuradan çalınıyor: X" label
    private val _contextLabel = MutableStateFlow<String?>(null)
    override val contextLabel: StateFlow<String?> = _contextLabel

    // What's playing right now — collection pages flip their play button to pause
    private val _currentContext = MutableStateFlow<PlaybackContext?>(null)
    override val currentContext: StateFlow<PlaybackContext?> = _currentContext

    override fun play(
        songs: List<Song>,
        startIndex: Int,
        context: PlaybackContext?,
        contextLabel: String?,
        startPositionMs: Long,
    ) {
        playbackContext = context
        _currentContext.value = context
        _contextLabel.value = contextLabel
        scope.launch {
            val playable = filterForOffline(songs)
            if (playable.isEmpty()) return@launch
            // Filtre sonrası başlangıç şarkısını koru
            val effectiveIndex = songs.getOrNull(startIndex)
                ?.let { target -> playable.indexOfFirst { it.id == target.id } }
                ?.takeIf { it >= 0 } ?: 0
            val items = playable.map { it.toItem() }
            _controller.value?.run {
                // Başlangıç konumu setMediaItems'a verilir → prepare/play sonrası sıfırlanmaz (yarışsız)
                setMediaItems(items, effectiveIndex, startPositionMs.coerceAtLeast(0L))
                prepare()
                play()
            }
        }
    }

    override fun playNext(songs: List<Song>) = enqueue(songs, next = true)
    override fun addToQueue(songs: List<Song>) = enqueue(songs, next = false)

    private fun enqueue(songs: List<Song>, next: Boolean) {
        scope.launch {
            val playable = filterForOffline(songs)
            if (playable.isEmpty()) return@launch
            val items = playable.map { it.toItem() }
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

    override fun togglePlayPause() {
        _controller.value?.run { if (isPlaying) pause() else play() }
    }

    override fun seekTo(positionMs: Long) { _controller.value?.seekTo(positionMs) }
    override fun skipNext() { _controller.value?.seekToNextMediaItem() }
    override fun skipPrevious() { _controller.value?.seekToPrevious() }
    override fun seekToQueueItem(index: Int) { _controller.value?.seekTo(index, 0L) }

    // UID-based queue ops: UI closures can freeze stale indices (remember'd
    // callbacks), but an item's UID never lies. Always resolve at call time.
    private fun indexOfUid(uid: String): Int? {
        val c = _controller.value ?: return null
        repeat(c.mediaItemCount) { i ->
            if (c.getMediaItemAt(i).queueUid() == uid) return i
        }
        return null
    }

    override fun seekToUid(uid: String) {
        indexOfUid(uid)?.let { _controller.value?.seekTo(it, 0L) }
    }

    override fun removeQueueItemByUid(uid: String) {
        indexOfUid(uid)?.let { _controller.value?.removeMediaItem(it) }
    }

    override fun moveQueueItemUidTo(uid: String, target: Int) {
        val c = _controller.value ?: return
        val from = indexOfUid(uid) ?: return
        c.moveMediaItem(from, target.coerceIn(0, c.mediaItemCount - 1))
    }

    /** Moves the item right after the currently playing one. */
    override fun playNextByUid(uid: String) {
        val c = _controller.value ?: return
        val from = indexOfUid(uid) ?: return
        var target = c.currentMediaItemIndex + 1
        if (from < target) target--
        if (from != target) c.moveMediaItem(from, target.coerceIn(0, c.mediaItemCount - 1))
    }

    // Bildirimden gelen "player'ı aç" istekleri (MainActivity intent'i tetikler)
    private val _expandRequests = MutableStateFlow(0)
    override val expandRequests: StateFlow<Int> = _expandRequests
    override fun requestExpand() { _expandRequests.value++ }

    // Endless/autoplay switch — continuation logic hooks in here (playback context)
    private val _endless = MutableStateFlow(false)
    override val endless: StateFlow<Boolean> = _endless
    override fun toggleEndless() {
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

    private suspend fun fetchContinuation(): List<Song> =
        queueCore.fetchContinuation(playbackContext, continuationChunk)

    override fun toggleShuffle() {
        _controller.value?.run { shuffleModeEnabled = !shuffleModeEnabled }
    }

    /** Stop control: halts playback, clears the queue, closes the player UI (currentItem → null). */
    override fun stop() {
        _controller.value?.run {
            stop()
            clearMediaItems()
        }
        playbackContext = null
        _currentContext.value = null
        _contextLabel.value = null
        cancelSleepTimer()
        scope.launch { queueCore.clearPersisted() }
    }

    override fun cycleRepeat() {
        _controller.value?.run {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}

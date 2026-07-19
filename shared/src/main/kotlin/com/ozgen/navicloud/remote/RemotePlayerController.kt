package com.ozgen.navicloud.remote

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Uzak cihazı süren [PlayerController]. Metotlar [Cmd] gönderir (fire-and-forget; otorite sunucudan gelen
 * bir sonraki [StateMsg]); gelen state [state] akışına yansır. UI/mini/plak bu controller ile hiçbir fark
 * görmeden uzak cihazı kumanda eder.
 *
 * Kapak URL'i wire'da gelmez → [artworkResolver] ile controller KENDİ oturumundan türetilir (coverArt id
 * bazında cache'lenir — saniyelik state tick'leri ek maliyet üretmez). Pozisyon [interpolatedPositionMs]
 * ile son snapshot'tan interpole edilir. Bağlantı kapanınca [onClosed] bir kez çağrılır (manager fallback'i).
 */
class RemotePlayerController(
    private val connection: RcConnection,
    private val scope: CoroutineScope,
    private val artworkResolver: suspend (Song) -> String?,
    private val onClosed: () -> Unit = {},
    /** Alıcı bilerek düşürdü (Bye) → manager otomatik reconnect ETMESİN. onClosed'dan ÖNCE çağrılır. */
    private val onKicked: () -> Unit = {},
) : PlayerController {

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _contextLabel = MutableStateFlow<String?>(null)
    override val contextLabel: StateFlow<String?> = _contextLabel.asStateFlow()

    private val _currentContext = MutableStateFlow<PlaybackContext?>(null)
    override val currentContext: StateFlow<PlaybackContext?> = _currentContext.asStateFlow()

    private val _endless = MutableStateFlow(false)
    override val endless: StateFlow<Boolean> = _endless.asStateFlow()

    private val _expandRequests = MutableStateFlow(0)
    override val expandRequests: StateFlow<Int> = _expandRequests.asStateFlow()

    private val _streamQuality = MutableStateFlow(StreamQuality.HIGH)
    override val streamQuality: StateFlow<StreamQuality> = _streamQuality.asStateFlow()

    /** Son tel snapshot'ı — manager "akıllı aktarım" kararını ilk snapshot'tan verir. */
    private val _snapshot = MutableStateFlow<WireState?>(null)
    val snapshot: StateFlow<WireState?> = _snapshot.asStateFlow()

    /** Sunucu reddi (busy/auth/protocol) — manager timeout beklemeden FAILED'e geçer. */
    private val _rejected = MutableStateFlow<String?>(null)
    val rejected: StateFlow<String?> = _rejected.asStateFlow()

    // coverArt id → çözülmüş URL (bu cihazın oturumu). Tick başına yeniden çözmemek için.
    private val artCache = HashMap<String, String?>()
    private val seq = AtomicLong(0)

    // Heartbeat (RC-4): karşıdan son mesaj zamanı; PING_INTERVAL'da bir Ping, HEARTBEAT_TIMEOUT
    // boyunca hiç mesaj gelmezse bağlantı ölü sayılır → kapat (incoming biter → onClosed → manager reconnect).
    @Volatile private var lastRxMs = System.currentTimeMillis()
    @Volatile private var closed = false

    init {
        scope.launch {
            try {
                connection.incoming.collect { msg ->
                    lastRxMs = System.currentTimeMillis()
                    when (msg) {
                        is StateMsg -> {
                            val snap = msg.snapshot
                            _snapshot.value = snap
                            resolveArtCache(snap.queue.map { it.song })
                            _state.value = snap.toUiState { song -> resolveArt(song) }
                            _contextLabel.value = snap.contextLabel
                            _endless.value = snap.endless
                            _currentContext.value = snap.context
                        }
                        is Reject -> _rejected.value = msg.reason
                        is Bye -> { onKicked(); connection.close() } // alıcı düşürdü → reconnect YOK
                        else -> Unit // Welcome/SessionInfo/Pong: lastRxMs güncellemesi yeterli (liveness)
                    }
                }
            } finally {
                onClosed()
            }
        }
        // Heartbeat döngüsü
        scope.launch {
            while (isActive && !closed) {
                delay(PING_INTERVAL_MS)
                if (closed) break
                runCatching { connection.send(Ping) }
                if (System.currentTimeMillis() - lastRxMs > HEARTBEAT_TIMEOUT_MS) {
                    connection.close() // → incoming biter → onClosed
                    break
                }
            }
        }
        send(Cmd(RcOp.REQUEST_STATE, seq.incrementAndGet()))
    }

    // toUiState sync lambda ister; cache'i collect coroutine'inde önden doldururuz.
    private suspend fun resolveArtCache(songs: List<Song>) {
        for (s in songs) {
            val key = s.coverArt ?: continue
            if (key !in artCache) artCache[key] = runCatching { artworkResolver(s) }.getOrNull()
        }
    }

    private fun resolveArt(song: Song): String? = song.coverArt?.let { artCache[it] }

    private fun send(cmd: Cmd) {
        scope.launch { runCatching { connection.send(cmd) } }
    }

    private fun op(op: RcOp) = send(Cmd(op, seq.incrementAndGet()))

    override val positionMs: Long
        get() = _snapshot.value?.let { interpolatedPositionMs(it, System.currentTimeMillis()) } ?: 0L
    override val durationMs: Long
        get() = _snapshot.value?.durationMs ?: 0L

    override fun play(
        songs: List<Song>,
        startIndex: Int,
        context: PlaybackContext?,
        contextLabel: String?,
        startPositionMs: Long,
    ) = send(
        Cmd(
            RcOp.SET_QUEUE, seq.incrementAndGet(),
            songs = songs, startIndex = startIndex, positionMs = startPositionMs,
            context = context, contextLabel = contextLabel,
        ),
    )

    override fun playNext(songs: List<Song>) = send(Cmd(RcOp.PLAY_NEXT, seq.incrementAndGet(), songs = songs))
    override fun addToQueue(songs: List<Song>) = send(Cmd(RcOp.ADD_QUEUE, seq.incrementAndGet(), songs = songs))
    override fun togglePlayPause() = op(RcOp.PAUSE_TOGGLE)
    override fun seekTo(positionMs: Long) = send(Cmd(RcOp.SEEK, seq.incrementAndGet(), positionMs = positionMs))
    override fun skipNext() = op(RcOp.NEXT)
    override fun skipPrevious() = op(RcOp.PREV)
    override fun seekToQueueItem(index: Int) = send(Cmd(RcOp.SEEK_INDEX, seq.incrementAndGet(), index = index))
    override fun seekToUid(uid: String) = send(Cmd(RcOp.SEEK_UID, seq.incrementAndGet(), uid = uid))
    override fun removeQueueItemByUid(uid: String) = send(Cmd(RcOp.REMOVE_UID, seq.incrementAndGet(), uid = uid))
    override fun moveQueueItemUidTo(uid: String, target: Int) =
        send(Cmd(RcOp.MOVE_UID, seq.incrementAndGet(), uid = uid, target = target))

    // "Çalanın hemen arkasına taşı" = mevcut index+1'e MOVE (ayrı op'a gerek yok; state'ten türetilir).
    override fun playNextByUid(uid: String) =
        send(Cmd(RcOp.MOVE_UID, seq.incrementAndGet(), uid = uid, target = _state.value.currentIndex + 1))

    override fun toggleShuffle() = op(RcOp.SHUFFLE)
    override fun cycleRepeat() = op(RcOp.REPEAT)
    override fun toggleEndless() = op(RcOp.ENDLESS)
    override fun stop() = op(RcOp.STOP)

    // Uzağı sürerken "player'ı aç" → BU cihazın UI sheet'ini açar (lokal davranış korunur).
    override fun requestExpand() {
        _expandRequests.value += 1
    }

    fun close() {
        closed = true
        connection.close()
    }

    companion object {
        const val PING_INTERVAL_MS = 2_000L
        const val HEARTBEAT_TIMEOUT_MS = 5_000L
    }
}

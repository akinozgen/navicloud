package com.ozgen.navicloud.remote

import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import com.ozgen.navicloud.playback.QueueTrack
import com.ozgen.navicloud.playback.RepeatMode

/**
 * Ses seviyesi çıkışı — sunucu tarafında platform bağlar (desktop mpv, Android null).
 * `sharedUi`'daki VolumeController'a bağımlı olmamak için `:shared` içinde ayrı ufak port.
 */
interface VolumeSink {
    /** 0f..1f */
    var volume: Float
}

/** Büyük kuyrukta gönderilen pencere — QueueSyncManager ile aynı sınırlar. */
const val RC_MAX_QUEUE = 500
const val RC_LOOKBACK = 100

/**
 * Oynatıcı durumunu tele çevirir. Kuyruk > [maxQueue] ise current çevresinde [lookback] geriye + ileri
 * pencere alınır; [WireState.currentIndex] pencereye göre yeniden hesaplanır.
 */
fun PlayerUiState.toWire(
    rev: Long,
    positionMs: Long,
    durationMs: Long,
    contextLabel: String?,
    volume: Float?,
    nowMs: Long,
    endless: Boolean = false,
    context: com.ozgen.navicloud.playback.PlaybackContext? = null,
    maxQueue: Int = RC_MAX_QUEUE,
    lookback: Int = RC_LOOKBACK,
): WireState {
    val safeIdx = currentIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    val windowStart = if (queue.size <= maxQueue) 0 else (safeIdx - lookback).coerceAtLeast(0)
    val windowed = if (queue.size <= maxQueue) queue else queue.drop(windowStart).take(maxQueue)
    val idxInWindow = (safeIdx - windowStart).coerceIn(0, (windowed.size - 1).coerceAtLeast(0))
    return WireState(
        rev = rev,
        current = currentTrack?.let { WireTrack(it.uid, it.song) },
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        shuffle = shuffle,
        repeat = repeat.name,
        queue = windowed.map { WireTrack(it.uid, it.song) },
        currentIndex = idxInWindow,
        positionMs = positionMs,
        durationMs = durationMs,
        asOfEpochMs = nowMs,
        contextLabel = contextLabel,
        volume = volume,
        endless = endless,
        context = context,
    )
}

/**
 * Tel durumunu controller'ın gördüğü [PlayerUiState]'e çevirir. Kapak URL'i wire'da yok →
 * [artworkResolver] ile controller KENDİ oturumundan türetir (null = kapaksız çizim).
 */
fun WireState.toUiState(artworkResolver: (Song) -> String? = { null }): PlayerUiState =
    PlayerUiState(
        currentTrack = current?.let { QueueTrack(it.uid, it.song, artworkResolver(it.song)) },
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        shuffle = shuffle,
        repeat = runCatching { RepeatMode.valueOf(repeat) }.getOrDefault(RepeatMode.OFF),
        queue = queue.map { QueueTrack(it.uid, it.song, artworkResolver(it.song)) },
        currentIndex = currentIndex,
    )

/**
 * Controller'ın seek bar'ı için: son snapshot'tan bu yana geçen süreyi ekler (yalnız çalarken),
 * [WireState.durationMs]'e clamp'ler. Böylece sunucu her saniye state push'lamak zorunda değil.
 */
fun interpolatedPositionMs(s: WireState, nowMs: Long): Long =
    if (s.isPlaying) {
        val upper = if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE
        (s.positionMs + (nowMs - s.asOfEpochMs)).coerceIn(0L, upper)
    } else {
        s.positionMs
    }

/**
 * Gelen komutu lokal [PlayerController]'a uygular. **Çağrı `playerDispatcher`'da yapılmalı**
 * (Android Media3 yalnız ana thread — QueueSyncManager ile aynı kural). Tanınmayan/eksik argüman = no-op.
 */
suspend fun applyCmd(cmd: Cmd, player: PlayerController, volume: VolumeSink?) {
    when (cmd.op) {
        RcOp.PLAY, RcOp.SET_QUEUE -> {
            val songs = cmd.songs ?: return
            player.play(
                songs = songs,
                startIndex = cmd.startIndex ?: 0,
                context = cmd.context,
                contextLabel = cmd.contextLabel,
                startPositionMs = cmd.positionMs ?: 0L,
            )
        }
        RcOp.ADD_QUEUE -> cmd.songs?.let { player.addToQueue(it) }
        RcOp.PLAY_NEXT -> cmd.songs?.let { player.playNext(it) }
        RcOp.PAUSE_TOGGLE -> player.togglePlayPause()
        RcOp.NEXT -> player.skipNext()
        RcOp.PREV -> player.skipPrevious()
        RcOp.SEEK -> cmd.positionMs?.let { player.seekTo(it) }
        RcOp.SEEK_INDEX -> cmd.index?.let { player.seekToQueueItem(it) }
        RcOp.SEEK_UID -> cmd.uid?.let { player.seekToUid(it) }
        RcOp.REMOVE_UID -> cmd.uid?.let { player.removeQueueItemByUid(it) }
        RcOp.MOVE_UID -> {
            val uid = cmd.uid
            val target = cmd.target
            if (uid != null && target != null) player.moveQueueItemUidTo(uid, target)
        }
        RcOp.SHUFFLE -> player.toggleShuffle()
        RcOp.REPEAT -> player.cycleRepeat()
        RcOp.ENDLESS -> player.toggleEndless()
        RcOp.STOP -> player.stop()
        RcOp.VOLUME -> cmd.volume?.let { v -> volume?.let { it.volume = v } }
        RcOp.REQUEST_STATE -> Unit // Sunucu güncel snapshot'ı zaten push'lar.
    }
}

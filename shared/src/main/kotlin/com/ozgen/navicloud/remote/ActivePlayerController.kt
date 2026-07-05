package com.ozgen.navicloud.remote

import com.ozgen.navicloud.audio.SleepTimerPreset
import com.ozgen.navicloud.audio.SleepTimerState
import com.ozgen.navicloud.core.model.Song
import com.ozgen.navicloud.data.StreamQuality
import com.ozgen.navicloud.playback.PlaybackContext
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * UI'ın gördüğü TEK sabit [PlayerController] referansı. İçteki [delegate] lokal (Media3/mpv) ile
 * uzak ([RemotePlayerController]) arasında swap edilir; StateFlow'lar `flatMapLatest` ile delegate
 * değişiminde reaktif olarak yeni kaynağa bağlanır → ekranlar/mini/plak hiçbir şey bilmeden geçiş yapar.
 *
 * Swap yalnızca [RemoteControlManager] tarafından yapılır. Başlangıç = lokal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivePlayerController(
    private val local: PlayerController,
    scope: CoroutineScope,
) : PlayerController {

    private val delegateFlow = MutableStateFlow(local)

    /** Şu anki hedef controller — manager set eder; UI doğrudan dokunmaz. */
    var delegate: PlayerController
        get() = delegateFlow.value
        set(value) {
            delegateFlow.value = value
        }

    val isLocal: Boolean get() = delegateFlow.value === local

    override val state: StateFlow<PlayerUiState> =
        delegateFlow.flatMapLatest { it.state }
            .stateIn(scope, SharingStarted.Eagerly, local.state.value)

    override val contextLabel: StateFlow<String?> =
        delegateFlow.flatMapLatest { it.contextLabel }
            .stateIn(scope, SharingStarted.Eagerly, local.contextLabel.value)

    override val currentContext: StateFlow<PlaybackContext?> =
        delegateFlow.flatMapLatest { it.currentContext }
            .stateIn(scope, SharingStarted.Eagerly, local.currentContext.value)

    override val endless: StateFlow<Boolean> =
        delegateFlow.flatMapLatest { it.endless }
            .stateIn(scope, SharingStarted.Eagerly, local.endless.value)

    override val expandRequests: StateFlow<Int> =
        delegateFlow.flatMapLatest { it.expandRequests }
            .stateIn(scope, SharingStarted.Eagerly, local.expandRequests.value)

    override val streamQuality: StateFlow<StreamQuality> =
        delegateFlow.flatMapLatest { it.streamQuality }
            .stateIn(scope, SharingStarted.Eagerly, local.streamQuality.value)

    override val sleepTimer: StateFlow<SleepTimerState> =
        delegateFlow.flatMapLatest { it.sleepTimer }
            .stateIn(scope, SharingStarted.Eagerly, local.sleepTimer.value)

    override val positionMs: Long get() = delegateFlow.value.positionMs
    override val durationMs: Long get() = delegateFlow.value.durationMs

    override fun play(
        songs: List<Song>,
        startIndex: Int,
        context: PlaybackContext?,
        contextLabel: String?,
        startPositionMs: Long,
    ) = delegateFlow.value.play(songs, startIndex, context, contextLabel, startPositionMs)

    override fun playNext(songs: List<Song>) = delegateFlow.value.playNext(songs)
    override fun addToQueue(songs: List<Song>) = delegateFlow.value.addToQueue(songs)
    override fun togglePlayPause() = delegateFlow.value.togglePlayPause()
    override fun seekTo(positionMs: Long) = delegateFlow.value.seekTo(positionMs)
    override fun skipNext() = delegateFlow.value.skipNext()
    override fun skipPrevious() = delegateFlow.value.skipPrevious()
    override fun seekToQueueItem(index: Int) = delegateFlow.value.seekToQueueItem(index)
    override fun seekToUid(uid: String) = delegateFlow.value.seekToUid(uid)
    override fun removeQueueItemByUid(uid: String) = delegateFlow.value.removeQueueItemByUid(uid)
    override fun moveQueueItemUidTo(uid: String, target: Int) = delegateFlow.value.moveQueueItemUidTo(uid, target)
    override fun playNextByUid(uid: String) = delegateFlow.value.playNextByUid(uid)
    override fun toggleShuffle() = delegateFlow.value.toggleShuffle()
    override fun cycleRepeat() = delegateFlow.value.cycleRepeat()
    override fun toggleEndless() = delegateFlow.value.toggleEndless()
    override fun stop() = delegateFlow.value.stop()
    override fun requestExpand() = delegateFlow.value.requestExpand()

    // Uyku zamanlayıcı platform yeteneği — uzakta yok (kapsam kararı), her zaman LOKALE gider.
    override fun startSleepTimer(preset: SleepTimerPreset) = local.startSleepTimer(preset)
    override fun cancelSleepTimer() = local.cancelSleepTimer()
}

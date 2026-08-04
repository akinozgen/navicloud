package com.ozgen.navicloud.widget

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget güncellemesini sürükleyen singleton (updatePeriodMillis=0 → biz sürüyoruz).
 *
 * PlaybackService onCreate'te canlı player'a bağlanır ([attach]); player
 * olaylarını debounce'layıp (~500ms) widget'ları yeniden çizer. Servis ölünce
 * ([detach]) idle/soğuk durum push edilir. Provider'lar da (widget eklendi /
 * yeniden boyutlandı) [requestRender] ile aynı yolu tetikler.
 *
 * İş bölümü: player YALNIZ ana thread'de okunur (Media3 kuralı), render (bitmap)
 * IO'da. [renderNow] önce ana thread'de ham durumu kopyalar, sonra IO'ya geçer.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    private val stateProvider: WidgetStateProvider,
    private val renderer: WidgetRenderer,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var player: Player? = null
    private var debounceJob: Job? = null
    private var tickJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                )
            ) {
                scheduleRender()
            }
        }
    }

    /** PlaybackService.onCreate — ana thread'de çağrılır (servis lifecycle main'de). */
    fun attach(p: Player) {
        player = p
        p.addListener(listener)
        scheduleRender()
        startTick()
    }

    /** PlaybackService.onDestroy — canlı bağı kes, idle/soğuk durum push et. */
    fun detach() {
        player?.removeListener(listener)
        player = null
        stopTick()
        renderNow()
    }

    /** Provider'lardan (onUpdate / onAppWidgetOptionsChanged) tetiklenir. */
    fun requestRender() = renderNow()

    private fun scheduleRender() {
        debounceJob?.cancel()
        debounceJob = mainScope.launch {
            delay(500)
            renderNow()
        }
    }

    private fun renderNow() {
        mainScope.launch {
            // Ana thread: canlı player'ı ham veriye kopyala (yoksa soğuk yol = null)
            val raw = player?.let { stateProvider.readPlayer(it) }
            withContext(Dispatchers.IO) {
                val snapshot = stateProvider.resolve(raw)
                runCatching { renderer.render(snapshot) }
            }
        }
    }

    /**
     * Kaba progress ilerlemesi: yalnız çalarken ~20sn'de bir yeniden çiz.
     * Saniyelik değil (pil/ANR) — olay-tetikli çizime ek yumuşak dokunuş.
     */
    private fun startTick() {
        tickJob?.cancel()
        tickJob = mainScope.launch {
            while (true) {
                delay(20_000)
                val p = player ?: break
                if (p.isPlaying) renderNow()
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }
}

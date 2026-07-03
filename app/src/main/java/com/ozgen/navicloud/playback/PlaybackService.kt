package com.ozgen.navicloud.playback

import android.content.Intent
import android.net.ConnectivityManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var streamCache: StreamCache
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var audioEffects: com.ozgen.navicloud.data.AudioEffectsRepository

    private var mediaSession: MediaSession? = null
    private var audioEffectsEngine: AudioEffectsEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Sabit audio session id: audiofx efektleri buna bağlanır (playback
        // başlamadan bilinsin diye kendimiz üretip player'a veriyoruz)
        val audioSessionId = androidx.media3.common.util.Util.generateAudioSessionIdV21(this)
        val player = ExoPlayer.Builder(this)
            // Akış LRU cache üzerinden: aynı şarkı ikinci kez ağa çıkmaz;
            // file:// (indirmeler) cache'e uğramadan doğrudan okunur
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    streamCache.playerDataSourceFactory()
                )
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.setAudioSessionId(audioSessionId)
        // EQ + ses efektleri motorunu bu oturuma bağla (DataStore durumunu uygular)
        audioEffectsEngine = AudioEffectsEngine(audioSessionId, audioEffects, scope).also { it.start() }
        player.addListener(scrobbleListener(player))
        player.addListener(prefetchListener(player))
        // Notification tap opens the app with the player expanded
        val sessionIntent = android.content.Intent(this, com.ozgen.navicloud.MainActivity::class.java).apply {
            action = ACTION_OPEN_PLAYER
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = android.app.PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.ozgen.navicloud.OPEN_PLAYER"

        /** Sıradaki kaç parça ısıtılır ve her birinden kaç bayt (≈50sn @320kbps). */
        private const val PREFETCH_TRACKS = 2
        private const val PREFETCH_BYTES = 2L * 1024 * 1024
    }

    private var prefetchJob: Job? = null

    /**
     * Sıradaki 1-2 parçanın ilk chunk'ını akış cache'ine önden yazar; parça
     * geçişinde ısınma sesi anında başlar. Track değişince eski iş iptal olur,
     * indirilmiş (file://) parçalar atlanır, metered ağda ayara uyar.
     */
    private fun prefetchListener(player: Player) = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
            schedulePrefetch(player)

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) =
            schedulePrefetch(player)
    }

    private fun schedulePrefetch(player: Player) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            // Önce çalan parça buffer'lansın; art arda queue değişikliklerini
            // de tek işe indirger
            delay(3_000)
            if (!settings.prefetchEnabled.first()) return@launch
            val cm = getSystemService(ConnectivityManager::class.java)
            if (cm != null && cm.isActiveNetworkMetered && settings.prefetchWifiOnly.first()) return@launch

            // Player'a yalnız main thread'den dokunulur
            val nextUris = withContext(Dispatchers.Main) {
                val from = player.currentMediaItemIndex
                (1..PREFETCH_TRACKS).mapNotNull { off ->
                    val i = from + off
                    if (i < player.mediaItemCount) {
                        player.getMediaItemAt(i).localConfiguration?.uri
                            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
                    } else null
                }
            }
            for (uri in nextUris) {
                val dataSpec = DataSpec.Builder()
                    .setUri(uri).setPosition(0).setLength(PREFETCH_BYTES)
                    .build()
                val writer = CacheWriter(
                    streamCache.cacheDataSourceFactory().createDataSource(),
                    dataSpec,
                    /* temporaryBuffer = */ null,
                    /* progressListener = */ null,
                )
                runCatching {
                    // cache() bloklar; iptal Thread.interrupt ile ulaşır
                    runInterruptible(Dispatchers.IO) { writer.cache() }
                }
            }
        }
    }

    /**
     * Navidrome scrobbling: "now playing" on every transition, submission when a
     * track finished naturally or was skipped after at least half (or 4 minutes).
     */
    private fun scrobbleListener(player: Player) = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId ?: return
            scope.launch { musicRepository.scrobble(id, submission = false) }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return
            val old = oldPosition.mediaItem ?: return
            val durationSec = old.mediaMetadata.extras?.getInt(MediaKeys.DURATION, 0) ?: 0
            val playedMs = oldPosition.positionMs
            val shouldSubmit = shouldSubmitScrobble(
                durationSec = durationSec,
                playedMs = playedMs,
                naturalEnd = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            )
            if (shouldSubmit) {
                scope.launch { musicRepository.scrobble(old.mediaId, submission = true) }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    // Stop playback when the user swipes the app away instead of lingering silently.
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        audioEffectsEngine?.release()
        audioEffectsEngine = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

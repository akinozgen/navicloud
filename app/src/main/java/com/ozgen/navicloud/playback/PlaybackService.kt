package com.ozgen.navicloud.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ozgen.navicloud.data.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var musicRepository: MusicRepository

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(scrobbleListener(player))
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
            val shouldSubmit = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION ||
                (durationSec > 0 && playedMs >= minOf(durationSec * 500L, 240_000L))
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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

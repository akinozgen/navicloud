package com.ozgen.navicloud

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.ozgen.navicloud.data.DownloadRepository
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.data.RecentSearchesStore
import com.ozgen.navicloud.data.ServerRepository
import com.ozgen.navicloud.data.SettingsRepository
import androidx.lifecycle.lifecycleScope
import com.ozgen.navicloud.playback.PlaybackService
import com.ozgen.navicloud.playback.PlayerController
import com.ozgen.navicloud.playback.QueueSyncManager
import kotlinx.coroutines.launch
import com.ozgen.navicloud.ui.AppContainer
import com.ozgen.navicloud.ui.LocalAppContainer
import com.ozgen.navicloud.ui.NaviCloudRoot
import com.ozgen.navicloud.ui.screens.servers.ServersScreen
import com.ozgen.navicloud.ui.theme.NaviCloudTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var serverRepository: ServerRepository
    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var recentSearches: RecentSearchesStore
    @Inject lateinit var audioEffects: com.ozgen.navicloud.audio.AudioEffectsController
    @Inject lateinit var queueSync: QueueSyncManager
    @Inject lateinit var remoteControl: com.ozgen.navicloud.remote.RemoteControlManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        queueSync.start()
        handleOpenPlayerIntent(intent)
        setContent {
            // Paylaşılan UI'ın bağımlılık kabı — Hilt grafiğinden beslenir
            val container = remember {
                AppContainer(
                    music = musicRepository,
                    player = playerController,
                    servers = serverRepository,
                    downloads = downloadRepository,
                    offline = settingsRepository,
                    recents = recentSearches,
                    audioEffects = audioEffects,
                    queueSync = queueSync,
                    remoteControl = remoteControl,
                )
            }
            CompositionLocalProvider(
                LocalAppContainer provides container,
            ) {
                NaviCloudTheme {
                    NaviCloudRoot(
                        platformSettings = { nav -> ServersScreen(nav) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenPlayerIntent(intent)
    }

    // Ön plana gelince başka cihazın bıraktığı kuyruğu yokla; arka plana/kapanışa geçerken
    // güncel durumu sunucuya it (throttle bypass). İkisi de sessiz fail.
    override fun onStart() {
        super.onStart()
        queueSync.requestResumeCheck()
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { queueSync.flush() }
    }

    private fun handleOpenPlayerIntent(intent: Intent?) {
        if (intent?.action == PlaybackService.ACTION_OPEN_PLAYER) {
            playerController.requestExpand()
        }
    }
}

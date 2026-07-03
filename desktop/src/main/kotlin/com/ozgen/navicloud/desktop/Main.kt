package com.ozgen.navicloud.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.ozgen.navicloud.data.MusicRepository
import com.ozgen.navicloud.ui.AppContainer
import com.ozgen.navicloud.ui.LocalAppContainer
import com.ozgen.navicloud.ui.NaviCloudRoot
import com.ozgen.navicloud.ui.theme.NaviCloudTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/** NaviCloud Desktop: paylaşılan UI + libmpv ses motoru. */
fun main() = application {
    val container = remember {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val okHttp = OkHttpClient()
        val servers = DesktopServerSource(okHttp, json)
        val offline = DesktopOfflineSource()
        val downloads = DesktopDownloads(servers, okHttp)
        val music = MusicRepository(servers, InMemoryApiCacheStore(), json, offline)
        val queueCore = com.ozgen.navicloud.playback.QueueCore(
            music, downloads, offline, FileQueueStateStore(), json,
        )
        val player = MpvPlayerController(
            MpvEngine(), music, queueCore, DesktopPrefs.streamQualityFlow,
            localFile = { id -> downloads.localPath(id) },
        ).apply { restoreQueue() }
        // Otonom doğrulama: NAVI_SELFTEST_DL=1 → indir, offline'a geç, yerelden çal
        if (System.getenv("NAVI_SELFTEST_DL") == "1") {
            @Suppress("OPT_IN_USAGE")
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val songs = music.randomSongs(1)
                    println("DLTEST indiriliyor: ${songs.first().title}")
                    downloads.enqueue(songs)
                    kotlinx.coroutines.withTimeout(90_000) {
                        downloads.totalCount.first { it >= 1 }
                    }
                    println("DLTEST indirildi, yerel=${downloads.localPath(songs.first().id)}")
                    DesktopPrefs.offlineMode = true
                    player.play(songs, 0, null, "dl-test")
                    kotlinx.coroutines.delay(7000)
                    val st = player.state.value
                    println("DLTEST offline calma: playing=${st.isPlaying} pos=${player.positionMs}ms track=${st.currentTrack?.song?.title}")
                    DesktopPrefs.offlineMode = false
                    player.stop()
                    println("DLTEST BITTI")
                }.onFailure { println("DLTEST HATA: $it") }
            }
        }
        AppContainer(
            music = music,
            player = player,
            servers = servers,
            downloads = downloads,
            offline = offline,
            recents = InMemoryRecentSearches(),
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "NaviCloud",
        state = WindowState(size = DpSize(1280.dp, 800.dp)),
    ) {
        // Coil3 masaüstü: OkHttp fetcher + disk cache (~/.navicloud/image_cache)
        setSingletonImageLoaderFactory { ctx ->
            ImageLoader.Builder(ctx)
                .components { add(OkHttpNetworkFetcherFactory()) }
                .memoryCache { MemoryCache.Builder().maxSizeBytes(128L * 1024 * 1024).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(System.getProperty("user.home"), ".navicloud/image_cache"))
                        .maxSizeBytes(250L * 1024 * 1024)
                        .build()
                }
                .crossfade(true)
                .build()
        }
        CompositionLocalProvider(LocalAppContainer provides container) {
            NaviCloudTheme {
                NaviCloudRoot(
                    platformSettings = { nav -> DesktopSettingsScreen(nav) },
                )
            }
        }
    }
}


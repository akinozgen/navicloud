package com.ozgen.navicloud.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

private class DesktopDeps(
    val container: AppContainer,
    val volume: com.ozgen.navicloud.ui.VolumeController,
)

/** Sistem tepsisi ikonu: accent yuvarlak kare + beyaz oynat üçgeni. */
private val TrayIconPainter: Painter = object : Painter() {
    override val intrinsicSize = Size(64f, 64f)
    override fun DrawScope.onDraw() {
        drawRoundRect(
            color = Color(0xFF7C4DFF),
            cornerRadius = CornerRadius(size.minDimension * 0.24f),
        )
        val triangle = Path().apply {
            moveTo(size.width * 0.40f, size.height * 0.28f)
            lineTo(size.width * 0.72f, size.height * 0.50f)
            lineTo(size.width * 0.40f, size.height * 0.72f)
            close()
        }
        drawPath(triangle, Color.White)
    }
}

/** NaviCloud Desktop: paylaşılan UI + libmpv ses motoru. */
fun main() = application {
    val deps = remember {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val okHttp = OkHttpClient()
        val servers = DesktopServerSource(okHttp, json)
        val offline = DesktopOfflineSource()
        val downloads = DesktopDownloads(servers, okHttp)
        val music = MusicRepository(servers, InMemoryApiCacheStore(), json, offline)
        val queueCore = com.ozgen.navicloud.playback.QueueCore(
            music, downloads, offline, FileQueueStateStore(), json,
        )
        // Coil3 tek görsel yükleyici — mini pencere de aynı loader'ı kullanır,
        // o yüzden pencereye değil sürece kur (setSafe: bir kez ayarlar)
        coil3.SingletonImageLoader.setSafe { ctx ->
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
        val engine = MpvEngine().apply { volume = DesktopPrefs.volume }
        val player = MpvPlayerController(
            engine, music, queueCore, DesktopPrefs.streamQualityFlow,
            localFile = { id -> downloads.localPath(id) },
        ).apply { restoreQueue() }
        DesktopDeps(
            container = AppContainer(
                music = music,
                player = player,
                servers = servers,
                downloads = downloads,
                offline = offline,
                recents = InMemoryRecentSearches(),
            ),
            volume = object : com.ozgen.navicloud.ui.VolumeController {
                override var volume: Float
                    get() = engine.volume / 100f
                    set(value) {
                        engine.volume = (value * 100).toInt()
                        DesktopPrefs.volume = (value * 100).toInt()
                    }
            },
        )
    }

    val player = deps.container.player
    val playerState by player.state.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }
    var miniOpen by remember { mutableStateOf(false) }
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))

    fun showWindow() {
        miniOpen = false
        windowState.isMinimized = false
        windowVisible = true
    }

    fun openMini() {
        miniOpen = true
        windowVisible = false
    }

    // Sistem tepsisi: pencere gizliyken de çalmayı yönetmek için. Sol tık
    // pencereyi getirir, sağ tık menüyü açar.
    Tray(
        icon = TrayIconPainter,
        tooltip = playerState.currentTrack?.let { "${it.song.artist} — ${it.song.title}" } ?: "NaviCloud",
        onAction = { showWindow() },
        menu = {
            Item("Göster", onClick = { showWindow() })
            Item("Mini oynatıcı", onClick = { openMini() })
            Separator()
            Item(
                if (playerState.isPlaying) "Duraklat" else "Çal",
                enabled = playerState.currentTrack != null,
                onClick = { player.togglePlayPause() },
            )
            Item("Önceki", enabled = playerState.currentTrack != null, onClick = { player.skipPrevious() })
            Item("Sonraki", enabled = playerState.currentTrack != null, onClick = { player.skipNext() })
            Separator()
            Item("Çıkış", onClick = ::exitApplication)
        },
    )

    Window(
        onCloseRequest = {
            // Ayar açıksa pencereyi tepsiye küçült; kapalıysa tamamen çık
            if (DesktopPrefs.closeToTray) windowVisible = false else exitApplication()
        },
        visible = windowVisible,
        state = windowState,
        title = "NaviCloud",
    ) {
        // Gizliyken tekrar gösterilince öne getir (tepsiden dönüş)
        LaunchedEffect(windowVisible) {
            if (windowVisible) runCatching { window.toFront() }
        }
        CompositionLocalProvider(
            LocalAppContainer provides deps.container,
            com.ozgen.navicloud.ui.LocalVolumeController provides deps.volume,
            com.ozgen.navicloud.ui.LocalMiniPlayerToggle provides { openMini() },
        ) {
            NaviCloudTheme {
                NaviCloudRoot(
                    platformSettings = { nav -> DesktopSettingsScreen(nav) },
                )
            }
        }
    }

    // Her zaman üstte kalabilen mini oynatıcı (ayrı, çerçevesiz pencere)
    if (miniOpen) {
        MiniPlayerWindow(player = player, onExpand = { showWindow() })
    }
}

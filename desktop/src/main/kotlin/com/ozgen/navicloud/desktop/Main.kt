package com.ozgen.navicloud.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Uygulama/tepsi ikonu: mor→indigo gradyan yuvarlak kare + beyaz bulut + mor play. */
private val NaviCloudIconPainter: Painter = object : Painter() {
    override val intrinsicSize = Size(512f, 512f)
    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF8E5BFF), Color(0xFF5B34E0)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
            cornerRadius = CornerRadius(size.minDimension * 0.22f),
        )
        // Bulut (beyaz): taban + üç puf
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.352f, h * 0.504f),
            size = Size(w * 0.316f, h * 0.142f),
            cornerRadius = CornerRadius(w * 0.064f),
        )
        drawCircle(Color.White, radius = w * 0.117f, center = Offset(w * 0.363f, h * 0.532f))
        drawCircle(Color.White, radius = w * 0.137f, center = Offset(w * 0.656f, h * 0.520f))
        drawCircle(Color.White, radius = w * 0.168f, center = Offset(w * 0.500f, h * 0.447f))
        // Play üçgeni (mor)
        val tri = Path().apply {
            moveTo(w * 0.457f, h * 0.451f)
            lineTo(w * 0.457f, h * 0.607f)
            lineTo(w * 0.600f, h * 0.529f)
            close()
        }
        drawPath(tri, Color(0xFF6D28D9))
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
        // Windows medya tuşları + kontrol merkezi/flyout oynatıcısı (SMTC)
        SmtcController(player, okHttp).start()
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
        icon = NaviCloudIconPainter,
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
        icon = NaviCloudIconPainter,
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

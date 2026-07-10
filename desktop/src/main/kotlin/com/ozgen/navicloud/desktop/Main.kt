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
import com.ozgen.navicloud.remote.rcServerId
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

private class DesktopDeps(
    val container: AppContainer,
    val volume: com.ozgen.navicloud.ui.VolumeController,
    val queueSync: com.ozgen.navicloud.playback.QueueSyncManager,
    val rcManager: com.ozgen.navicloud.remote.RemoteControlManager,
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
fun main() {
    // Tek örnek: zaten çalışan varsa onu öne getirt ve çık (çift tıkta yeni pencere açma).
    if (!SingleInstance.acquire()) {
        SingleInstance.signalExisting()
        return
    }
    // Saydam (yuvarlak köşeli) mini pencere için: Windows'un varsayılan D3D
    // backend'i saydam pencerede çizilmeyen bölgeyi opak siyah bırakıyordu.
    // OpenGL backend per-pixel alpha'yı doğru temizliyor. renderApi global
    // olduğu için pencere yaratılmadan önce, en başta set edilmeli.
    // Linux'ta skiko default backend (OpenGL/Vulkan) zaten uygun; zorlarsak bazı
    // sürücülerde ilk frame boş çizilip ham AWT arka planı (beyaz) kalıyor.
    if (System.getProperty("os.name").orEmpty().startsWith("Windows")) {
        System.setProperty("skiko.renderApi", "OPENGL")
    }
    runApp()
}

private fun runApp() = application {
    val deps = remember {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val okHttp = OkHttpClient()
        val servers = DesktopServerSource(okHttp, json)
        val offline = DesktopOfflineSource()
        val downloads = DesktopDownloads(servers, okHttp)
        val music = MusicRepository(servers, InMemoryApiCacheStore(), json, offline, okHttp, DesktopLyricsSettings())
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
        // Uzaktan kumanda (RC-1/RC-2): alıcı sunucu + mDNS keşif + lokal↔uzak swap proxy'si.
        // UI container.player olarak ACTIVE'i görür; QueueSync/SMTC LOKAL player'da kalır.
        val rcScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
        )
        val activePlayer = com.ozgen.navicloud.remote.ActivePlayerController(player, rcScope)
        val selfNameFlow = kotlinx.coroutines.flow.MutableStateFlow(DesktopPrefs.deviceName)
        val rcPairing = FilePairingStore()
        val rcManager = com.ozgen.navicloud.remote.RemoteControlManager(
            local = player,
            active = activePlayer,
            client = com.ozgen.navicloud.remote.OkHttpRcClient(okHttp, json),
            discovery = JmDnsPeerDiscovery(DesktopPrefs.deviceId),
            scope = rcScope,
            // mpv erişimi thread-güvenli → ana thread zorunlu değil
            playerDispatcher = kotlinx.coroutines.Dispatchers.Default,
            selfInfo = {
                com.ozgen.navicloud.remote.RcDeviceInfo(DesktopPrefs.deviceId, selfNameFlow.value, "desktop")
            },
            serverIdFlow = servers.activeServer.map { s -> s?.baseUrl?.let(::rcServerId) },
            artworkResolver = { song -> runCatching { music.coverArtUrl(song.coverArt) }.getOrNull() },
            selfName = selfNameFlow,
            setSelfNameImpl = { name ->
                DesktopPrefs.deviceName = name
                selfNameFlow.value = DesktopPrefs.deviceName
            },
            pairing = rcPairing,
            secret = { DesktopPrefs.remoteSecret.ifBlank { null } },
        )
        val rcServer = com.ozgen.navicloud.remote.RemoteControlServer(
            server = com.ozgen.navicloud.remote.KtorRcServer(json),
            localPlayer = player,
            scope = rcScope,
            playerDispatcher = kotlinx.coroutines.Dispatchers.Default,
            deviceInfo = {
                com.ozgen.navicloud.remote.RcDeviceInfo(DesktopPrefs.deviceId, DesktopPrefs.deviceName, "desktop")
            },
            pairing = rcPairing,
            volumeSink = object : com.ozgen.navicloud.remote.VolumeSink {
                override var volume: Float
                    get() = engine.volume / 100f
                    set(value) {
                        engine.volume = (value * 100).toInt().coerceIn(0, 100)
                        DesktopPrefs.volume = (value * 100).toInt()
                    }
            },
            // Kumanda ederken kilitli (soru turu kararı): meşgulken gelen Hello reddedilir
            isBusy = rcManager::isBusy,
            secret = { DesktopPrefs.remoteSecret.ifBlank { null } },
        )
        // Varsayılan port doluysa OS seçsin; mDNS gerçek boundPort'u yayınlar. Sessiz fail.
        runCatching { rcServer.start(com.ozgen.navicloud.remote.RC_DEFAULT_PORT) }
            .recoverCatching { rcServer.start(0) }
        runCatching { rcManager.start(rcServer.boundPort) }
        // Alıcı göstergesi + PIN gösterimi + "kumandayı al" (RC-3/RC-5)
        rcManager.attachReceiver(rcServer.controllerCount, rcServer.pairPin) { rcServer.kickControllers() }
        // Cihazlar arası kuyruk senkronu — açılışta pull (checkForResume start() içinde tetiklenir),
        // track/pause/periyodik push, kapanışta flushBlocking.
        val queueSync = com.ozgen.navicloud.playback.QueueSyncManager(
            music, servers, offline, FileQueueSyncStateStore(), json,
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
            ),
            player,
            // Masaüstü: mpv erişimi thread-güvenli → ana thread zorunlu değil
            kotlinx.coroutines.Dispatchers.Default,
        ).apply { start() }
        DesktopDeps(
            queueSync = queueSync,
            rcManager = rcManager,
            container = AppContainer(
                music = music,
                player = activePlayer, // UI lokal↔uzak swap proxy'sini görür
                servers = servers,
                downloads = downloads,
                offline = offline,
                recents = InMemoryRecentSearches(),
                queueSync = queueSync,
                remoteControl = rcManager,
            ),
            // Ses slider'ı: hedef Remote iken UZAK cihazın sesini sürer (VOLUME cmd), değilse lokal mpv (RC-3)
            volume = object : com.ozgen.navicloud.ui.VolumeController {
                override var volume: Float
                    get() = if (rcManager.isBusy()) rcManager.remoteVolume.value ?: 1f
                    else engine.volume / 100f
                    set(value) {
                        if (rcManager.isBusy()) {
                            rcManager.setRemoteVolume(value)
                        } else {
                            engine.volume = (value * 100).toInt()
                            DesktopPrefs.volume = (value * 100).toInt()
                        }
                    }
            },
        )
    }

    val player = deps.container.player
    val playerState by player.state.collectAsState()
    var windowVisible by remember { mutableStateOf(true) }
    var miniOpen by remember { mutableStateOf(false) }
    // Mini pencere durumu (konum + varyant) tek kaynaktan — motordan bağımsız
    val miniModel = remember { MiniWindowModel() }
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    // Ana pencere AWT referansı (tek-instance "öne getir" için) — Window içeriğinde set edilir.
    var windowRef by remember { mutableStateOf<androidx.compose.ui.awt.ComposeWindow?>(null) }

    fun showWindow() {
        miniOpen = false
        windowState.isMinimized = false
        windowVisible = true
    }

    // Tek-instance: ikinci kez açılış sinyali gelince mevcut pencereyi öne getir (tepside/minimize'da bile).
    val focusReq by SingleInstance.focusRequests.collectAsState()
    LaunchedEffect(focusReq) {
        if (focusReq > 0) {
            showWindow()
            windowRef?.let { runCatching { it.toFront(); it.requestFocus() } }
        }
    }

    fun openMini() {
        miniOpen = true
        windowVisible = false
    }

    fun quitApp() {
        deps.queueSync.flushBlocking(1500)
        deps.rcManager.stop() // mDNS goodbye — diğer cihazların listesinden düş
        exitApplication()
    }

    // Linux: AWT tepsisi KDE/GNOME'da işlevsiz (XEmbed) — D-Bus StatusNotifierItem dene.
    // Callback'ler dbus thread'inden gelir; Compose state'ine AWT EDT üzerinden dokun.
    val linuxTray = remember {
        fun ui(block: () -> Unit): () -> Unit = { javax.swing.SwingUtilities.invokeLater(block) }
        LinuxTray(
            onShow = ui { showWindow() },
            onMini = ui { openMini() },
            onPlayPause = { deps.container.player.togglePlayPause() },
            onPrev = { deps.container.player.skipPrevious() },
            onNext = { deps.container.player.skipNext() },
            onQuit = ui { quitApp() },
        ).takeIf { it.start() }
    }
    // Linux medya tuşları + KDE/GNOME medya denetçisi (SMTC'nin muadili).
    // UI gibi ACTIVE player'ı sürer; ses de ana ses slider'ıyla aynı yoldan (RC'de uzak cihaz).
    remember {
        fun ui(block: () -> Unit): () -> Unit = { javax.swing.SwingUtilities.invokeLater(block) }
        MprisController(
            player = deps.container.player,
            onRaise = ui { showWindow() },
            onQuit = ui { quitApp() },
            getVolume = { deps.volume.volume },
            setVolume = { deps.volume.volume = it },
        ).apply { start() }
    }
    if (linuxTray != null) {
        LaunchedEffect(playerState.currentTrack, playerState.isPlaying) {
            linuxTray.update(
                tooltip = playerState.currentTrack?.let { "${it.song.artist} — ${it.song.title}" } ?: "",
                playLabel = if (playerState.isPlaying) "Duraklat" else "Çal",
                hasTrack = playerState.currentTrack != null,
            )
        }
    } else {
        // AWT tepsisi (Windows + SNI'siz Linux): sol tık pencereyi getirir, sağ tık menüyü açar.
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
                Item("Çıkış", onClick = { quitApp() })
            },
        )
    }

    Window(
        onCloseRequest = {
            // Kapanış/küçültmede güncel kuyruğu sunucuya it (timeout'lu, asılmaz)
            deps.queueSync.flushBlocking(1500)
            // Ayar açıksa pencereyi tepsiye küçült (advertise SÜRER — hâlâ kumanda edilebilir);
            // kapalıysa mDNS goodbye + tam çıkış
            if (DesktopPrefs.closeToTray) {
                windowVisible = false
            } else {
                deps.rcManager.stop()
                exitApplication()
            }
        },
        visible = windowVisible,
        state = windowState,
        title = "NaviCloud",
        icon = NaviCloudIconPainter,
    ) {
        // Pencere AWT referansını tek-instance "öne getir" için dışarı ver
        androidx.compose.runtime.SideEffect { windowRef = window }
        // Gizliyken tekrar gösterilince öne getir (tepsiden dönüş) + başka cihazın
        // bıraktığı kuyruğu yeniden yokla (açıkken de sync yakalasın)
        LaunchedEffect(windowVisible) {
            if (windowVisible) {
                runCatching { window.toFront() }
                deps.queueSync.requestResumeCheck()
            }
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

    // Her zaman üstte kalabilen mini oynatıcı (ayrı, çerçevesiz pencere).
    // Varyant modelde tutulur; değişince pencere aynı konumda swap olur.
    if (miniOpen) {
        when (miniModel.variant) {
            MiniVariant.STANDARD -> MiniPlayerWindow(player, miniModel, onExpand = { showWindow() })
            MiniVariant.VINYL -> MiniVinylWindow(player, miniModel, onExpand = { showWindow() })
        }
    }
}
